# Luồng đặt phòng và hợp đồng

Triển khai BE trong `src/main/java/com/tamdaptit/rentalmanagement/rental`; FE Angular trong `../DOANTOTNGHIEPFE/src/app/features/rental` và booking dialog hiện có của Listing. Không dùng payments/VNPay cho luồng thuê.

## Hành vi

1. Khách chọn đúng một Room thuộc Property của tin PUBLISHED còn hạn, nhập thông tin và gửi yêu cầu. Nhiều khách có thể cùng PENDING; một khách chỉ có một PENDING/phòng. Gửi lại payload giống hệt trả request ban đầu; payload khác trả 409. Không dùng Idempotency-Key riêng: unique pending guard + so sánh toàn bộ payload là cơ chế replay.
2. Chủ trọ chấp nhận: request ACCEPTED, hợp đồng PENDING_CONFIRMATION giữ phòng 48 giờ, snapshot tên/thông tin các bên, tài sản, giá/phí, thời hạn và điều khoản. Tự từ chối các PENDING khác cùng phòng. Accept lại cùng request trả hợp đồng cũ.
3. Worker lấy job đã commit, render PDF tiếng Việt bằng JasperReports + Noto Sans (OFL kèm font), upload Cloudinary raw/authenticated. PDF READY chỉ sau khi lưu file và SHA-256 thành công. File cố định sau phát hành.
4. Khách tải PDF qua API có JWT. Angular kiểm tra SHA-256 của chính blob đã tải trước khi bật xác nhận. Khách đồng ý đúng phiên bản → ACTIVE và Room RENTED trong cùng giao dịch. Chủ trọ không thể xác nhận thay khách.
5. Khách decline hoặc hết 48 giờ → CANCELLED, giữ request ACCEPTED làm lịch sử. Phòng không còn hold; bảo toàn MAINTENANCE. Hết hạn request sau 7 ngày. ACTIVE chỉ đóng qua xác nhận bàn giao của chủ trọ; không tự giải phóng theo endDate.

Mọi ngày nghiệp vụ theo Asia/Ho_Chi_Minh; ngày kết thúc = `start.plusMonths(months).minusDays(1)`, bao gồm trường hợp cuối tháng. MVP không đặt trước nhiều khoảng thời gian trên cùng phòng.

WHOLE dùng quy ước hiện có: đúng một Room đại diện. Mapping nhiều Room trả lỗi, không tự sửa/tạo dữ liệu. `bookingAvailability` do BE tính gồm AVAILABLE/HELD/RENTED/MAINTENANCE; FE không dùng AVAILABLE vật lý để suy ra có thể đặt.

## Cơ sở dữ liệu và giao dịch

Migration mới: `V3__rental_booking_contract_workflow.sql`, sau V1 và V2 thực tế trong repository và lịch sử local. Không sửa V1/V2. Các trường mới nullable cho legacy; booking mới yêu cầu đầy đủ tại DTO/service.

- Room luôn khóa trước, sau đó mới request/agreement. Lookup roomId ban đầu là scalar, tránh entity cache giữ trạng thái cũ trước khi chờ khóa.
- Các giao dịch chuyển trạng thái dùng READ_COMMITTED: tránh snapshot REPEATABLE_READ được tạo bởi lần đọc user trước khi chờ Room lock.
- `occupied_room_id` STORED + UNIQUE bảo vệ DRAFT/PENDING_CONFIRMATION/ACTIVE kể cả khi bypass service. UNIQUE rental_request_id ngăn hai hợp đồng/request.
- `open_request_key` là generated **VIRTUAL** + UNIQUE. MySQL không cho base column của generated STORED dùng CASCADE; VIRTUAL giữ nguyên FK tenant hiện có và vẫn được InnoDB đánh unique index.
- MySQL `innodb_lock_wait_timeout=3` được cấu hình trên từng kết nối Hikari; hint JPA cũng được đặt. Lock timeout/deadlock trả 409 ROOM_BUSY. Các unique guard trả 409 tương ứng.
- Không render PDF hoặc gọi Cloudinary trong transaction giữ Room lock.

Chạy `docs/rental-legacy-conflicts.sql` để rà soát trước upgrade. Migration dùng bảng tạm unique để dừng **trước khi ALTER** nếu legacy có hai hợp đồng mở/phòng hoặc hai hợp đồng/request. Không tự xóa/chuyển trạng thái lịch sử. Nếu Flyway có mục V3 thất bại: kiểm tra phần DDL đã áp dụng, xử lý nguyên nhân rồi mới repair mục thất bại. Không repair checksum của migration thành công để che lỗi.

## Job, thông báo và audit

Agreement chính là hàng đợi bền: PENDING/FAILED, attempt_count, next_retry_at, claim token và lease 5 phút. Tối đa 6 lần thử, backoff 60 giây tới 30 phút. PROCESSING quá lease được reclaim; token cũ không thể ghi đè kết quả mới. Quá 6 lần phải được hỗ trợ vận hành xử lý; không tự mở phòng vì PDF lỗi. Sau deadline, timeout đóng hold an toàn.

