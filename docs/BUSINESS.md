# Nghiệp vụ và dữ liệu backend

[Mục lục](../README.md) · [API chung](api.md) · [Vận hành](OPERATIONS.md)

## Property, Room và Listing

Property là cơ sở vật lý; Room là một đơn vị cho thuê độc lập. Chủ sở hữu, địa chỉ và tọa độ thuộc Property. `WHOLE` có đúng một Room đại diện; `BY_ROOM` có ít nhất một Room. Không thêm quantity/stock hay bảng rental mode; PropertyType là danh mục phẳng, RoomType có cây cha/con.

Mặc định Room chỉ là input khi tạo cơ sở: trường null dùng mặc định, số 0 là phí thực sự. Room number được nhập rõ, trim và duy nhất trong cơ sở; tầng 0 hợp lệ, tầng null không được suy từ mã phòng. Tiện ích chung và riêng độc lập. Tạo Property và toàn bộ Room cùng transaction, một Room lỗi thì rollback. Hợp đồng và request thuê luôn chọn đúng một Room thuộc Property của Listing.

Không thêm Floor table, BaseEntity, soft delete hoặc cascade ALL tùy tiện. Dữ liệu cũ không tự nhóm cơ sở theo địa chỉ. Tài sản riêng chưa tạo tin không xuất hiện công khai.

## Lượt đăng và ưu tiên

Tạo tin trừ một lượt đăng và lựa chọn ưu tiên/Boost trong cùng transaction. Hạn mức lấy từ snapshot kỳ gói; các cột consumed ghi nhận lượt độc lập với trạng thái hiển thị. Ẩn tin, hết hạn, bỏ/đổi ưu tiên hoặc Boost không hoàn lượt.

Chỉ admin từ chối PENDING còn hạn ở lần duyệt đầu (`initialReview=true`, chưa xuất bản) mới hoàn đúng lượt về nguồn gói tương ứng. Sau duyệt, ẩn, hết hạn hoặc gửi lại, cờ này không bật lại. Cùng tin/kỳ/mức sử dụng lại lượt đã trừ; tin đã được hoàn lượt sẽ trừ lại khi gửi duyệt. Free có 3 lượt và không tự reset hàng tháng. Nâng gói ngay chuyển lượt đã dùng sang kỳ thay thế, không cấp lại lượt cũ.

Listing thuộc Property, tạo bằng propertyId. Cơ sở phải thuộc chủ trọ và có Room AVAILABLE; không tạo thêm tin PENDING/PUBLISHED còn hiệu lực cho cùng cơ sở. PENDING có hạn 7 ngày; admin duyệt mới bắt đầu một tháng lịch hiển thị. Khi chọn Boost lúc tạo, gói phải đã mua, ACTIVE, còn hạn/lượt và không đồng thời chọn ưu tiên riêng của gói dịch vụ.

Truy vấn công khai chỉ trả PUBLISHED còn hạn, chọn tin mới nhất theo publishedAt rồi ID nếu dữ liệu cũ có nhiều tin/cơ sở. Bộ lọc loại phòng/giá/trạng thái/tiện ích phải khớp cùng một Room; tiện ích có thể ở Room hoặc Property. Lọc trước phân trang, giữ tổng kết quả và thứ tự ưu tiên/thời gian; API không dựa vào job hết hạn để bảo vệ công khai.

## Gói dịch vụ, kỳ quyền lợi và thanh toán

- `service_packages`, `service_package_prices`: danh mục và giá 1/3/6/12 tháng; max_listings null là không giới hạn. Cấu hình quyền lợi chốt vào snapshot khi đặt mua.
- `service_subscription_orders`: từng lần mua/gia hạn/đổi gói, giá và cấu hình đã chốt, hạn thanh toán và dấu vết áp dụng để xử lý lặp an toàn.
- `service_package_subscriptions`: trạng thái SCHEDULED/ACTIVE/EXPIRED/CANCELLED; database giới hạn một ACTIVE và một SCHEDULED/chủ trọ.
- `service_subscription_periods`: kỳ quyền lợi bất biến, gắn một đơn, không cập nhật giá/quyền lợi lịch sử. Service khóa và kiểm tra không chồng kỳ; unique index không thay kiểm tra khoảng thời gian.
- Gia hạn nối kỳ mới và giữ snapshot cũ. CHANGE đã trả tiền vẫn cần admin; hạ gói xếp kỳ sau, nâng gói ngay dùng giá trị/tháng quy đổi. Gói cũ CANCELLED khi nâng ngay, giữ lịch sử; SCHEDULED chặn gia hạn gói cũ.
- Boost mua riêng có snapshot và hạn thanh toán 15 phút. Mỗi phân bổ thuộc một gói cụ thể, không tự chuyển sang gói khác. active quyết định lựa chọn đang áp dụng; lượt đã dùng vẫn được giữ sau khi bỏ.
- `payments` trỏ đúng một đơn gói dịch vụ hoặc đăng ký Boost bằng FK khớp type. Merchant order ID và mã giao dịch nhà cung cấp có unique guard. SUCCESS có paid_at; mỗi payment tối đa một hóa đơn.
- Thu trùng/quá hạn/không đủ điều kiện vẫn giữ SUCCESS và hóa đơn, đánh dấu requires_review/review_reason; không cấp quyền lần nữa. Return trình duyệt không tự ghi nhận thu tiền; chỉ IPN/QueryDr đã xác minh.
- Giữ FK lịch sử thanh toán theo RESTRICT; không xóa dây chuyền tài khoản/tin. Không dùng payments cho tiền thuê/cọc.

