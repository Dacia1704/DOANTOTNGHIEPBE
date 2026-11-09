# Kế hoạch xây dựng Review và Report

Ngày khảo sát và chốt nghiệp vụ: 03/11/2026. Trạng thái: đã triển khai nghiệp vụ và REST API backend theo năm quyết định đã chốt; kiểm chứng và bàn giao được ghi tại mục 11. Giao diện Angular chưa triển khai.

Thư mục làm việc trực tiếp: `D:/DOANTOTNGHIEPBE` (local checkout, nhánh `feature/ggmap`). Các thay đổi tiếp theo của công việc này thực hiện tại local checkout, không thực hiện trong worktree của phiên chat. Frontend nằm tại `D:/DOANTOTNGHIEPFE`.

## 1. Mục tiêu và phạm vi

Review giúp người thuê đánh giá phòng/chủ trọ và chủ trọ đánh giá người thuê dựa trên hợp đồng. Report tiếp nhận báo cáo vi phạm tin đăng/chủ trọ, có bằng chứng và kết quả xử lý của admin. Cách hiểu Report này bám theo `docs/requirements/CSDL.txt` và entity hiện tại.

MVP gồm tạo/xem đánh giá, điểm tổng hợp, phản hồi của chủ trọ, admin ẩn/hiện đánh giá; gửi/theo dõi báo cáo, tải bằng chứng và admin xử lý. Thống kê vận hành báo cáo chỉ cần số lượng theo trạng thái. Báo cáo doanh thu, xuất Excel/PDF, tố cáo review, chat hoặc tenant nằm ngoài phạm vi MVP.

Người dùng đã xác nhận: đánh giá trong thời gian thuê và sau trả phòng; review không thể sửa hoặc xóa; landlord đang hoạt động được xem toàn bộ review đang hiển thị về tenant mà không cần hợp đồng giữa hai bên (cập nhật 09/11/2026); chỉ báo cáo landlord đã có hợp đồng; xử lý report theo phạm vi mặc định, chưa thêm xử phạt tự động. Chi tiết và tiêu chí kiểm chứng được đồng bộ bên dưới.

## 2. Hiện trạng đã đối chiếu

- Backend Java 21, Spring Boot, MySQL 8; Flyway quản lý schema, Hibernate `ddl-auto=validate`. Checkout và xác nhận hợp đồng đã có trong `RentalService`.
- Module `review` mới có `RoomReview`, `LandlordReview`, `TenantReview`; mỗi bảng đã UNIQUE theo `rental_agreement_id`, có rating 1–5, `is_hidden`, `hidden_by`. Hai loại đầu có `landlord_reply`.
- Module `report` mới có `Report`, `ReportEvidence`, enum `PENDING/PROCESSING/RESOLVED/REJECTED`. Chưa có repository, DTO, service, controller hay test riêng cho hai module.
- `ErrorCode` đã có nhóm REVIEW và REPORT; cần tận dụng và bổ sung lỗi còn thiếu.
- `Listing` gắn với `Property`; Room thuộc Property. Review phòng vẫn gắn Room, không chuyển sang Listing hoặc Property.
- Dùng lại JWT với principal là user ID, `@PreAuthorize`, `ApiResponse`, `PageResponse`, DTO record và MapStruct như các module đang có.
- `StorageFolder.forReport()` và `ImageValidator` đã có. `ImageStorageService` hiện lưu ảnh công khai; không đáp ứng việc bảo vệ bằng chứng chỉ bằng cách giới hạn API đọc report.
- Module notification mới có entity. `RentalEvents` là outbox riêng của rental, không phải dịch vụ thông báo dùng chung sẵn có.
- Checkout đổi `ACTIVE` sang `EXPIRED` hoặc `TERMINATED`; xác nhận hợp đồng có thể diễn ra trước ngày bắt đầu thuê. Chỉ kiểm tra `ACTIVE` sẽ cho đánh giá quá sớm.
- Đã xác minh frontend `D:/DOANTOTNGHIEPFE` tồn tại bên cạnh local checkout backend; các hạng mục giao diện bên dưới là kế hoạch tích hợp, chưa khảo sát code Angular.
- Thư mục migration hiện có V1 và V2. Một số tài liệu nhắc V3/V14 cũ; lấy file migration thực tế làm căn cứ khi chọn phiên bản tiếp theo.

