# Vận hành và kiểm thử backend

[Mục lục](../README.md) · [API](api.md) · [Nghiệp vụ](BUSINESS.md)

## Database và migration hiện tại

Tại ngày hợp nhất 10/11/2026, thư mục migration có `V1__fullsql.sql`, `V2__seed_reference_and_demo_data.sql`, `V6__notification_delivery.sql`, `V7__role_permission_management.sql`. V1 là schema đã gộp, V2 là seed; V6/V7 bổ sung giao thông báo và phân quyền. Những số migration V3–V5, V8–V14 trong báo cáo triển khai cũ không phải hướng dẫn áp dụng cho checkout hiện tại. Không sửa migration đã áp dụng hoặc chạy repair để che lỗi checksum.

Cài đặt toàn hệ thống, biến môi trường và seed theo [README chung](../../AIService/README.md). Không reset hoặc seed lại database đang dùng để chạy thử tính năng. Source là cơ sở đối chiếu bảng/cột, không dùng số bảng trong báo cáo cũ làm tiêu chí xóa bảng.

<a id="vnpay"></a>

## VNPay Sandbox


Backend đã có checkout, Return, IPN, QueryDr kiểm tra lại thanh toán, hóa đơn và đối soát khoản thu. Triển khai theo [tài liệu kết nối VNPay 2.1.0](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html), [hướng dẫn checksum](https://sandbox.vnpayment.vn/apis/docs/chuyen-doi-thuat-toan/changeTypeHash.html) và [đặc tả QueryDr](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).

### Cấu hình runtime

Đặt các biến bên dưới trực tiếp trong `.env` tại thư mục gốc dự án theo định dạng `TEN_BIEN=gia_tri` (không thêm `export` hoặc dấu nháy bao giá trị). Backend tự đọc `.env` qua `spring.config.import` khi chạy với working directory là thư mục gốc dự án; không cần `.env.local`. Khởi động lại backend sau khi thay đổi. Biến môi trường của tiến trình vẫn được ưu tiên hơn giá trị trong `.env`.

- `VNPAY_TMN_CODE`: mã website được cấp; mặc định NQC9OREC cho sandbox đã thống nhất.
- `VNPAY_HASH_SECRET`: khóa checksum của terminal; bắt buộc, không có giá trị mặc định.
- `VNPAY_PAYMENT_URL`: mặc định `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`.
- `VNPAY_RETURN_URL`: URL trình duyệt trở về giao diện Angular; mặc định `http://localhost:4200/thanh-toan/ket-qua` khi chạy local. Khi triển khai, dùng `https://<frontend-public>/thanh-toan/ket-qua`. Trang kết quả đọc query params và gọi backend lấy trạng thái payment; có thể gửi nguyên các tham số VNPay về GET `/api/payments/vnpay/return` để kiểm tra chữ ký.
- `VNPAY_QUERY_URL`: mặc định `https://sandbox.vnpayment.vn/merchant_webapi/api/transaction`; dùng để truy vấn kết quả, không phải URL thanh toán trình duyệt.
- `VNPAY_QUERY_IP`: IP máy chủ gửi yêu cầu QueryDr, mặc định `127.0.0.1` cho local; cấu hình IP máy chủ phù hợp khi triển khai.

Trong cấu hình merchant của VNPay, đăng ký IPN URL là `https://<backend-public>/api/payments/vnpay/ipn`. IPN là cấu hình phía merchant, không phải trường do frontend tùy ý gửi hoặc tham số vnp_IpnUrl trong checkout. URL này phải truy cập được từ hệ thống VNPay. Backend đứng sau proxy dùng remote address mà servlet cung cấp; không tự tin cậy X-Forwarded-For do client gửi.

Khóa VNPay được cấu hình trong `.env`. Thiếu khóa, checkout trả 503; các module khác vẫn khởi động bình thường.

### Nhận IPN khi backend chạy localhost

Trong Merchant Admin, mở **Thông tin tài khoản → website của mình → sửa thông tin IPN**. IPN URL phải là URL HTTPS gọi được từ Internet, kết thúc bằng `/api/payments/vnpay/ipn`; giá trị `#` hoặc `localhost` không nhận được callback từ VNPay. Chọn **GET** và **HMACSHA512**. Return URL của frontend và IPN URL của backend có vai trò khác nhau.

Khi chưa có backend public, có thể dùng [Cloudflare Quick Tunnel](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/). Tunnel trung chuyển dữ liệu callback thanh toán qua Cloudflare; chỉ bật khi người quản lý môi trường cho phép. Proxy đi kèm chỉ cho GET tới endpoint IPN, chặn các API khác và không chuyển tiếp Cookie/Authorization của client. Backend vẫn kiểm tra chữ ký, merchant, số tiền và chống ghi nhận trùng như bình thường.

1. Chạy backend ở cổng 8080 và cài Node.js 22 trở lên.
2. Tải `cloudflared-windows-amd64.exe` bản **2026.10.6** từ [release chính thức](https://github.com/cloudflare/cloudflared/releases/tag/2026.10.6), lưu thành `target/vnpay-diagnostics/bin/cloudflared.exe`. Script kiểm tra SHA256 `d521b3bf68982165f0d06d76732f22d9a105ef057ff3ae4c0986f3a23190950f` trước khi chạy.
3. Chạy `./scripts/start-vnpay-ipn.ps1`. Proxy chỉ lắng nghe `127.0.0.1:18080`; tunnel tạo URL HTTPS tạm trên `trycloudflare.com`.
4. Đăng ký `https://<ten-tunnel>.trycloudflare.com/api/payments/vnpay/ipn` trên Merchant Admin. Mỗi lần tạo tunnel mới phải cập nhật URL nếu nó thay đổi.
5. Giữ terminal và backend chạy trong lúc thử. Đóng tunnel sẽ làm IPN URL tạm ngừng hoạt động; môi trường triển khai cần URL backend HTTPS ổn định.

Kiểm tra proxy bằng `node --test scripts/vnpay-ipn-proxy.test.mjs`. Request không có chữ ký tới endpoint IPN phải trả `RspCode=97`; gọi các API khác qua proxy phải trả 404. Log nằm trong `target/vnpay-diagnostics/` và không ghi HashSecret hoặc toàn bộ query string callback.

Nếu Return có chữ ký hợp lệ báo `00/00` nhưng payment còn PENDING/EXPIRED, kiểm tra cấu hình và log nhận IPN. QueryDr trả `99 / Unexpected error` là lỗi truy vấn, không phải bằng chứng giao dịch thanh toán đã thất bại. Không thay trạng thái payment bằng cách sửa database hoặc dùng Return để giả làm IPN; cần callback IPN thật hoặc phản hồi QueryDr đã xác minh.

### Kiểm tra trực tiếp

1. Cấu hình môi trường ứng dụng và database theo dự án; để Flyway áp dụng các migration hiện có trong `src/main/resources/db/migration`. Không reset database để chạy thử thanh toán.
2. Admin bật gói và giá theo tháng. Chủ trọ tạo đơn bằng POST `/api/subscriptions/orders`.
3. Gọi POST `/api/payments/vnpay/checkout` với type=SERVICE_PACKAGE và targetId là ID đơn, bằng token của chủ trọ.
4. Mở paymentUrl mới được tạo, thanh toán bằng thông tin thử nghiệm VNPay. Trình duyệt trở về trang kết quả Angular; frontend lấy trạng thái thanh toán từ backend. Return không tự cấp quyền; kết quả URL phải được backend kiểm tra hoặc đối chiếu trạng thái payment.
5. Chờ IPN hoặc gọi POST `/api/payments/{paymentId}/check` bằng token chủ giao dịch/admin (không có body) để backend hỏi VNPay. QueryDr chạy được trên localhost, không cần mua tên miền hoặc có IPN công khai. Gọi GET `/api/payments/{paymentId}` để xác nhận SUCCESS, invoiceId và requiresReview; kiểm tra quyền tại `/api/subscriptions/current`. Chỉ truy vấn thành công chưa có nghĩa đã thu tiền: cần providerTransactionStatus=00.
6. Với CHANGE, admin xác nhận sau khi IPN/QueryDr ghi nhận thanh toán qua POST `/api/subscriptions/orders/{id}/confirm`; trước hạn là SCHEDULED, sau hạn bắt đầu tại lúc xác nhận.
7. Với Boost, tạo `/api/boost-subscriptions` trước, checkout type=BOOST_PACKAGE; sau IPN/QueryDr kiểm tra lịch sử Boost và priorityLevel của tin.
8. Kiểm tra hủy/thất bại, gửi lại callback, thanh toán sau hạn và hai lần thu cho cùng đối tượng. Không được thêm kỳ/Boost/hóa đơn hai lần cho một callback.

### Bằng chứng và giới hạn

Kiểm thử tự động dùng MySQL riêng, Flyway V1–V5, JPA thật, controller/service/repository thật và SecurityConfig của ứng dụng. Người dùng thử được cung cấp bằng Spring Security test; bộ kiểm thử không thay thế kiểm tra đăng nhập/JWT và toàn bộ hạ tầng ứng dụng.

Callback trong kiểm thử được ký bằng khóa giả của test, gồm đối chiếu HMAC với vector tính độc lập, tham số bị sửa/trùng, sai terminal/số tiền, IPN lặp, thu trùng, thời hạn và giao dịch đồng thời. Kiểm tra sandbox thực tế ngày 30/10/2026 đã xác nhận giao dịch mua gói 20.000đ qua website VNPay: sau khi lưu IPN HTTPS, backend nhận callback thật và phản hồi `RspCode=00`; payment thành SUCCESS, có đúng một hóa đơn 20.000đ, đơn mua gói chuyển APPLIED và không cần đối soát thủ công. Kết quả này kiểm chứng luồng IPN cho giao dịch mới. Các giao dịch trước khi cấu hình IPN vẫn chưa được xác nhận trong backend; QueryDr sandbox vẫn trả `99 / Unexpected error` ở lần kiểm tra gần nhất.

QueryDr được kiểm tra bằng HTTP server local giả lập VNPay và các ca ghi nhận thanh toán trên MySQL: phản hồi sai không cấp quyền, khôi phục EXPIRED, Boost, CHANGE vẫn chờ admin và IPN đến trong lúc truy vấn không nhân quyền/hóa đơn. Kết quả kiểm thử này không khẳng định giao dịch sandbox cụ thể của người dùng đã được VNPay xác nhận; dùng endpoint check để kiểm tra giao dịch đó. Với payment trước V5, vnp_CreateDate được suy ra từ created_at nên có thể không chính xác nếu URL checkout cũ từng được tạo lại; xem mục 11.4 trong [API chung](api.md) nếu provider trả 91.

Chạy nhóm kiểm thử bằng JDK 21 và MySQL thử nghiệm, không đặt URL database ứng dụng:

```powershell
$env:SUBSCRIPTION_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:33317/'
$env:SUBSCRIPTION_TEST_MYSQL_USER='root'
./mvnw.cmd '-Dtest=PackageCatalogApiTest,SubscriptionSchemaMigrationTest,VnPaySignerTest,VnPayQueryClientTest' test
```

Nếu MySQL thử nghiệm có mật khẩu, truyền `SUBSCRIPTION_TEST_MYSQL_PASSWORD` qua môi trường chạy. Các schema được tạo với tên ngẫu nhiên `subscription_test_<uuid>` và dọn sau test; test database không chạy khi thiếu URL phù hợp. Hai kiểm thử chữ ký vẫn có thể chạy độc lập, không cần MySQL.

<a id="thong-bao"></a>

## Redis cache và thông báo RabbitMQ

### Khởi chạy

Từ thư mục backend, dùng cấu hình kết nối trong `.env`:

```powershell
docker compose up -d mysql redis rabbitmq
.\mvnw.cmd spring-boot:run
```

Từ `DOANTOTNGHIEPFE`:

```powershell
npm install
npm start
```

FE tại `http://localhost:4200`, BE tại `http://localhost:8080`, RabbitMQ Management tại `http://localhost:15672`.
Flyway áp dụng các migration còn thiếu, gồm V6 cho `notification_outbox` và index inbox. Không sửa các migration đã chạy.

### Cache Redis

Cache danh mục tiện ích, loại phòng, loại cơ sở, tỉnh/thành và phường/xã. Các API và cấu trúc response hiện có được giữ nguyên.

- Key có prefix `rental:v1:`, tách theo danh mục và tham số truy vấn.
- TTL mặc định 30 phút, thay bằng biến `CACHE_TTL`, ví dụ `10m`.
- Thêm/sửa/xóa danh mục và đồng bộ địa chỉ xóa cache liên quan sau khi giao dịch commit. Rollback giữ cache cũ.
- Dữ liệu danh mục được serialize JSON có thông tin kiểu và thời gian. Không cache các entity có quan hệ lazy hoặc dữ liệu riêng theo tài khoản.
- Redis không khả dụng: đọc từ MySQL, ghi log cảnh báo. Xóa cache thất bại có thể giữ dữ liệu danh mục cũ đến khi TTL hết; không dùng cache này để quyết định quyền, giá hợp đồng, số phòng trống hoặc hạn mức.
- Xóa cache dùng SCAN theo lô để tránh chặn Redis bằng KEYS.

### Luồng thông báo

Nghiệp vụ → outbox MySQL cùng giao dịch → relay RabbitMQ → consumer → inbox MySQL → WebSocket → FE.

Đã kết nối các sự kiện yêu cầu thuê, hợp đồng/PDF/trả phòng, xử lý báo cáo và duyệt/từ chối tin đăng. Các `rental_events` cũ chưa giao được chuyển vào outbox bởi tác vụ riêng, kể cả khi tắt worker PDF.

Relay chạy mỗi 2 giây, dùng `FOR UPDATE SKIP LOCKED` cho nhiều worker. Chỉ đánh dấu `published_at` khi nhận publisher confirm và không bị trả về do sai routing. Khi lỗi, sự kiện được giữ lại, thử lại với thời gian chờ tăng dần tối đa 5 phút. Có thể chỉnh `NOTIFICATIONS_RELAY_DELAY_MS` hoặc tắt relay bằng `NOTIFICATIONS_RELAY_ENABLED=false`.

Exchange `rental.notifications`, routing key `notification.created`, queue bền vững `rental.notifications.inbox`. Message được gửi với delivery mode persistent. Consumer retry 3 lần; message không xử lý được đi tới dead-letter queue `rental.notifications.dead.inbox` qua exchange `rental.notifications.dead`. Theo dõi queue này trong RabbitMQ Management; sau khi sửa nguyên nhân có thể chuyển message trở lại exchange chính.

Mỗi sự kiện giữ nguyên ID qua các lần gửi lại; primary key inbox ngăn thông báo trùng và giữ trạng thái đã đọc. Việc ghi inbox và gửi realtime được tách tại thời điểm commit. Sự kiện của tài khoản đã xóa được bỏ qua. Outbox đã publish được dọn sau 7 ngày; lịch sử thông báo vẫn giữ trong inbox.

WebSocket dùng cùng kết nối STOMP/JWT với chat (`/ws`). Client chỉ subscribe `/user/queue/notifications`; payload `{"kind":"CHANGED"}` yêu cầu lấy lại inbox/số chưa đọc. FE tự đồng bộ khi kết nối lại, khi cửa sổ được focus và mỗi 60 giây. Logout/đổi tài khoản hủy request và xóa số chưa đọc cũ.

### API và giao diện

Xem [API thông báo](api.md#notifications).

### Giao diện

Chuông trên header dùng chung cho người thuê, chủ trọ và quản trị viên. Hiển thị badge chưa đọc, danh sách mới nhất, lọc chưa đọc, xem thêm, đọc từng mục/đọc tất cả, trạng thái rỗng và thử lại khi lỗi. Hỗ trợ bàn phím, Escape, trả focus và màn hình nhỏ.

Thông báo hợp đồng mở chi tiết hợp đồng; yêu cầu thuê mở danh sách tương ứng vai trò; tin đăng mở trang quản lý tin đăng. Thông báo báo cáo hiển thị kết quả xử lý ngay trong danh sách.

### Kiểm thử

`NotificationInfrastructureTest` chạy với MySQL/Redis/RabbitMQ thật khi `NOTIFICATION_INFRA_TEST=true`. Cần `SUBSCRIPTION_TEST_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/`, `SUBSCRIPTION_TEST_MYSQL_USER`, `SUBSCRIPTION_TEST_MYSQL_PASSWORD` và các biến `REDIS_PASSWORD`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` tương ứng Docker. Tài khoản RabbitMQ cần quyền quản trị để test tạo/xóa vhost tạm; MySQL cần quyền tạo/xóa schema tạm. Test dùng schema/vhost/cache riêng, không xóa dữ liệu ứng dụng.

```powershell
.\mvnw.cmd test "-Dtest=NotificationInfrastructureTest,ResilientCacheTest,WebSocketAuthInterceptorTest"
```

Kiểm tra rollback, publisher confirm, routing lỗi và gửi lại, chống trùng, DLQ, phân trang/phân quyền, cache JSON/TTL và thời điểm xóa cache. FE chạy `npm test -- --watch=false` và `npm run build`.

## Kiểm thử thuê phòng và gói dịch vụ

Từ BE, với MySQL 8 local; URL chỉ chấp nhận localhost/127.0.0.1. Test tự tạo/xóa schema ngẫu nhiên, không dùng DB ứng dụng:

```powershell
$env:SUBSCRIPTION_TEST_MYSQL_URL='jdbc:mysql://localhost:3306/'
$env:SUBSCRIPTION_TEST_MYSQL_USER='root'
# Đặt SUBSCRIPTION_TEST_MYSQL_PASSWORD từ cấu hình riêng, không commit mật khẩu.
.\mvnw.cmd '-Dtest=RentalWorkflowTest,RentalValidationTest,RentalMigrationTest' test
```

15 test gồm transactions song song bằng CountDownLatch, duplicate submit/accept, accept-vs-submit, confirm-vs-timeout cả trước/đúng boundary, unique DB bypass, khác phòng cùng ACTIVE, bàn giao/lịch sử, WHOLE/foreign room, RBAC/IDOR, PDF Unicode/hash/snapshot, storage fail/retry, lease recovery, MySQL timeout 3 giây, room status bypass và migration conflict/repair.

`CloudinaryRentalPdfStorageTest` opt-in qua RENTAL_TEST_CLOUDINARY=true và ba biến RENTAL_TEST_CLOUD_NAME/KEY/SECRET: roundtrip thật, unsigned URL bị từ chối, xóa tệp tổng hợp trong finally. Không chạy mặc định để tránh phụ thuộc external account.

Từ FE:

```powershell
npx ng test --watch=false
npm run build
```

Fullstack thực: khởi động BE trên 18080 với DB_NAME trỏ vào **database tạm riêng**, áp dụng các migration trong checkout hiện tại, giữ worker bật. FE build production rồi chạy `node scripts/rental-e2e-preview.cjs`. Đặt PLAYWRIGHT_MODULE_PATH nếu Playwright không nằm trong node_modules, RENTAL_E2E_ISOLATED=true, chạy `node scripts/rental-booking.e2e.cjs`. Script dùng các tài khoản demo V2, tạo tin trong DB tạm, đặt qua UI, duyệt qua UI, tải Cloudinary PDF, xác nhận, kiểm tra RENTED/IDOR/redirect/mobile; ảnh lưu ở `docs/screenshots/rental_*.png`. Sau test cần dừng BE tạm, xóa các raw/authenticated PDF của chính database tạm và drop schema tạm. Không chạy script lên DB ứng dụng hoặc production.

### Gói dịch vụ và chữ ký thanh toán

`SubscriptionSchemaMigrationTest` dùng MySQL thật, tự tạo schema ngẫu nhiên có tiền tố `subscription_test_` và chỉ dọn schema đó. Không dùng database ứng dụng làm schema thử nghiệm.

Chạy với một MySQL thử nghiệm riêng, Java 21 và Maven wrapper:

```powershell
$env:SUBSCRIPTION_TEST_MYSQL_URL = 'jdbc:mysql://127.0.0.1:33317/'
$env:SUBSCRIPTION_TEST_MYSQL_USER = 'root'
# SUBSCRIPTION_TEST_MYSQL_PASSWORD được cấp qua môi trường nếu server yêu cầu.
.\mvnw.cmd "-Dtest=SubscriptionSchemaMigrationTest,PackageCatalogApiTest,VnPaySignerTest,VnPayQueryClientTest" test
```

Các test MySQL bỏ qua khi chưa đặt URL thử nghiệm; test chữ ký không cần database. Bài kiểm tra schema gồm migration không tạo bảng phân bổ riêng, JPA validate toàn bộ entity, nâng cấp tin/danh mục cũ, guard lịch sử không xác minh được, FK/check cho ưu tiên trên tin, unique cho ACTIVE/SCHEDULED/CHANGE/Boost/mã giao dịch và nâng V3 lên V4 khi đã có dữ liệu thanh toán.

API test dùng MockMvc, controller/service/repository thật, SecurityConfig của ứng dụng và người dùng giả lập Spring Security; kiểm tra danh mục, tháng lịch/năm nhuận, snapshot, quyền sở hữu, gia hạn/đổi gói, callback VNPay sai/lặp/thu trùng, hóa đơn, quota/slot/Boost và các giao dịch đồng thời. Callback được ký bằng khóa giả của test trong schema riêng, chưa phải kết quả thanh toán qua website VNPay. Không thay thế nghiệm thu IPN công khai hoặc kiểm tra toàn bộ hạ tầng/JWT thật; xem [VNPay](#vnpay).

Các số lượng test và kết quả sandbox có ngày trong tài liệu này là bằng chứng lịch sử, không phải kết quả kiểm tra lại ngày hợp nhất tài liệu. Test API dùng security fixtures không thay thế nghiệm thu JWT/hạ tầng thật; smoke FE mô phỏng không thay thế E2E.