## Thuê phòng và PDF

### Hành vi

1. Khách chọn đúng một Room thuộc Property của tin PUBLISHED còn hạn, nhập thông tin và gửi yêu cầu. Nhiều khách có thể cùng PENDING; một khách chỉ có một PENDING/phòng. Gửi lại payload giống hệt trả request ban đầu; payload khác trả 409. Không dùng Idempotency-Key riêng: unique pending guard + so sánh toàn bộ payload là cơ chế replay.
2. Chủ trọ chấp nhận: request ACCEPTED, hợp đồng PENDING_CONFIRMATION giữ phòng 48 giờ, snapshot tên/thông tin các bên, tài sản, giá/phí, thời hạn và điều khoản. Tự từ chối các PENDING khác cùng phòng. Accept lại cùng request trả hợp đồng cũ.
3. Worker lấy job đã commit, render PDF tiếng Việt bằng JasperReports + Noto Sans (OFL kèm font), upload Cloudinary raw/authenticated. PDF READY chỉ sau khi lưu file và SHA-256 thành công. File cố định sau phát hành.
4. Khách tải PDF qua API có JWT. Angular kiểm tra SHA-256 của chính blob đã tải trước khi bật xác nhận. Khách đồng ý đúng phiên bản → ACTIVE và Room RENTED trong cùng giao dịch. Chủ trọ không thể xác nhận thay khách.
5. Khách decline hoặc hết 48 giờ → CANCELLED, giữ request ACCEPTED làm lịch sử. Phòng không còn hold; bảo toàn MAINTENANCE. Hết hạn request sau 7 ngày. ACTIVE chỉ đóng qua xác nhận bàn giao của chủ trọ; không tự giải phóng theo endDate.

Mọi ngày nghiệp vụ theo Asia/Ho_Chi_Minh; ngày kết thúc = `start.plusMonths(months).minusDays(1)`, bao gồm trường hợp cuối tháng. MVP không đặt trước nhiều khoảng thời gian trên cùng phòng.

WHOLE dùng quy ước hiện có: đúng một Room đại diện. Mapping nhiều Room trả lỗi, không tự sửa/tạo dữ liệu. `bookingAvailability` do BE tính gồm AVAILABLE/HELD/RENTED/MAINTENANCE; FE không dùng AVAILABLE vật lý để suy ra có thể đặt.

### Cơ sở dữ liệu và giao dịch

- Room luôn khóa trước, sau đó mới request/agreement. Lookup roomId ban đầu là scalar, tránh entity cache giữ trạng thái cũ trước khi chờ khóa.
- Các giao dịch chuyển trạng thái dùng READ_COMMITTED: tránh snapshot REPEATABLE_READ được tạo bởi lần đọc user trước khi chờ Room lock.
- `occupied_room_id` STORED + UNIQUE bảo vệ DRAFT/PENDING_CONFIRMATION/ACTIVE kể cả khi bypass service. UNIQUE rental_request_id ngăn hai hợp đồng/request.
- `open_request_key` là generated **VIRTUAL** + UNIQUE. MySQL không cho base column của generated STORED dùng CASCADE; VIRTUAL giữ nguyên FK tenant hiện có và vẫn được InnoDB đánh unique index.
- MySQL `innodb_lock_wait_timeout=3` được cấu hình trên từng kết nối Hikari; hint JPA cũng được đặt. Lock timeout/deadlock trả 409 ROOM_BUSY. Các unique guard trả 409 tương ứng.
- Không render PDF hoặc gọi Cloudinary trong transaction giữ Room lock.

### Job, thông báo và audit

Agreement chính là hàng đợi bền: PENDING/FAILED, attempt_count, next_retry_at, claim token và lease 5 phút. Tối đa 6 lần thử, backoff 60 giây tới 30 phút. PROCESSING quá lease được reclaim; token cũ không thể ghi đè kết quả mới. Quá 6 lần phải được hỗ trợ vận hành xử lý; không tự mở phòng vì PDF lỗi. Sau deadline, timeout đóng hold an toàn.

Upload intent được lưu trong `rental_pdf_assets` trước HTTP. Reconciliation kiểm tra file không được agreement tham chiếu, tránh lease còn hoạt động rồi xóa bằng đúng raw/authenticated. Intent được giữ và luân phiên kiểm tra để xử lý upload hoàn tất muộn sau timeout. Cần có chính sách retention/archival khi vận hành lâu dài.

