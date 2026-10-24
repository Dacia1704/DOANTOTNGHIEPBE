# VNPay Sandbox

Backend đã có checkout, Return, IPN, QueryDr kiểm tra lại thanh toán, hóa đơn và đối soát khoản thu. Triển khai theo [tài liệu kết nối VNPay 2.1.0](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html), [hướng dẫn checksum](https://sandbox.vnpayment.vn/apis/docs/chuyen-doi-thuat-toan/changeTypeHash.html) và [đặc tả QueryDr](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).

## Cấu hình runtime

Đặt trong Environment variables của cấu hình chạy IntelliJ hoặc tiến trình backend:

Cũng có thể đặt các biến bên dưới trong `.env.local` tại thư mục gốc dự án theo định dạng `TEN_BIEN=gia_tri` (không thêm `export` hoặc dấu nháy bao giá trị). Backend tự đọc file này qua spring.config.import khi chạy với working directory là thư mục gốc dự án. File đã được Git ignore; khởi động lại backend sau khi thay đổi. Biến môi trường của tiến trình được ưu tiên hơn giá trị trong file local.

- `VNPAY_TMN_CODE`: mã website được cấp; mặc định NQC9OREC cho sandbox đã thống nhất.
- `VNPAY_HASH_SECRET`: khóa checksum của terminal; bắt buộc, không có giá trị mặc định.
- `VNPAY_PAYMENT_URL`: mặc định `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`.
- `VNPAY_RETURN_URL`: URL trình duyệt trở về; mặc định `http://localhost:8080/api/payments/vnpay/return` khi chạy local. Với backend công khai, dùng `https://<backend-public>/api/payments/vnpay/return`.
- `VNPAY_QUERY_URL`: mặc định `https://sandbox.vnpayment.vn/merchant_webapi/api/transaction`; dùng để truy vấn kết quả, không phải URL thanh toán trình duyệt.
- `VNPAY_QUERY_IP`: IP máy chủ gửi yêu cầu QueryDr, mặc định `127.0.0.1` cho local; cấu hình IP máy chủ phù hợp khi triển khai.

Trong cấu hình merchant của VNPay, đăng ký IPN URL là `https://<backend-public>/api/payments/vnpay/ipn`. IPN là cấu hình phía merchant, không phải trường do frontend tùy ý gửi hoặc tham số vnp_IpnUrl trong checkout. URL này phải truy cập được từ hệ thống VNPay. Backend đứng sau proxy dùng remote address mà servlet cung cấp; không tự tin cậy X-Forwarded-For do client gửi.

Không lưu khóa thật vào source, log, docs, `.env` hoặc `.env.docker` đang được theo dõi Git. Thiếu khóa, checkout trả 503; các module khác vẫn khởi động bình thường.

## Kiểm tra trực tiếp

1. Cấu hình môi trường ứng dụng và database theo dự án; áp dụng Flyway V3/V4/V5 theo hướng dẫn dữ liệu cũ trong `SUBSCRIPTION_SCHEMA.md`. Không reset database để chạy thử thanh toán.
2. Admin bật gói và giá theo tháng. Chủ trọ tạo đơn bằng POST `/api/subscriptions/orders`.
3. Gọi POST `/api/payments/vnpay/checkout` với type=SERVICE_PACKAGE và targetId là ID đơn, bằng token của chủ trọ.
4. Mở paymentUrl, thanh toán bằng thông tin thử nghiệm VNPay. Return chỉ trả kết quả có kiểm tra chữ ký, không cấp quyền.
5. Chờ IPN hoặc gọi POST `/api/payments/{paymentId}/check` bằng token chủ giao dịch/admin (không có body) để backend hỏi VNPay. QueryDr chạy được trên localhost, không cần mua tên miền hoặc có IPN công khai. Gọi GET `/api/payments/{paymentId}` để xác nhận SUCCESS, invoiceId và requiresReview; kiểm tra quyền tại `/api/subscriptions/current`. Chỉ truy vấn thành công chưa có nghĩa đã thu tiền: cần providerTransactionStatus=00.
6. Với CHANGE, admin xác nhận sau khi IPN/QueryDr ghi nhận thanh toán qua POST `/api/subscriptions/orders/{id}/confirm`; trước hạn là SCHEDULED, sau hạn bắt đầu tại lúc xác nhận.
7. Với Boost, tạo `/api/boost-subscriptions` trước, checkout type=BOOST_PACKAGE; sau IPN/QueryDr kiểm tra lịch sử Boost và priorityLevel của tin.
8. Kiểm tra hủy/thất bại, gửi lại callback, thanh toán sau hạn và hai lần thu cho cùng đối tượng. Không được thêm kỳ/Boost/hóa đơn hai lần cho một callback.

## Bằng chứng và giới hạn

Kiểm thử tự động dùng MySQL riêng, Flyway V1–V5, JPA thật, controller/service/repository thật và SecurityConfig của ứng dụng. Người dùng thử được cung cấp bằng Spring Security test; bộ kiểm thử không thay thế kiểm tra đăng nhập/JWT và toàn bộ hạ tầng ứng dụng.

Callback trong kiểm thử được ký bằng khóa giả của test, gồm đối chiếu HMAC với vector tính độc lập, tham số bị sửa/trùng, sai terminal/số tiền, IPN lặp, thu trùng, thời hạn và giao dịch đồng thời. Chưa thực hiện một giao dịch qua website VNPay hoặc xác nhận VNPay gọi được URL IPN công khai; cần URL backend và cấu hình merchant để kiểm tra bước này.

QueryDr được kiểm tra bằng HTTP server local giả lập VNPay và các ca ghi nhận thanh toán trên MySQL: phản hồi sai không cấp quyền, khôi phục EXPIRED, Boost, CHANGE vẫn chờ admin và IPN đến trong lúc truy vấn không nhân quyền/hóa đơn. Kết quả kiểm thử này không khẳng định giao dịch sandbox cụ thể của người dùng đã được VNPay xác nhận; dùng endpoint check để kiểm tra giao dịch đó. Với payment trước V5, vnp_CreateDate được suy ra từ created_at nên có thể không chính xác nếu URL checkout cũ từng được tạo lại; xem mục 11.4 trong `api.md` nếu provider trả 91.

Chạy nhóm kiểm thử bằng JDK 21 và MySQL thử nghiệm, không đặt URL database ứng dụng:

```powershell
$env:SUBSCRIPTION_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:33317/'
$env:SUBSCRIPTION_TEST_MYSQL_USER='root'
./mvnw.cmd '-Dtest=PackageCatalogApiTest,SubscriptionSchemaMigrationTest,VnPaySignerTest,VnPayQueryClientTest' test
```

Nếu MySQL thử nghiệm có mật khẩu, truyền `SUBSCRIPTION_TEST_MYSQL_PASSWORD` qua môi trường chạy. Các schema được tạo với tên ngẫu nhiên `subscription_test_<uuid>` và dọn sau test; test database không chạy khi thiếu URL phù hợp. Hai kiểm thử chữ ký vẫn có thể chạy độc lập, không cần MySQL.