Nguồn chính: [yêu cầu CSDL](requirements/CSDL.txt), [schema hiện tại](../src/main/resources/db/migration/V1__fullsql.sql), [Review entity](../src/main/java/com/tamdaptit/rentalmanagement/review/entity/RoomReview.java), [Report entity](../src/main/java/com/tamdaptit/rentalmanagement/report/entity/Report.java), [luồng rental](../src/main/java/com/tamdaptit/rentalmanagement/rental/service/RentalService.java), [phân quyền](../src/main/java/com/tamdaptit/rentalmanagement/common/config/SecurityConfig.java), [quy tắc dự án](RULE.md).

## 3. Quy tắc Review

### 3.1. Điều kiện tạo và quyền sở hữu

- Tenant của hợp đồng tạo đánh giá phòng và chủ trọ; landlord của hợp đồng tạo đánh giá tenant. Kiểm tra cả role và quan hệ thực tế, không chỉ role.
- Hợp đồng phải có xác nhận của hai bên, `startDate <= ngày hiện tại` và trạng thái thuộc `ACTIVE`, `EXPIRED`, `TERMINATED`. Không cho `DRAFT`, `PENDING_CONFIRMATION`, `CANCELLED`.
- Cho đánh giá trong suốt thời gian thuê từ ngày bắt đầu và cả sau trả phòng; không đặt thời hạn hết quyền đánh giá sau checkout. Đây là xác thực quan hệ thuê theo hợp đồng; hệ thống hiện chưa có mốc check-in để chứng minh thời gian lưu trú thực tế.
- Mỗi hợp đồng được tối đa một review cho từng loại, tức tối đa ba review độc lập. Review bị ẩn vẫn chiếm lượt đã dùng.
- Request chỉ nhận `rentalAgreementId`, `rating`, `comment`; lấy tenant, landlord và room từ hợp đồng. Không nhận ID người viết/đối tượng do client tự khai báo.
- Phản hồi RoomReview xác thực landlord từ **hợp đồng**, tránh cấp quyền cho chủ sở hữu mới của Property. Chặn tự đánh giá ngay cả khi tài khoản có nhiều role.
- Rating bắt buộc là số nguyên 1–5. Comment tùy chọn, tối đa 2.000 ký tự; trim và lưu plain text. Review đã gửi không thể sửa hoặc xóa; admin chỉ ẩn/hiện, không sửa nội dung thay tác giả.

### 3.2. Hiển thị và tính điểm

- Review phòng/chủ trọ được đọc công khai khi đối tượng được phép xuất hiện trên giao diện công khai. API không trở thành cách đọc thông tin phòng của Property không được công khai.
- Do chưa có trang Room công khai, ưu tiên đọc review Room qua ngữ cảnh tin đăng: kiểm tra Listing còn công khai/hợp lệ và Room thuộc Property của Listing; không phụ thuộc `Room.rentalStatus=AVAILABLE` để đọc lịch sử review.
- Điểm Property trên trang Listing là tổng hợp review các Room thuộc Property; điểm landlord chỉ lấy `LandlordReview`. Không trộn hai nhóm điểm hoặc lấy trung bình của các trung bình Room.
- Tổng hợp trả `totalReviews`, `averageRating`, phân bố 1–5 sao. Không có review thì `averageRating=null`, tổng bằng 0. Query chỉ tính `is_hidden=false`, làm tròn điểm cuối cùng đến một chữ số thập phân.
- DTO công khai chỉ có nội dung review, tên hiển thị phù hợp, avatar nếu có, điểm và thời gian. Không lộ hợp đồng, email, số điện thoại, giấy tờ, ghi chú moderation hoặc entity User đầy đủ.
- Đánh giá tenant không công khai. Chính tenant, admin và landlord đang hoạt động được đọc toàn bộ review đang hiển thị về tenant, bao gồm review do landlord khác viết. Landlord không cần có hợp đồng với tenant; có thể xem trước khi lập hợp đồng hoặc ngay khi xem hợp đồng nháp/chờ xác nhận. Quyền đọc toàn bộ không bỏ qua quy tắc review bị ẩn bên dưới.
- Tác giả có thể xem review bị ẩn của mình kèm trạng thái/lý do. Chỉ admin đọc toàn bộ review ẩn. Các bên khác không đọc được qua URL chi tiết hoặc tham số `includeHidden`.
- Phân trang mặc định 10, tối đa 50; sắp xếp `createdAt DESC, id DESC`, chỉ cho các kiểu sort đã định nghĩa. MVP tính điểm trực tiếp bằng aggregate query, chưa cache.

### 3.3. Phản hồi và moderation

