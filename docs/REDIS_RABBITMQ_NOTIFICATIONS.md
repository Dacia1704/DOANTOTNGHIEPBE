# Redis cache và thông báo RabbitMQ

## Khởi chạy

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
Flyway tự chạy migration V6 để tạo `notification_outbox` và index inbox. Không sửa các migration đã chạy.

## Cache Redis

Cache danh mục tiện ích, loại phòng, loại cơ sở, tỉnh/thành và phường/xã. Các API và cấu trúc response hiện có được giữ nguyên.

- Key có prefix `rental:v1:`, tách theo danh mục và tham số truy vấn.
- TTL mặc định 30 phút, thay bằng biến `CACHE_TTL`, ví dụ `10m`.
- Thêm/sửa/xóa danh mục và đồng bộ địa chỉ xóa cache liên quan sau khi giao dịch commit. Rollback giữ cache cũ.
- Dữ liệu danh mục được serialize JSON có thông tin kiểu và thời gian. Không cache các entity có quan hệ lazy hoặc dữ liệu riêng theo tài khoản.
- Redis không khả dụng: đọc từ MySQL, ghi log cảnh báo. Xóa cache thất bại có thể giữ dữ liệu danh mục cũ đến khi TTL hết; không dùng cache này để quyết định quyền, giá hợp đồng, số phòng trống hoặc hạn mức.
- Xóa cache dùng SCAN theo lô để tránh chặn Redis bằng KEYS.

## Luồng thông báo

Nghiệp vụ → outbox MySQL cùng giao dịch → relay RabbitMQ → consumer → inbox MySQL → WebSocket → FE.

Đã kết nối các sự kiện yêu cầu thuê, hợp đồng/PDF/trả phòng, xử lý báo cáo và duyệt/từ chối tin đăng. Các `rental_events` cũ chưa giao được chuyển vào outbox bởi tác vụ riêng, kể cả khi tắt worker PDF.

Relay chạy mỗi 2 giây, dùng `FOR UPDATE SKIP LOCKED` cho nhiều worker. Chỉ đánh dấu `published_at` khi nhận publisher confirm và không bị trả về do sai routing. Khi lỗi, sự kiện được giữ lại, thử lại với thời gian chờ tăng dần tối đa 5 phút. Có thể chỉnh `NOTIFICATIONS_RELAY_DELAY_MS` hoặc tắt relay bằng `NOTIFICATIONS_RELAY_ENABLED=false`.

Exchange `rental.notifications`, routing key `notification.created`, queue bền vững `rental.notifications.inbox`. Message được gửi với delivery mode persistent. Consumer retry 3 lần; message không xử lý được đi tới dead-letter queue `rental.notifications.dead.inbox` qua exchange `rental.notifications.dead`. Theo dõi queue này trong RabbitMQ Management; sau khi sửa nguyên nhân có thể chuyển message trở lại exchange chính.

Mỗi sự kiện giữ nguyên ID qua các lần gửi lại; primary key inbox ngăn thông báo trùng và giữ trạng thái đã đọc. Việc ghi inbox và gửi realtime được tách tại thời điểm commit. Sự kiện của tài khoản đã xóa được bỏ qua. Outbox đã publish được dọn sau 7 ngày; lịch sử thông báo vẫn giữ trong inbox.

WebSocket dùng cùng kết nối STOMP/JWT với chat (`/ws`). Client chỉ subscribe `/user/queue/notifications`; payload `{"kind":"CHANGED"}` yêu cầu lấy lại inbox/số chưa đọc. FE tự đồng bộ khi kết nối lại, khi cửa sổ được focus và mỗi 60 giây. Logout/đổi tài khoản hủy request và xóa số chưa đọc cũ.

## API thông báo

Tất cả endpoint yêu cầu access token. Backend lấy user từ JWT, không nhận user ID từ client. Response dùng `ApiResponse<T>` hiện có.

- `GET /api/notifications?page=0&size=20&unreadOnly=false`: `PageResponse<NotificationResponse>`, mới nhất trước, thứ tự ổn định theo thời gian và ID. `size` từ 1 đến 100.
- `GET /api/notifications/unread-count`: `data` là số thông báo chưa đọc.
- `PUT /api/notifications/{id}/read`: đánh dấu đã đọc; gọi lại được; ID không thuộc tài khoản trả 404.
- `PUT /api/notifications/read-all`: đánh dấu toàn bộ thông báo của tài khoản hiện tại đã đọc.

Một mục inbox có dạng:

```json
{
  "id": "event-uuid",
  "type": "RENTAL_AGREEMENT",
  "title": "Cập nhật thuê phòng",
  "body": "Hợp đồng PDF đã sẵn sàng. Vui lòng xem và xác nhận trước hạn.",
  "referenceType": "RENTAL_AGREEMENT",
  "referenceId": "agreement-uuid",
  "read": false,
  "createdAt": "2026-11-08T10:00:00"
}
```

Thời gian không kèm offset dùng múi giờ Việt Nam, khớp dữ liệu thuê phòng hiện có. FE hiển thị giờ Việt Nam. Swagger liệt kê API tại `/swagger-ui/index.html`.

## Giao diện

Chuông trên header dùng chung cho người thuê, chủ trọ và quản trị viên. Hiển thị badge chưa đọc, danh sách mới nhất, lọc chưa đọc, xem thêm, đọc từng mục/đọc tất cả, trạng thái rỗng và thử lại khi lỗi. Hỗ trợ bàn phím, Escape, trả focus và màn hình nhỏ.

Thông báo hợp đồng mở chi tiết hợp đồng; yêu cầu thuê mở danh sách tương ứng vai trò; tin đăng mở trang quản lý tin đăng. Thông báo báo cáo hiển thị kết quả xử lý ngay trong danh sách.

## Kiểm thử

`NotificationInfrastructureTest` chạy với MySQL/Redis/RabbitMQ thật khi `NOTIFICATION_INFRA_TEST=true`. Cần `SUBSCRIPTION_TEST_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/`, `SUBSCRIPTION_TEST_MYSQL_USER`, `SUBSCRIPTION_TEST_MYSQL_PASSWORD` và các biến `REDIS_PASSWORD`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` tương ứng Docker. Tài khoản RabbitMQ cần quyền quản trị để test tạo/xóa vhost tạm; MySQL cần quyền tạo/xóa schema tạm. Test dùng schema/vhost/cache riêng, không xóa dữ liệu ứng dụng.

```powershell
.\mvnw.cmd test "-Dtest=NotificationInfrastructureTest,ResilientCacheTest,WebSocketAuthInterceptorTest"
```

Kiểm tra rollback, publisher confirm, routing lỗi và gửi lại, chống trùng, DLQ, phân trang/phân quyền, cache JSON/TTL và thời điểm xóa cache. FE chạy `npm test -- --watch=false` và `npm run build`.
