# Tài khoản chat hỗ trợ

Tenant và landlord mở hỗ trợ qua `POST /api/chat/conversations/support`. BE chọn tài khoản nhận tin; FE không gửi ID admin.

## Cấu hình

`app.chat.support-admin-email` lấy từ biến môi trường `CHAT_SUPPORT_ADMIN_EMAIL`, mặc định **`admin@gmail.com`** để khớp bộ seed V2 hiện tại.

Tài khoản được chỉ định phải tồn tại, có vai trò ADMIN và trạng thái ACTIVE. Nếu không thỏa mãn, API trả 503 `SUPPORT_ADMIN_UNAVAILABLE`; không tự chuyển nội dung sang admin khác.

Ví dụ cấu hình một tài khoản khác:

```properties
CHAT_SUPPORT_ADMIN_EMAIL=support@example.com
```

Khởi động lại BE sau khi đổi cấu hình. Với database cũ dùng `admin.demo@trotot.example.test`, đặt biến môi trường thành địa chỉ đó nếu muốn tiếp tục sử dụng tài khoản và hội thoại hỗ trợ cũ. Việc đổi email tài khoản hỗ trợ không tự di chuyển lịch sử sang admin mới.

## Vì sao admin có thể chưa có hội thoại?

Danh sách chat chỉ chứa hội thoại tài khoản đang đăng nhập tham gia. Admin hỗ trợ không tự đọc toàn bộ chat riêng giữa tenant và landlord; admin khác cũng không được đọc hộp thư của tài khoản hỗ trợ.

Bộ seed mới có 100 hội thoại tenant–landlord, chưa có hội thoại hỗ trợ. Người dùng mở hỗ trợ sẽ tạo hội thoại với tài khoản được cấu hình; gửi tin nhắn sẽ lưu vào MySQL và phát sự kiện đến đúng người nhận. Khi mở lại, hai phía lấy danh sách/lịch sử từ BE.

## Nguyên nhân lỗi đã sửa

Code cũ cố định cả luồng tạo và kiểm tra quyền đọc hội thoại vào `admin.demo@trotot.example.test`, trong khi seed mới tạo `admin@gmail.com`. Vì thiếu tài khoản theo email cũ, yêu cầu mở hỗ trợ bị từ chối trước khi tạo hội thoại.

## Kiểm tra

`ChatFlowTest` kiểm tra mặc định theo seed hiện tại, tài khoản cấu hình riêng, trạng thái/vai trò người nhận và không chọn admin thay thế. `ChatSupportWorkflowTest` chạy trên MySQL riêng khi đặt `CHAT_TEST_MYSQL_URL=jdbc:mysql://127.0.0.1:<port>/`, tạo schema ngẫu nhiên và dọn sau kiểm thử. Kiểm tra dùng REST/JWT security, JPA và transaction thực; broker được thay bằng mock để xác nhận đúng đích nhận mà không gửi tin đến người dùng thật.

Test MySQL tập trung luồng chat, nạp baseline V1 và metadata vai trò V7 vào schema tạm; không dùng để xác nhận chuỗi migration của toàn ứng dụng.

Đã kiểm chứng: 26 tests qua, trong đó có 4 tests MySQL thực với REST/JWT, lưu lịch sử và phân quyền hội thoại. Chưa thay cấu hình hay khởi động lại tiến trình BE đang chạy trong IDE của người dùng.