- Landlord của hợp đồng được tạo/cập nhật một phản hồi cho review phòng/chủ trọ đang hiển thị; tối đa 2.000 ký tự, plain text. TenantReview chưa có phản hồi trong MVP.
- Admin ẩn/hiện review; khi ẩn bắt buộc lý do tối đa 500 ký tự. Landlord không tự ẩn đánh giá nhận được.
- Lưu `hidden_by`, bổ sung `hidden_at`, `hidden_reason`. Khi hiện lại thì xóa bộ metadata ẩn hiện hành; MVP chưa lưu đầy đủ lịch sử mọi lần moderation.
- Ẩn/hiện tác động ngay tới danh sách và điểm tổng hợp. Dùng khóa bản ghi khi cập nhật moderation/phản hồi để tránh kiểm tra trạng thái trên dữ liệu cũ.

## 4. Quy tắc Report

### 4.1. Gửi và theo dõi

- Chỉ tenant đang hoạt động tạo report. Lấy người gửi từ principal; client không đặt `status`, `handledBy`, `resolvedAt`.
- Request có `targetType=LISTING|LANDLORD`, `targetId`, `reasonCode`, `description`. Một request chỉ có một đối tượng chính.
- Với LISTING: lưu `listing_id`, suy ra `landlord_id` qua `listing.property.landlord`; không tin landlord ID client gửi. Cho báo cáo tin đang công khai, còn hiệu lực mà tenant nhìn thấy; không yêu cầu đã thuê.
- Với LANDLORD: lưu `landlord_id`, để `listing_id=null`; tenant phải có hợp đồng đã được hai bên xác nhận với landlord đó, trạng thái `ACTIVE`, `EXPIRED` hoặc `TERMINATED`. Cho báo cáo khi đang thuê hoặc sau trả phòng. Hợp đồng nháp, chờ xác nhận, đã hủy và tương tác chỉ qua chat không đủ điều kiện.
- Từ chối target không tồn tại, không đủ quyền truy cập và tự báo cáo. Report đã tạo vẫn được tenant/admin đọc khi tin bị ẩn/hết hạn.
- Danh mục lý do đầu tiên: `SCAM`, `MISLEADING_INFORMATION`, `INVALID_PRICE`, `UNSAFE_CONDITION`, `INAPPROPRIATE_CONTENT`, `OTHER`; cấu hình danh mục hợp lệ theo loại target. `OTHER` bắt buộc mô tả; giới hạn mô tả 5.000 ký tự.
- Mỗi tenant chỉ có một report đang mở (`PENDING/PROCESSING`) cho cùng đối tượng chính, bất kể mã lý do. Khi đã đóng có thể tạo report mới cho vụ việc mới.
- Để chống gửi trùng đồng thời: khóa hàng user của tenant trước khi kiểm tra/tạo report trong cùng transaction; mọi đường tạo report phải đi qua bước này. Report LISTING và LANDLORD là hai target khác nhau dù cùng landlord.
- Người gửi xem danh sách/chi tiết của mình; admin xem tất cả. Landlord bị báo cáo chưa có quyền đọc danh tính người báo cáo hoặc bằng chứng trong MVP.
- Không sửa target, lý do hoặc mô tả sau khi gửi; không xóa report. Tenant chỉ bổ sung bằng chứng khi còn `PENDING`.

### 4.2. Bằng chứng

- Tạo report JSON trước, sau đó upload từng ảnh qua multipart. Report không bắt buộc có ảnh; nếu upload lỗi, người dùng vẫn thấy report và có thể thử lại.
- Tối đa 5 ảnh/report. Dùng lại `ImageValidator`: JPEG/PNG, tối đa 5 MiB/ảnh, mỗi chiều tối đa 4.096 px. Upload từng ảnh tránh xung đột giới hạn tổng request giữa cấu hình local và Docker; kiểm tra cả phần multipart overhead.
- Thêm adapter `ReportEvidenceStorage` lưu ảnh Cloudinary dạng authenticated, đọc qua backend sau kiểm tra quyền. Có thể tham khảo cách bảo vệ tài liệu trong `CloudinaryRentalPdfStorage`; không dùng URL ảnh public của `ImageStorageService` làm bằng chứng riêng tư.
- Dùng `StorageFolder.forReport(reportId)` và lưu `image_public_id`. `image_url` hiện có có thể lưu đường dẫn đọc qua backend; DTO không trả URL gốc công khai hay thông tin ký truy cập lâu dài.
- Quy trình upload: xác thực sơ bộ → validate/upload authenticated → transaction ngắn khóa report, kiểm tra lại chủ sở hữu/trạng thái/số lượng → lưu evidence. Nếu transaction hoặc kiểm tra sau upload thất bại thì xóa đúng asset vừa tạo; ghi nhận public ID cần dọn lại nếu xóa thất bại.
- MVP không có API xóa bằng chứng; tránh thay đổi hồ sơ trong lúc admin xử lý. Upload và admin nhận xử lý cùng khóa report, nên không thể thêm bằng chứng sau khi đã chuyển `PROCESSING`.

