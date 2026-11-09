# Quy ước controller/service và đối chiếu database

Đối chiếu ngày 08/11/2026 trên code hiện tại, migration V1–V6 và database local cấu hình bởi `DB_NAME`.

## Controller → service interface → implementation

Theo mẫu module `room`, controller chỉ inject interface trong `service`; implementation đặt trong `service.impl`, có `@Service`, `implements` và `@Override`. Transaction và nghiệp vụ nằm ở implementation, controller tiếp nhận request, lấy danh tính từ authentication và tạo HTTP response.

Đã áp dụng cho `NotificationService`, `RentalService`, `ListingContactService`, `ListingReviewService`, `ReportEvidenceService`, `BoostAllocationService`. Các service đã theo mẫu ở room, auth, chat, property, payment, identity và các danh mục gói dịch vụ tiếp tục giữ cấu trúc đó.

`RentalContractService` tách phần kiểm tra quyền/trạng thái PDF, tải file và xác minh SHA-256 khỏi controller. Tải file diễn ra ngoài transaction; transaction đọc quyền/trạng thái kết thúc trước khi gọi storage. HTTP header, MIME type và lựa chọn inline/download vẫn thuộc controller.

Các worker/scheduler, bộ tạo PDF và adapter hạ tầng không phải entry point nghiệp vụ của controller. Không cần tạo interface cho từng helper chỉ để tăng số lớp.

## Vì sao có bảng không có entity?

Database local có **51 bảng**. Migration ứng dụng tạo **50 bảng**; **47 entity** ánh xạ đầy đủ 47 bảng trong số đó. Bảng còn lại ngoài migration ứng dụng là `flyway_schema_history`, do Flyway quản lý.

Không có entity nào thiếu bảng trong migration. Không phát hiện bảng ứng dụng dư ngoài migration V1–V6 trong database local.

Ba bảng ứng dụng không có JPA entity đều đang được đọc/ghi bằng `JdbcTemplate`:

- **`rental_events`**: nhật ký chuyển trạng thái thuê phòng, có actor, người nhận, reference và hash PDF. `RentalEvents` ghi cùng transaction nghiệp vụ; các sự kiện cũ chưa giao được chuyển sang hàng đợi thông báo. Cần giữ để truy vết lịch sử, không thay bằng message tạm trong RabbitMQ.
- **`rental_pdf_assets`**: ghi trước ý định upload và theo dõi từng asset của mỗi lần thử tạo PDF. `RentalPdfJobs` tạo bản ghi trước network I/O; `RentalPdfWorker` dùng để xử lý file mồ côi khi upload timeout, tiến trình dừng hoặc worker cũ hoàn tất muộn. Một cột `pdf_public_id` trên hợp đồng không lưu được tất cả asset của nhiều lần thử. Cần giữ.
- **`notification_outbox`**: lưu ý định gửi thông báo trong cùng transaction với nghiệp vụ. `NotificationPublisher` ghi, `NotificationOutboxRelay` gửi qua RabbitMQ bằng publisher confirm và lên lịch gửi lại khi lỗi. Cần giữ cho cơ chế hiện tại để việc cập nhật nghiệp vụ không phụ thuộc broker đang online. Các bản ghi đã publish được dọn sau 7 ngày.

`flyway_schema_history` không phải entity nghiệp vụ. Flyway tự tạo và sử dụng để lưu version/checksum migration; Hibernate không nên quản lý bảng này.

## Có cần tạo thêm bảng hoặc entity không?

**Không cần thêm bảng cho lần refactor này. Không cần tạo JPA entity chỉ để số entity bằng số bảng.** Các bảng dùng SQL trực tiếp vẫn tham gia transaction của Spring và có nơi chịu trách nhiệm rõ ràng.

`notifications` đã có entity `Notification`; đây là inbox hiển thị cho người dùng và lưu trạng thái đã đọc. Nó khác `notification_outbox`, là trạng thái giao message. `rental_events` cũng không thay được outbox chung: chỉ ghi sự kiện thuê phòng và giữ lịch sử audit, trong khi thông báo còn có báo cáo/tin đăng và có chính sách dọn dữ liệu giao message riêng.

Có thể thiết kế lại để dùng `notifications` đồng thời làm inbox/outbox bằng cách thêm metadata giao message, nhưng khi đó phải đổi hợp đồng lưu/giao thông báo và cơ chế retry. Hiện không có yêu cầu cần đổi các trách nhiệm này, nên giữ thiết kế hiện có. Không xóa bảng hoặc sửa migration đã áp dụng để làm số bảng khớp số entity.

## Kiểm tra chống tái diễn

- `ControllerServiceArchitectureTest`: quét tất cả REST controller, chặn inject service concrete class hoặc repository trực tiếp; kiểm tra interface có implementation tương ứng trong `service.impl`.
- `SchemaEntityCoverageTest`: đối chiếu tên bảng trong migration và entity, yêu cầu bảng dùng JDBC phải có tên và file chịu trách nhiệm rõ ràng.
- `NotificationInfrastructureTest`, `RentalWorkflowTest`, `ReviewReportWorkflowTest` kiểm tra hành vi notification/thuê phòng/báo cáo với MySQL và broker/cache thật theo cấu hình kiểm thử.

Refactor giữ URL, payload và response API để FE hiện tại tiếp tục sử dụng.
