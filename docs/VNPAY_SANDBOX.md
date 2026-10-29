# VNPay Sandbox

Backend đã có checkout, Return, IPN, QueryDr kiểm tra lại thanh toán, hóa đơn và đối soát khoản thu. Triển khai theo [tài liệu kết nối VNPay 2.1.0](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html), [hướng dẫn checksum](https://sandbox.vnpayment.vn/apis/docs/chuyen-doi-thuat-toan/changeTypeHash.html) và [đặc tả QueryDr](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).

## Cấu hình runtime

Đặt các biến bên dưới trực tiếp trong `.env` tại thư mục gốc dự án theo định dạng `TEN_BIEN=gia_tri` (không thêm `export` hoặc dấu nháy bao giá trị). Backend tự đọc `.env` qua `spring.config.import` khi chạy với working directory là thư mục gốc dự án; không cần `.env.local`. Khởi động lại backend sau khi thay đổi. Biến môi trường của tiến trình vẫn được ưu tiên hơn giá trị trong `.env`.

- `VNPAY_TMN_CODE`: mã website được cấp; mặc định NQC9OREC cho sandbox đã thống nhất.
- `VNPAY_HASH_SECRET`: khóa checksum của terminal; bắt buộc, không có giá trị mặc định.
- `VNPAY_PAYMENT_URL`: mặc định `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`.
- `VNPAY_RETURN_URL`: URL trình duyệt trở về giao diện Angular; mặc định `http://localhost:4200/thanh-toan/ket-qua` khi chạy local. Khi triển khai, dùng `https://<frontend-public>/thanh-toan/ket-qua`. Trang kết quả đọc query params và gọi backend lấy trạng thái payment; có thể gửi nguyên các tham số VNPay về GET `/api/payments/vnpay/return` để kiểm tra chữ ký.
- `VNPAY_QUERY_URL`: mặc định `https://sandbox.vnpayment.vn/merchant_webapi/api/transaction`; dùng để truy vấn kết quả, không phải URL thanh toán trình duyệt.
- `VNPAY_QUERY_IP`: IP máy chủ gửi yêu cầu QueryDr, mặc định `127.0.0.1` cho local; cấu hình IP máy chủ phù hợp khi triển khai.

Trong cấu hình merchant của VNPay, đăng ký IPN URL là `https://<backend-public>/api/payments/vnpay/ipn`. IPN là cấu hình phía merchant, không phải trường do frontend tùy ý gửi hoặc tham số vnp_IpnUrl trong checkout. URL này phải truy cập được từ hệ thống VNPay. Backend đứng sau proxy dùng remote address mà servlet cung cấp; không tự tin cậy X-Forwarded-For do client gửi.

Khóa VNPay được cấu hình trong `.env`. Thiếu khóa, checkout trả 503; các module khác vẫn khởi động bình thường.

## Nhận IPN khi backend chạy localhost

Trong Merchant Admin, mở **Thông tin tài khoản → website của mình → sửa thông tin IPN**. IPN URL phải là URL HTTPS gọi được từ Internet, kết thúc bằng `/api/payments/vnpay/ipn`; giá trị `#` hoặc `localhost` không nhận được callback từ VNPay. Chọn **GET** và **HMACSHA512**. Return URL của frontend và IPN URL của backend có vai trò khác nhau.

Khi chưa có backend public, có thể dùng [Cloudflare Quick Tunnel](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/). Tunnel trung chuyển dữ liệu callback thanh toán qua Cloudflare; chỉ bật khi người quản lý môi trường cho phép. Proxy đi kèm chỉ cho GET tới endpoint IPN, chặn các API khác và không chuyển tiếp Cookie/Authorization của client. Backend vẫn kiểm tra chữ ký, merchant, số tiền và chống ghi nhận trùng như bình thường.

1. Chạy backend ở cổng 8080 và cài Node.js 22 trở lên.
2. Tải `cloudflared-windows-amd64.exe` bản **2026.10.6** từ [release chính thức](https://github.com/cloudflare/cloudflared/releases/tag/2026.10.6), lưu thành `target/vnpay-diagnostics/bin/cloudflared.exe`. Script kiểm tra SHA256 `d521b3bf68982165f0d06d76732f22d9a105ef057ff3ae4c0986f3a23190950f` trước khi chạy.
3. Chạy `./scripts/start-vnpay-ipn.ps1`. Proxy chỉ lắng nghe `127.0.0.1:18080`; tunnel tạo URL HTTPS tạm trên `trycloudflare.com`.
4. Đăng ký `https://<ten-tunnel>.trycloudflare.com/api/payments/vnpay/ipn` trên Merchant Admin. Mỗi lần tạo tunnel mới phải cập nhật URL nếu nó thay đổi.
5. Giữ terminal và backend chạy trong lúc thử. Đóng tunnel sẽ làm IPN URL tạm ngừng hoạt động; môi trường triển khai cần URL backend HTTPS ổn định.

Kiểm tra proxy bằng `node --test scripts/vnpay-ipn-proxy.test.mjs`. Request không có chữ ký tới endpoint IPN phải trả `RspCode=97`; gọi các API khác qua proxy phải trả 404. Log nằm trong `target/vnpay-diagnostics/` và không ghi HashSecret hoặc toàn bộ query string callback.

Nếu Return có chữ ký hợp lệ báo `00/00` nhưng payment còn PENDING/EXPIRED, kiểm tra cấu hình và log nhận IPN. QueryDr trả `99 / Unexpected error` là lỗi truy vấn, không phải bằng chứng giao dịch thanh toán đã thất bại. Không thay trạng thái payment bằng cách sửa database hoặc dùng Return để giả làm IPN; cần callback IPN thật hoặc phản hồi QueryDr đã xác minh.

## Kiểm tra trực tiếp

1. Cấu hình môi trường ứng dụng và database theo dự án; áp dụng Flyway V3/V4/V5 theo hướng dẫn dữ liệu cũ trong `SUBSCRIPTION_SCHEMA.md`. Không reset database để chạy thử thanh toán.
2. Admin bật gói và giá theo tháng. Chủ trọ tạo đơn bằng POST `/api/subscriptions/orders`.
3. Gọi POST `/api/payments/vnpay/checkout` với type=SERVICE_PACKAGE và targetId là ID đơn, bằng token của chủ trọ.
4. Mở paymentUrl mới được tạo, thanh toán bằng thông tin thử nghiệm VNPay. Trình duyệt trở về trang kết quả Angular; frontend lấy trạng thái thanh toán từ backend. Return không tự cấp quyền; kết quả URL phải được backend kiểm tra hoặc đối chiếu trạng thái payment.
5. Chờ IPN hoặc gọi POST `/api/payments/{paymentId}/check` bằng token chủ giao dịch/admin (không có body) để backend hỏi VNPay. QueryDr chạy được trên localhost, không cần mua tên miền hoặc có IPN công khai. Gọi GET `/api/payments/{paymentId}` để xác nhận SUCCESS, invoiceId và requiresReview; kiểm tra quyền tại `/api/subscriptions/current`. Chỉ truy vấn thành công chưa có nghĩa đã thu tiền: cần providerTransactionStatus=00.
6. Với CHANGE, admin xác nhận sau khi IPN/QueryDr ghi nhận thanh toán qua POST `/api/subscriptions/orders/{id}/confirm`; trước hạn là SCHEDULED, sau hạn bắt đầu tại lúc xác nhận.
7. Với Boost, tạo `/api/boost-subscriptions` trước, checkout type=BOOST_PACKAGE; sau IPN/QueryDr kiểm tra lịch sử Boost và priorityLevel của tin.
8. Kiểm tra hủy/thất bại, gửi lại callback, thanh toán sau hạn và hai lần thu cho cùng đối tượng. Không được thêm kỳ/Boost/hóa đơn hai lần cho một callback.

## Bằng chứng và giới hạn

Kiểm thử tự động dùng MySQL riêng, Flyway V1–V5, JPA thật, controller/service/repository thật và SecurityConfig của ứng dụng. Người dùng thử được cung cấp bằng Spring Security test; bộ kiểm thử không thay thế kiểm tra đăng nhập/JWT và toàn bộ hạ tầng ứng dụng.

Callback trong kiểm thử được ký bằng khóa giả của test, gồm đối chiếu HMAC với vector tính độc lập, tham số bị sửa/trùng, sai terminal/số tiền, IPN lặp, thu trùng, thời hạn và giao dịch đồng thời. Kiểm tra sandbox thực tế ngày 30/10/2026 đã xác nhận giao dịch mua gói 20.000đ qua website VNPay: sau khi lưu IPN HTTPS, backend nhận callback thật và phản hồi `RspCode=00`; payment thành SUCCESS, có đúng một hóa đơn 20.000đ, đơn mua gói chuyển APPLIED và không cần đối soát thủ công. Kết quả này kiểm chứng luồng IPN cho giao dịch mới. Các giao dịch trước khi cấu hình IPN vẫn chưa được xác nhận trong backend; QueryDr sandbox vẫn trả `99 / Unexpected error` ở lần kiểm tra gần nhất.

QueryDr được kiểm tra bằng HTTP server local giả lập VNPay và các ca ghi nhận thanh toán trên MySQL: phản hồi sai không cấp quyền, khôi phục EXPIRED, Boost, CHANGE vẫn chờ admin và IPN đến trong lúc truy vấn không nhân quyền/hóa đơn. Kết quả kiểm thử này không khẳng định giao dịch sandbox cụ thể của người dùng đã được VNPay xác nhận; dùng endpoint check để kiểm tra giao dịch đó. Với payment trước V5, vnp_CreateDate được suy ra từ created_at nên có thể không chính xác nếu URL checkout cũ từng được tạo lại; xem mục 11.4 trong `api.md` nếu provider trả 91.

Chạy nhóm kiểm thử bằng JDK 21 và MySQL thử nghiệm, không đặt URL database ứng dụng:

```powershell
$env:SUBSCRIPTION_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:33317/'
$env:SUBSCRIPTION_TEST_MYSQL_USER='root'
./mvnw.cmd '-Dtest=PackageCatalogApiTest,SubscriptionSchemaMigrationTest,VnPaySignerTest,VnPayQueryClientTest' test
```

Nếu MySQL thử nghiệm có mật khẩu, truyền `SUBSCRIPTION_TEST_MYSQL_PASSWORD` qua môi trường chạy. Các schema được tạo với tên ngẫu nhiên `subscription_test_<uuid>` và dọn sau test; test database không chạy khi thiếu URL phù hợp. Hai kiểm thử chữ ký vẫn có thể chạy độc lập, không cần MySQL.