### 4.3. Xử lý của admin

Luồng mặc định: `PENDING → PROCESSING → RESOLVED | REJECTED`.

- Admin nhận xử lý: khóa report và chuyển `PENDING → PROCESSING`, lưu `handled_by` từ principal.
- Admin đang được giao xử lý mới được đóng report. `resolutionNote` bắt buộc, tối đa 5.000 ký tự; lưu `resolved_at` khi sang `RESOLVED/REJECTED`.
- Không đóng thẳng từ `PENDING`, không mở lại report đã đóng trong MVP. Chưa có giao lại người xử lý; phải nêu giới hạn này trong hướng dẫn quản trị.
- Dùng pessimistic lock hoặc conditional update để hai admin nhận/đóng cùng lúc chỉ có một thao tác thắng. Lỗi chuyển trạng thái trả mã nghiệp vụ rõ ràng; không dùng `REPORT_NOT_PENDING` cho mọi trường hợp.
- `RESOLVED` nghĩa là đã ghi nhận kết quả, không tự động khóa user hoặc ẩn Listing. Listing hiện chưa có thao tác admin ẩn tin; xử phạt cần hạng mục riêng nếu muốn hỗ trợ ngay trong giao diện.
- Tạo notification loại `REPORT` cho tenant khi nhận xử lý và khi đóng, cùng transaction database với thay đổi trạng thái. Chỉ thêm repository/helper nhỏ cần thiết; không mở rộng `RentalEvents` sang nghiệp vụ report. API xem thông báo và realtime để giai đoạn sau.

## 5. Hợp đồng API dự kiến

Đây là route đề xuất, chưa phải API đã có. Dùng envelope `ApiResponse` và `PageResponse` hiện tại. Chuẩn hóa lỗi 400 validation/nghiệp vụ theo quy ước repo, 401 chưa đăng nhập, 403 sai quyền, 404 không tồn tại hoặc tài nguyên không được công khai. Mọi endpoint có ID đều kiểm tra quan hệ tại service.

### 5.1. Review

- `GET /api/rental-agreements/{id}/review-eligibility`: bên hợp đồng; trả quyền tạo từng loại, review ID đã có và mã lý do nếu không được tạo.
- `POST /api/reviews/rooms`: tenant tạo RoomReview.
- `POST /api/reviews/landlords`: tenant tạo LandlordReview.
- `POST /api/reviews/tenants`: landlord tạo TenantReview.
- `GET /api/reviews/mine?type=ROOM|LANDLORD|TENANT&page=0&size=10`: review do người đăng nhập viết, bao gồm trạng thái ẩn.
- `GET /api/listings/{listingId}/rooms/{roomId}/reviews` và `/summary`: công khai sau kiểm tra Listing/Room.
- `GET /api/listings/{listingId}/reviews` và `/reviews/summary`: review phòng gộp theo Property của tin; mỗi review chỉ được tính một lần.
- `GET /api/landlords/{landlordId}/reviews` và `/reviews/summary`: review chủ trọ công khai nếu landlord có tin đang công khai.
- `GET /api/tenants/{tenantId}/reviews` và `/reviews/summary`: kiểm tra quyền đọc riêng tư đã mô tả ở mục 3.
- `PUT /api/reviews/rooms/{id}/reply`, `PUT /api/reviews/landlords/{id}/reply`: landlord của hợp đồng.
- `GET /api/admin/reviews?type=...&hidden=...&page=0&size=10`: admin tìm/lọc review, có thể lọc thêm theo đối tượng.
- `PATCH /api/admin/reviews/{type}/{id}/visibility`: body `{hidden, reason}`, admin ẩn/hiện; type là enum allowlist.

Chỉ mở public GET cho các route review công khai cụ thể; giữ `/mine`, eligibility, tenant review và `/admin/**` yêu cầu xác thực. Kiểm tra thứ tự matcher trong `SecurityConfig`.

### 5.2. Report