Upload intent được lưu trong `rental_pdf_assets` trước HTTP. Reconciliation kiểm tra file không được agreement tham chiếu, tránh lease còn hoạt động rồi xóa bằng đúng raw/authenticated. Intent được giữ và luân phiên kiểm tra để xử lý upload hoàn tất muộn sau timeout. Cần có chính sách retention/archival khi vận hành lâu dài.

`rental_events` là audit + notification outbox: chỉ actor, recipient, resource ID, transition, timestamp và hash, không chứa CCCD/token/request body. Dispatcher chạy sau giao dịch nghiệp vụ, khóa SKIP LOCKED, ghi Notification hiện hữu và delivered_at trong cùng transaction; không gửi trùng khi retry. ID event là correlation ID của transition. UI đọc trạng thái mới bằng API, không phụ thuộc vào websocket.

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

## Mẫu hợp đồng JasperReports

`src/main/resources/reports/rental-contract.jrxml` là mẫu A4 gồm quốc hiệu, thông tin Bên A/Bên B và bốn mục: nội dung thuê, trách nhiệm Bên A, trách nhiệm Bên B, điều khoản thực hiện. `RentalContractPdfService` compile mẫu một lần, fill từ snapshot bằng `JREmptyDataSource`, rồi xuất bằng `JRPdfExporter`. JasperReports core, PDF và JDT module cùng phiên bản 7.0.8; JDT hỗ trợ compile mẫu bằng classloader khi chạy Spring Boot executable JAR. PDFBox chỉ dùng trong test để đọc/kiểm tra kết quả.

Tiền được trình bày theo định dạng Việt Nam và có bằng chữ cho tiền thuê, tiền cọc; ngày thuê hiển thị dd/MM/yyyy. Số người tối đa lấy từ snapshot của phòng. Snapshot mới lưu thêm CCCD chủ trọ; các snapshot cũ vẫn đọc được. Ngày/nơi cấp CCCD, địa chỉ thường trú chủ trọ và ngày thanh toán chưa được thu thập trong luồng hiện tại nên để dòng chấm, không tự suy đoán. `app.rental.contract-terms` vẫn được chốt vào snapshot và hiển thị trong phần thỏa thuận bổ sung.

Font thường và đậm được đóng gói, nhúng vào PDF qua `jasperreports_extension.properties` và `fonts/rental-fonts.xml`; không cần cài font trên máy chủ. Nội dung snapshot là plain text, không diễn giải HTML hoặc biểu thức Jasper. Trường dài và thỏa thuận bổ sung tự giãn/chuyển trang; có khu vực ký bản giấy và ghi nhận đồng ý trong ứng dụng. PDF đã phát hành vẫn giữ nguyên bytes/hash.

Kiểm tra mẫu độc lập với MySQL/Cloudinary và tạo bản xem thử bằng dữ liệu tổng hợp:

```powershell
.\mvnw.cmd '-Dtest=RentalContractPdfServiceTest' '-Drental.pdf.preview=target/contract-preview' test
```

PDF và ảnh từng trang nằm trong `target/contract-preview/`. Sửa mẫu chỉ ảnh hưởng các lần tạo PDF tiếp theo, không thay file của hợp đồng đã phát hành.

**Giới hạn cần xử lý trước production:** repo chưa có framework mã hóa trường dữ liệu cá nhân. CCCD/địa chỉ và contract_snapshot hiện lưu plaintext trong MySQL; response list không chứa chúng, detail chỉ trả CCCD đã mask, PDF giới hạn đúng party/admin. Cần cấu hình mã hóa storage/backup, quản lý khóa, phân quyền DB và retention. Mẫu điều khoản mặc định được đánh dấu tham khảo; phải thay bằng template đã duyệt. Xác nhận UI là đồng ý điện tử trong ứng dụng; không mô tả là chữ ký số chứng thực.

## API

Các response dùng ApiResponse/PageResponse hiện có; lỗi bổ sung `errorCode` mà vẫn giữ code/message.

- Public `GET /api/listings/{id}/bookable-rooms`: RoomResponse kèm bookingAvailability.
- TENANT `POST /api/rental-requests`, `GET /api/rental-requests/my`, `POST /api/rental-requests/{id}/cancel`.
- LANDLORD `GET /api/landlord/rental-requests` (status/propertyId/roomId/page/size), `POST .../{id}/accept`, `POST .../{id}/reject` (reason optional).
- Party/admin `GET /api/rental-requests/{id}`, `GET /api/rental-agreements/{id}`, `GET /api/rental-agreements/{id}/pdf?download=true`.
- TENANT `GET /api/rental-agreements/my`, `POST /api/rental-agreements/{id}/confirm`, `POST .../{id}/decline`.
- LANDLORD `GET /api/landlord/rental-agreements`, `POST .../{id}/checkout` với handoverConfirmed=true.

```json
{
  "listingId": "listing-uuid",
  "roomId": "room-uuid",
  "tenantFullName": "Tên người thuê",
  "tenantPhone": "0912345678",
  "tenantEmail": "tenant@example.test",
  "tenantCitizenId": "012345678901",
  "tenantPermanentAddress": "Địa chỉ thường trú",
  "proposedMoveInDate": "2026-11-15",
  "durationMonths": 12,
  "occupantCount": 2,
  "message": "Lời nhắn",
  "acceptedPrivacyNotice": true
}
```