`rental_events` lưu audit thuê phòng: chỉ actor, recipient, resource ID, transition, timestamp và hash, không chứa CCCD/token/request body. Các sự kiện chưa giao được chuyển sang notification_outbox; relay RabbitMQ và consumer giao vào inbox theo [luồng thông báo](OPERATIONS.md#thong-bao). ID event là correlation ID của transition. UI đọc trạng thái mới bằng API, không phụ thuộc vào websocket.

Cấu hình tùy chọn:

```yaml
app:
  rental:
    jobs-enabled: true
    job-delay-ms: 5000
    cleanup-delay-ms: 300000
    contract-terms: "Nội dung điều khoản đã được chủ sản phẩm rà soát và phê duyệt"
```

Cloudinary dùng bean/config `app.cloudinary` sẵn có. Runtime PDF bắt buộc `resource_type=raw`, `type=authenticated`; không dùng hàm uploadRaw public của ImageStorage. Endpoint BE kiểm tra party/admin trước khi tải qua signed API, đối chiếu SHA-256, trả application/pdf, no-store, inline hoặc attachment khi `download=true`. Không trả publicId/URL Cloudinary ra FE.

### Mẫu hợp đồng JasperReports

`src/main/resources/reports/rental-contract.jrxml` là mẫu A4 gồm quốc hiệu, thông tin Bên A/Bên B và bốn mục: nội dung thuê, trách nhiệm Bên A, trách nhiệm Bên B, điều khoản thực hiện. `RentalContractPdfService` compile mẫu một lần, fill từ snapshot bằng `JREmptyDataSource`, rồi xuất bằng `JRPdfExporter`. JasperReports core, PDF và JDT module cùng phiên bản 7.0.8; JDT hỗ trợ compile mẫu bằng classloader khi chạy Spring Boot executable JAR. PDFBox chỉ dùng trong test để đọc/kiểm tra kết quả.

Tiền được trình bày theo định dạng Việt Nam và có bằng chữ cho tiền thuê, tiền cọc; ngày thuê hiển thị dd/MM/yyyy. Số người tối đa lấy từ snapshot của phòng. Snapshot mới lưu thêm CCCD chủ trọ; các snapshot cũ vẫn đọc được. Ngày/nơi cấp CCCD, địa chỉ thường trú chủ trọ và ngày thanh toán chưa được thu thập trong luồng hiện tại nên để dòng chấm, không tự suy đoán. `app.rental.contract-terms` vẫn được chốt vào snapshot và hiển thị trong phần thỏa thuận bổ sung.

Font thường và đậm được đóng gói, nhúng vào PDF qua `jasperreports_extension.properties` và `fonts/rental-fonts.xml`; không cần cài font trên máy chủ. Nội dung snapshot là plain text, không diễn giải HTML hoặc biểu thức Jasper. Trường dài và thỏa thuận bổ sung tự giãn/chuyển trang; có khu vực ký bản giấy và ghi nhận đồng ý trong ứng dụng. PDF đã phát hành vẫn giữ nguyên bytes/hash.

Kiểm tra mẫu độc lập với MySQL/Cloudinary và tạo bản xem thử bằng dữ liệu tổng hợp:

```powershell
.\mvnw.cmd '-Dtest=RentalContractPdfServiceTest' '-Drental.pdf.preview=target/contract-preview' test
```

PDF và ảnh từng trang nằm trong `target/contract-preview/`. Sửa mẫu chỉ ảnh hưởng các lần tạo PDF tiếp theo, không thay file của hợp đồng đã phát hành.

**Giới hạn cần xử lý trước production:** repo chưa có framework mã hóa trường dữ liệu cá nhân. CCCD/địa chỉ và contract_snapshot hiện lưu plaintext trong MySQL; response list không chứa chúng, detail chỉ trả CCCD đã mask, PDF giới hạn đúng party/admin. Cần cấu hình mã hóa storage/backup, quản lý khóa, phân quyền DB và retention. Mẫu điều khoản mặc định được đánh dấu tham khảo; phải thay bằng template đã duyệt. Xác nhận UI là đồng ý điện tử trong ứng dụng; không mô tả là chữ ký số chứng thực.

## Review, Report và Chat

Review gắn hợp đồng, mỗi loại chỉ tạo một lần. Điều kiện tạo lấy từ eligibility; quyền đọc review tenant độc lập với điều kiện tạo. Report tạo trước, bổ sung minh chứng sau; xử lý report không tự ẩn tin/khóa tài khoản. Contract và giới hạn chi tiết nằm tại [Review](api.md#review), [Report](api.md#report).

Chat chỉ dành cho các bên hợp lệ và tài khoản ACTIVE. Listing chat suy chủ trọ qua Property, kiểm tra thành viên ở mọi thao tác; hỗ trợ dùng admin cố định trong seed. Tạo hội thoại và participant cùng transaction; retry không tạo hội thoại trùng. Lưu tin và cập nhật lastMessageAt cùng transaction, phát realtime sau commit. Không tin senderId từ client hoặc dùng topic công khai cho chat riêng. [API Chat](api.md#chat).