- `GET /api/reports/reasons?targetType=LISTING|LANDLORD`: tenant đọc danh mục lý do.
- `POST /api/reports`: tenant tạo report theo payload ở mục 4.1.
- `GET /api/reports/mine?status=...&page=0&size=10`: tenant xem của mình.
- `GET /api/reports/{id}`: người gửi hoặc admin xem chi tiết/kết quả.
- `POST /api/reports/{id}/evidences`: người gửi upload một ảnh, chỉ `PENDING`.
- `GET /api/reports/{id}/evidences/{evidenceId}/content`: người gửi hoặc admin đọc ảnh; kiểm tra evidence thực sự thuộc report, response private/no-store.
- `GET /api/admin/reports?status=...&reasonCode=...&page=0&size=10`: danh sách quản trị.
- `GET /api/admin/reports/summary`: số report theo bốn trạng thái, phục vụ bộ lọc quản trị.
- `POST /api/admin/reports/{id}/assign`: admin tự nhận xử lý.
- `POST /api/admin/reports/{id}/resolve`, `POST /api/admin/reports/{id}/reject`: admin được giao xử lý đóng report, body `{resolutionNote}`.

Danh sách report chỉ trả tóm tắt; chi tiết tải bằng chứng theo endpoint riêng. Không nhận `imageUrl` hoặc `imagePublicId` từ client để tránh gắn/xóa asset của người khác.

## 6. Thiết kế code và database

- Review: thêm ba repository theo entity, DTO request/response cần thiết, mapper, `ReviewService` + implementation; controller có thể chia public/người dùng và admin. Dùng một enum ReviewType giới hạn loại review, không dùng reflection hoặc tên bảng do client cung cấp.
- Report: thêm `ReportRepository`, `ReportEvidenceRepository`, DTO, mapper, `ReportService` + implementation, controller người dùng/admin và `ReportEvidenceStorage`.
- Service dùng transaction cho mutation, `readOnly=true` cho truy vấn; chỉ fetch quan hệ cần cho DTO để tránh N+1. Không trả JPA entity trực tiếp.
- Giữ UNIQUE `rental_agreement_id` và CHECK rating đã có. Kiểm tra trước tạo để báo lỗi dễ hiểu, nhưng database vẫn là chốt chống review trùng đồng thời; map vi phạm đúng constraint về lỗi đã có, không bắt mọi lỗi integrity thành trùng review.
- Sửa giá trị mặc định ở ba review entity và Report: dùng `@Builder.Default` hoặc set tường minh `hidden=false`, `status=PENDING`; hiện initializer không bảo đảm giá trị khi dùng Lombok builder.
- Thêm migration mới với `hidden_at`, `hidden_reason` cho ba bảng review; chuẩn hóa `is_hidden=NULL` thành false và chuyển NOT NULL nếu dữ liệu hiện có cho phép.
- Thêm CHECK cho report phải có ít nhất một target (`listing_id IS NOT NULL OR landlord_id IS NOT NULL`); quan hệ landlord của listing được kiểm tra tại service. Audit và xử lý dữ liệu cũ không hợp lệ trước khi siết constraint.
- Chỉ mục đề xuất: từng bảng review `(target_id, is_hidden, created_at, id)`; report `(status, created_at, id)`, `(tenant_id, created_at, id)`, `(tenant_id, listing_id, status)`, `(tenant_id, landlord_id, status)`. `target_id` là cột thực tương ứng `room_id`, `landlord_id`, `tenant_id`, không phải cột mới. Đối chiếu index sẵn có/EXPLAIN trước khi thêm trùng lặp.
- Không tạo bảng review tổng quát, bảng rating summary hoặc enum trạng thái mới khi chưa cần. Không sửa V1/V2 đã áp dụng; dùng số migration kế tiếp còn trống lúc triển khai.
- Kiểm tra đường xóa Room/Property/Listing để không cascade mất review/report. `UserDeletionGuard` hiện đã chặn xóa user còn tham chiếu; không mở lại việc xóa lịch sử qua module mới.
- Phạm vi lưu vết MVP gồm trạng thái hiện hành, người xử lý, thời gian và ghi chú. Nhật ký đầy đủ các lần chuyển trạng thái, snapshot nội dung tin bị báo cáo và phân công lại là mở rộng sau MVP.

## 7. Giao diện cần tích hợp