Giá, landlord, endDate và trạng thái không nhận từ client. Accept trả RentalAgreementResponse với id/requestId/status/pdfStatus/confirmationExpiresAt; FE dùng id để mở `/hop-dong/{id}`. Confirm gửi `{ "accepted": true, "expectedPdfSha256": "hash-64-hex-cua-PDF-da-tai" }`.

RentalAgreementResponse có thêm `tenantId`, `landlordId` bên cạnh `tenantName`, `landlordName` trong mọi response tạo/xem/danh sách/xác nhận/checkout. Hai ID lấy từ quan hệ hợp đồng, không suy từ tên hiển thị hoặc chủ sở hữu hiện tại của Property. FE dùng tenantId để mở `GET /api/tenants/{tenantId}/reviews`, landlordId để tạo report LANDLORD qua `POST /api/reports`; quyền xem/báo cáo vẫn được backend kiểm tra. Chủ trọ xem review nhận được qua `GET /api/landlord/reviews?type=ROOM|LANDLORD`, xem [contract Review/Report](api.md#16-review).

FE: `/tro/:id` → form; `/yeu-cau-thue` của tenant; `/quan-ly-dat-phong` của landlord (tab yêu cầu/hợp đồng); `/hop-dong/:id` xem PDF, xác nhận/từ chối/bàn giao. Menu tài khoản có liên kết tới các trang. Khi giữ/thuê, dropdown sửa trạng thái phòng bị khóa, BE vẫn kiểm tra invariant.

Cập nhật 09/11/2026: landlord đang hoạt động có thể dùng tenantId để xem danh sách và tổng hợp review về người thuê ngay từ hợp đồng nháp/chờ xác nhận, hoặc trước khi hai bên có hợp đồng. Các API `/api/tenants/{tenantId}/reviews` và `/summary` kiểm tra quyền theo vai trò, không yêu cầu quan hệ hợp đồng. Review bị ẩn không xuất hiện trong kết quả này.

## Kiểm thử

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

Fullstack thực: khởi động BE trên 18080 với DB_NAME trỏ vào **database tạm riêng**, migrate V1–V3, giữ worker bật. FE build production rồi chạy `node scripts/rental-e2e-preview.cjs`. Đặt PLAYWRIGHT_MODULE_PATH nếu Playwright không nằm trong node_modules, RENTAL_E2E_ISOLATED=true, chạy `node scripts/rental-booking.e2e.cjs`. Script dùng các tài khoản demo V2, tạo tin trong DB tạm, đặt qua UI, duyệt qua UI, tải Cloudinary PDF, xác nhận, kiểm tra RENTED/IDOR/redirect/mobile; ảnh lưu ở `docs/screenshots/rental_*.png`. Sau test cần dừng BE tạm, xóa các raw/authenticated PDF của chính database tạm và drop schema tạm. Không chạy script lên DB ứng dụng hoặc production.

## Nhóm file thay đổi

- BE: rental controller/DTO/repository/service/entity; RoomRepository/RoomService/RoomResponse; enrich availability trong PropertyService và ListingService; SecurityConfig/ApiResponse/error handler; V3, font + OFL; rental tests và wiring availability cho test context cũ.
- FE: models/service rental; booking dialog thay luồng chat; listing detail chọn phòng/role; rental list/contract/confirmation modal; routing/header menu; landlord room status UI; fixtures có bookingAvailability và test API/form/access/PDF state; scripts fullstack. Các test chat cũ được chuẩn hóa chữ thường ở package để Surefire chạy được trên Windows.

## Kết quả kiểm tra local (29/10/2026)

- Migration V1–V3 và Hibernate validate chạy thành công trên MySQL 8.0.46. V3 đã được áp dụng vào database local sau khi kiểm tra và repair duy nhất mục V3 thất bại do devtools tự chạy bản migration đang phát triển; V1/V2 không đổi.
- 15 test rental trên MySQL/unit, 99 test Angular và production build. Suite BE mặc định chạy riêng trên schema tạm, các integration suite lịch sử không có biến môi trường được skip theo điều kiện sẵn có.
- Cloudinary live test đã upload/download raw authenticated, đối chiếu bytes, kiểm tra URL unsigned bị chặn, rồi xóa tệp.
- Fullstack Playwright với JWT/BE/MySQL/Cloudinary thật trên DB riêng đã qua create → landlord accept → PDF preview/hash → tenant confirm → RENTED; IDOR 403, anonymous login redirect, mobile 390px không tràn ngang. Ảnh ở FE docs/screenshots. Các tài khoản và thông tin giấy tờ trong ảnh hoàn toàn là dữ liệu tổng hợp phục vụ kiểm thử.
- Sau kiểm thử đã dừng server tạm, xóa PDF riêng tư của lượt chạy và drop database tạm. Không đưa booking thử vào DB ứng dụng.