- Tenant: mục đánh giá trong chi tiết hợp đồng; hiển thị điều kiện chưa đạt/đã đánh giá từ eligibility API, tránh tự suy luận ở Angular. Có danh sách review đã viết và trạng thái bị ẩn.
- Trang Listing: phân bố sao và review phòng theo Property, bộ lọc từng Room, điểm landlord riêng. Nút báo cáo mở form chọn lý do, mô tả, tạo report rồi upload ảnh có trạng thái thử lại.
- Tenant: trang báo cáo của tôi, lọc trạng thái, xem kết quả và ảnh chỉ khi có quyền. Nút báo cáo landlord chỉ hiện khi đủ điều kiện quan hệ hợp đồng.
- Landlord: xem review nhận được, phản hồi review phòng/chủ trọ; đánh giá tenant từ hợp đồng; đọc lịch sử tenant theo quyền đã thống nhất.
- Admin: danh sách review để ẩn/hiện có lý do; hàng đợi report, bộ lọc trạng thái/lý do, chi tiết bằng chứng, nhận xử lý và đóng hồ sơ.
- Hiển thị plain text an toàn, phân trang và trạng thái tải/lỗi; không đưa URL công khai của bằng chứng vào HTML. Khi hết quyền hoặc nhận 403 phải ngừng hiển thị tài nguyên đã tải.

## 8. Thứ tự triển khai và ước lượng

Ước lượng sơ bộ cho một lập trình viên đã quen dự án; là ngày công, không phải cam kết lịch. Backend khoảng 8–11 ngày, tích hợp Angular thêm khoảng 3–5 ngày, cần hiệu chỉnh sau khi khảo sát frontend.

1. **Hoàn thiện contract từ nghiệp vụ đã chốt — 0,5–1 ngày.** Năm quyết định nghiệp vụ đã được xác nhận ở mục 10. Hoàn thiện DTO, mã lý do, ví dụ response và lỗi trước khi frontend tích hợp; không cần hỏi lại các quyết định đã chốt.
2. **Nền dữ liệu và Review — 2–3 ngày.** Migration, defaults, repositories, tạo ba loại review, eligibility, danh sách và aggregate, phản hồi/moderation. Hoàn thành test phân quyền và chống tạo trùng trước khi sang report.
3. **Report và bằng chứng — 2–3 ngày.** Tạo/list/detail, chống trùng, authenticated storage, upload có bù lỗi, kiểm tra quyền đọc ảnh. Đầu ra là tenant có thể gửi và theo dõi hồ sơ hoàn chỉnh.
4. **Quản trị report — 1–1,5 ngày.** Nhận/đóng report có khóa, summary và notification lưu trong DB. Hoàn thành test hai admin xử lý đồng thời.
5. **Hồi quy và tài liệu backend — 2–2,5 ngày.** Migration MySQL thật, kiểm thử API với security thật, failure cases storage, cập nhật `docs/api.md`, seed/test fixtures riêng và hướng dẫn demo.
6. **Frontend và nghiệm thu — thêm 3–5 ngày.** Triển khai các màn hình ở mục 7 sau khi kiểm tra cấu trúc Angular; xác minh luồng tenant → landlord → admin trên môi trường thử nghiệm.

Nếu cần chia nhỏ bản phát hành: Review trước; Report kèm bằng chứng và admin xử lý sau. Mỗi bản phải có phân quyền, tài liệu và test tương ứng.

## 9. Kiểm chứng và tiêu chí nghiệm thu

### Review

- Đúng người, đúng role, hợp đồng đủ điều kiện tạo được từng loại; hợp đồng chưa bắt đầu, thiếu xác nhận, đã hủy hoặc tài khoản ngoài hợp đồng đều bị chặn.
- Hai request đồng thời cho cùng hợp đồng/loại chỉ tạo một bản ghi; loại review khác vẫn độc lập. Review bị ẩn không cho tạo lại.
- Hợp đồng đang thuê và đã checkout đều cho tạo review nếu chưa dùng lượt; không áp giới hạn số ngày sau trả phòng. Không có API sửa/xóa review; thử PUT/PATCH/DELETE để thay nội dung hoặc xóa không được làm thay đổi dữ liệu.
- Rating thập phân/ngoài 1–5, payload thừa quyền, comment/reply quá dài bị từ chối theo contract đã chốt.
- Chỉ landlord trên hợp đồng được phản hồi; tài khoản landlord khác và chủ Property mới không lấy được quyền qua thay đổi sở hữu.
- Admin ẩn/hiện cập nhật đúng metadata. Review bị ẩn biến mất khỏi mọi API công khai và aggregate, kể cả filter/phân trang; tác giả/admin vẫn xem được đúng phạm vi.
- Review tổng hợp Property tính theo số review thực, không trung bình các trung bình; không có dữ liệu trả average null. TenantReview và ID hợp đồng không rò qua API công khai.
- Landlord đang hoạt động xem được cả review do landlord khác viết khi chưa có hợp đồng, hợp đồng đang nháp/chờ xác nhận hoặc đã trả phòng. Tenant khác và người chưa đăng nhập bị chặn. Review bị ẩn vẫn chỉ tác giả/admin được xem theo quyền riêng.

### Report và storage

- Không tạo được report không target, target giả/không được xem, tự báo cáo, mã lý do sai hoặc thiếu mô tả OTHER. Landlord của report Listing luôn do backend suy ra.
- Báo cáo LANDLORD thành công với hợp đồng đã xác nhận đang thuê/đã trả phòng; bị chặn nếu chỉ có chat hoặc hợp đồng nháp/chờ xác nhận/đã hủy. RESOLVED không tự động ẩn tin hoặc khóa tài khoản.
- Gửi trùng đồng thời chỉ có một report mở cho cùng tenant/target; có thể báo cáo hai Listing khác nhau của cùng landlord và tạo vụ việc mới khi report cũ đã đóng.
- Report/ảnh chỉ người gửi và admin đọc được; đổi report ID hoặc evidence ID của người khác bị chặn. Không truy cập được asset authenticated bằng URL công khai.
- File giả MIME, quá 5 MiB, kích thước lớn, ảnh thứ sáu và upload sau `PROCESSING` bị từ chối; hai upload đồng thời không vượt giới hạn.
- Upload lỗi giữa chừng hoặc commit thất bại không sinh evidence hỏng; asset vừa upload được dọn/báo lại để dọn, không xóa nhầm asset cũ.
- Hai admin cùng nhận chỉ một người thắng; người khác không đóng được. Không đi tắt trạng thái, không sửa report đã đóng; người xử lý/thời gian/note/notification khớp transaction.

### Hồi quy và bàn giao

- Test service cho quy tắc; MockMvc với security cho 401/403/IDOR; integration trên MySQL 8 riêng cho UNIQUE, CHECK, aggregate, lock và migration. Không dùng DB ứng dụng để chạy bài test tạo/xóa schema.
- Dùng fake storage để kiểm tra failure/cleanup; thêm smoke test authenticated Cloudinary bằng tài khoản test khi có cấu hình. Không coi mock storage là bằng chứng đã kiểm chứng quyền tải file thật.
- Chạy các test rental/property/schema liên quan để bảo đảm luồng đặt phòng, checkout và bảo vệ lịch sử vẫn đúng. Ghi rõ test tích hợp bị skip do thiếu cấu hình.
- Cập nhật `docs/api.md` sau khi API thực sự được viết, kèm request/response, phân quyền, giới hạn ảnh, trạng thái và mã lỗi. Không ghi API đề xuất thành API hiện hữu.
- Demo tối thiểu: hợp đồng đủ điều kiện → tenant đánh giá phòng/chủ trọ → landlord phản hồi/đánh giá tenant → admin ẩn review → điểm thay đổi → tenant báo cáo kèm ảnh → admin nhận và đóng → tenant xem kết quả.

## 10. Năm quyết định đã được người dùng xác nhận

1. **Thời điểm đánh giá:** trong suốt thời gian thuê và cả sau khi trả phòng; mỗi loại vẫn tối đa một review cho mỗi hợp đồng.
2. **Tính bất biến:** review không thể sửa hoặc xóa sau khi gửi. Phản hồi của landlord và thao tác admin ẩn/hiện là các thao tác riêng theo mục 3.3.
3. **Quyền xem tenant review (cập nhật 09/11/2026):** landlord đang hoạt động được xem toàn bộ review đang hiển thị về tenant đó, kể cả review do landlord khác viết, không cần hợp đồng giữa hai bên. Không mở công khai và không bỏ qua quy tắc ẩn review.
4. **Điều kiện báo cáo landlord:** chỉ tenant đã có hợp đồng được xác nhận với landlord đó; không mở rộng sang quan hệ chỉ qua chat.
5. **Xử lý report:** trước mắt theo mặc định: nhận xử lý và ghi kết quả, chưa có tự động ẩn tin/khóa tài khoản, chưa thêm lịch sử xử lý đầy đủ hoặc giao lại người xử lý.

Không còn câu hỏi nghiệp vụ nào trong năm mục này cần hỏi lại. Kế hoạch và các bước triển khai tiếp theo dùng local checkout `D:/DOANTOTNGHIEPBE` làm nơi làm việc trực tiếp.

## 11. Bàn giao backend

- Đã thêm controller, DTO, mapper, repository và service cho ba loại Review; tạo theo hợp đồng, eligibility, danh sách, tổng hợp sao, phản hồi và admin ẩn/hiện. Không có API sửa/xóa review.
- Đã thêm nghiệp vụ Report theo target Listing/landlord, danh mục lý do, chống report mở trùng, hồ sơ riêng tư, admin nhận và đóng report, thống kê trạng thái và notification cùng transaction.
- Đã thêm upload/download bằng chứng Cloudinary raw/authenticated. Kiểm tra lại quyền/trạng thái/giới hạn sau upload, dọn asset khi lưu thất bại; không trả URL public cũ qua DTO.
- Đã thêm `V3__review_report_workflow.sql`; đồng bộ entity/defaults, metadata moderation, index, CHECK và FK bảo vệ lịch sử. Room có lịch sử hợp đồng không được xóa.
- Đã cập nhật [hợp đồng API thực tế](api.md#16-review), bao gồm giới hạn, phân quyền, lỗi và hướng dẫn tải ảnh bằng token.
- Test mới: `ReviewReportWorkflowTest` (MySQL + cấu hình Security production + fake storage có mô phỏng lỗi), `ReviewReportMigrationTest` (nâng cấp V2 có dữ liệu cũ), `CloudinaryReportEvidenceStorageTest` (opt-in kiểm tra Cloudinary thật bằng ảnh tổng hợp, tự xóa asset).
- Các kiểm thử database chỉ tạo/xóa schema ngẫu nhiên riêng; không dùng schema ứng dụng. Bài Cloudinary thật đã kiểm tra tải lại đúng bytes và từ chối URL unsigned/public.

Kết quả xác nhận lần triển khai ban đầu ngày 03/11/2026: biên dịch source với release Java 21 thành công; **69 kiểm thử liên quan qua, không failure/error/skip** trên chín suite: ReviewReportWorkflow 24, ReviewReportMigration 1, CloudinaryReportEvidenceStorage 1, PropertyApi 10, RentalWorkflow 12, RentalValidation 3, RentalMigration 1, RentalContractPdfService 13 và SeedDataMigration 4. Đây là các suite trong phạm vi thay đổi và hồi quy liên quan, không phải toàn bộ test suite của repository.

Đợt bổ sung contract cho FE: RentalAgreementResponse có tenantId/landlordId; API `GET /api/landlord/reviews?type=ROOM|LANDLORD` trả review nhận được theo landlord trên hợp đồng, có ngữ cảnh phòng/hợp đồng và canReply, độc lập trạng thái tin. Response quản lý review có targetId/targetName; ReportResponse có targetName/handledByName. Không thay đổi DTO review công khai hoặc quyền đọc review bị ẩn. Đã chạy lại **41 kiểm thử qua, không failure/error/skip**: RentalWorkflow 13 và ReviewReportWorkflow 28, bao gồm tin hết hạn/đổi chủ, phân quyền và nhãn đối tượng. Không cần migration bổ sung cho các trường response này.

Đã sửa fixture dọn dữ liệu Property theo thứ tự FK và chuẩn hóa khoảng trắng trong assertion đọc PDF của rental; không thay đổi nội dung/mẫu PDF. Đã bổ sung ca bảo vệ Room có lịch sử checkout trước khi có review. Các log và báo cáo JUnit của lần kiểm chứng nằm trong `target/review-report-verification/surefire-reports`; file POM tạm dùng tách output khỏi IDE đã được xóa.

Chạy test backend với Java 21 trở lên:

```powershell
$env:SUBSCRIPTION_TEST_MYSQL_URL='jdbc:mysql://localhost:3306/'
$env:SUBSCRIPTION_TEST_MYSQL_USER='root'
# Đặt SUBSCRIPTION_TEST_MYSQL_PASSWORD qua môi trường, không ghi mật khẩu vào tài liệu.
.\mvnw.cmd '-Dtest=ReviewReportWorkflowTest,ReviewReportMigrationTest' test
```

Smoke test Cloudinary bật bằng `REVIEW_REPORT_TEST_CLOUDINARY=true` và ba biến `REVIEW_REPORT_TEST_CLOUD_NAME`, `REVIEW_REPORT_TEST_CLOUD_KEY`, `REVIEW_REPORT_TEST_CLOUD_SECRET`, sau đó chạy `CloudinaryReportEvidenceStorageTest`. Không commit giá trị bí mật. Nếu IDE đang ghi `target/classes`, dùng output build riêng khi chạy Maven để tránh xung đột file class.

Phần tiếp theo là tích hợp giao diện Angular ở mục 7. MVP hiện chưa có mở lại/giao lại report, xử phạt tự động, sửa/xóa review hoặc API đọc thông báo/realtime.
