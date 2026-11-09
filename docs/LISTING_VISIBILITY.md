# Phân biệt Room và bài đăng

> Schema hi?n t?i ?? g?p v?o Flyway V1, seed d?ng V2. C?c t?n migration/s? b?ng v? k?t qu? ki?m ch?ng c? ng?y ? d??i ph?n ?nh l?ch s? tri?n khai; kh?ng ?p d?ng l?i migration c?. C?i ??t theo [README chung](../../AIService/README.md).

## Lượt đăng và ưu tiên (02/11/2026)

Quy tắc này thay thế cách tính suất đồng thời trước đây: tạo tin thành công trừ ngay một lượt đăng; `POST /api/listings` nhận thêm `priorityLevel` tùy chọn (0–2, mặc định 0) để trừ lượt ưu tiên theo gói trong cùng transaction. Nếu không đủ lượt, toàn bộ việc tạo tin được rollback.

Không thêm bảng. Đã đối chiếu toàn bộ bảng/cột/FK và lịch sử Flyway của `rental_db` (46 bảng nghiệp vụ + bảng Flyway). Hạn mức vẫn lấy từ `service_subscription_periods` và `boost_service_package_subscriptions`. `listings.posting_quota_consumed`/`posting_quota_period_id` và `consumed_priority_levels`/`priority_quota_period_id` ghi nhận lượt đã dùng trong kỳ, độc lập với trạng thái hiển thị. `boost_allocations` giữ các lượt đã gán; `active` xác định lựa chọn đang áp dụng. Ẩn tin, hết hạn, bỏ/đổi ưu tiên hoặc Boost không hoàn lượt.

`POST /api/listings` nhận cặp `boostSubscriptionId`/`boostPriorityLevel` (1/2) để dùng gói Boost đã mua ngay trong transaction tạo tin; không đồng thời chọn lượt ưu tiên riêng của gói dịch vụ. Hết lượt, gói chưa trả tiền/hết hạn hoặc không thuộc chủ trọ sẽ rollback cả tin và lượt đăng. Lựa chọn Boost được giữ trên PENDING còn hạn, xuất hiện trong `boostAllocation`; chỉ tính vào hiển thị công khai sau khi duyệt. Gói Boost vẫn giữ thời hạn đã mua, không bắt đầu lại khi duyệt.

Flyway V5 chỉ thêm `listings.initial_review`. Tin mới có cờ true; duyệt, từ chối, ẩn, hết hạn hoặc gửi duyệt lại chuyển thành false và không bật lại. Chỉ admin từ chối PENDING còn hạn, có `initial_review=true` và chưa xuất bản mới hoàn đúng lượt của tin về nguồn gói tương ứng. Không đặt lại tổng hạn mức, không hoàn tiền hoặc ảnh hưởng những tin khác. Từ chối lần hai, kể cả sau khi lần đầu đã được hoàn và gửi lại, không được hoàn. Khi gửi lại, lựa chọn Boost cũ ngừng áp dụng, lượt đã dùng được giữ.

Dữ liệu trước V5 không có lịch sử từng chuyển trạng thái: chỉ PENDING chưa xuất bản với `submitted_at` trùng `created_at` (sai số 1 giây) được nhận diện là lần gửi đầu. Không suy đoán hoàn lượt cho tin có mốc gửi lại khác ngày tạo. Migration được kiểm chứng trên schema riêng; database ứng dụng sẽ nhận V5 khi khởi động lại BE bằng Flyway.

Kiểm chứng luồng Boost khi tạo tin: 87 test BE (80 PackageCatalogApiTest + 7 PropertyApiTest) qua trên MySQL 8.0.46 với schema ngẫu nhiên; có JPA validate, nâng schema V3→V5 không tăng số bảng, rollback khi Boost sai/hết lượt, hoàn đúng số dư ban đầu, không hoàn sau ẩn/hết hạn/gửi lại và tranh lượt cuối đồng thời. FE qua 19 test liên quan và production build; headless Edge kiểm tra form ở 1440px/390px qua Postman simulation, gồm Boost cao/thấp, lỗi 409 giữ nội dung và hiển thị lựa chọn ở tin chờ duyệt. Đây là kiểm tra UI với API giả lập; không phát sinh giao dịch VNPay thật.

Cùng tin, cùng kỳ và cùng mức ưu tiên sử dụng lại lượt đã trừ. Gửi lại tin đã được hoàn lượt sẽ trừ lại. Kỳ gia hạn được mua có hạn mức riêng; nâng gói tức thời chuyển lượt đã dùng sang kỳ thay thế, không cấp lại lượt cũ. Free có 3 lượt, không tự reset theo tháng.

Flyway `V4__consume_existing_listing_and_boost_quota.sql` chỉ bổ sung cột/index trên `listings` và `boost_allocations`, ghi nhận dữ liệu tin/ưu tiên hiện còn lưu. Lịch sử phân bổ từng bị xóa trước migration không thể khôi phục. Tạo cơ sở/phòng vật lý vẫn tách riêng với tạo tin đăng. Chưa áp dụng migration vào database ứng dụng trong phiên này.

Room/Property là tài sản của chủ trọ. Tạo Room không tạo Listing và không làm phòng xuất hiện trên trang công khai hoặc danh sách bài đăng admin.

## Công khai

- `GET /api/listings?page=0&size=12`: chỉ `PUBLISHED` và `expiresAt > now`, giữ thứ tự ưu tiên Boost/gói rồi thời gian đăng mới nhất như hiện tại.
- Bộ lọc tùy chọn: roomTypeName, provinceId, wardId, rentalStatus, minPrice, maxPrice, amenityNames lặp lại. Tiện ích có thể nằm ở Room hoặc Property; phải khớp đủ các tên đã chọn. Lọc trước phân trang; totalElements là tổng tin khớp điều kiện. Giá không âm, min <= max, page >= 0, size 1–100.
- `GET /api/listings/{id}`: trả 404 cho tin chờ duyệt, ẩn, từ chối, hết hạn hoặc ID không tồn tại. Không dùng roomId làm listingId.
- ListingResponse trả `propertyId`, `property` (PropertyResponse chứa rooms), propertyName, rentalMode, landlordName. Listing và Property có ID độc lập. Ảnh/tiện ích được tải theo nhóm và map qua MapStruct, không gọi endpoint quản lý Room từ FE công khai.
- Khách không cần đăng nhập. GET danh sách/filter loại phòng, tiện ích, tỉnh, phường cũng được phép để sử dụng bộ lọc; CRUD và các API tài sản riêng vẫn có kiểm tra quyền.

## Admin

- `GET /api/listings/admin?page=0&size=10&status=PENDING`: ADMIN only; bỏ status để xem toàn bộ bài đăng của mọi chủ trọ. Không có Room chưa tạo bài trong kết quả.
- Status: PENDING, PUBLISHED, REJECTED, HIDDEN, EXPIRED. Bộ lọc EXPIRED bao gồm tin đã quá hạn dù job chưa cập nhật trạng thái DB; PUBLISHED/PENDING chỉ gồm tin còn hạn tương ứng. Response giữ status lưu trong DB; FE hiển thị trạng thái hiệu lực theo thời gian.
- Duyệt/từ chối dùng API cũ, chỉ áp dụng tin PENDING còn hạn. Các quyền/quota/Boost hiện có được giữ nguyên.
- Màn admin quản lý loại phòng chỉ CRUD master data; bỏ phần liệt kê Room. API `/api/room-types/categories` chuyển thành LANDLORD only và chỉ trả phòng của chính tài khoản đó.

## Kiểm chứng

72 test PackageCatalogApiTest/PropertyApiTest trên MySQL 8.0.46 với schema ngẫu nhiên tách riêng đều qua. Test mới kiểm tra phòng chưa tạo tin không xuất hiện, công khai chỉ có PUBLISHED còn hạn, nội dung/ảnh Room đi kèm Listing, bộ lọc trước phân trang, quyền ADMIN, lọc trạng thái hiệu lực và quyền sở hữu danh mục Room. Maven build kiểm chứng dùng output tách khỏi target do IDE đang ghi class vào target với tên package khác hoa/thường.

Migration V13 chuyển liên kết bài đăng sang cơ sở; chưa chạy trên database ứng dụng.

## Bài đăng theo cơ sở (27/10/2026)

Tạo tin nhận `propertyId`, không nhận `roomId`. Chủ trọ chọn cơ sở có ít nhất một phòng/căn AVAILABLE; BE kiểm tra quyền sở hữu, quota và không cho tạo thêm tin PENDING/PUBLISHED còn hiệu lực cho cùng cơ sở.

ListingResponse trả `propertyId` và `property` (PropertyResponse chứa thông tin chung, tiện ích, ảnh và danh sách `rooms`). Mapping dùng MapStruct, dữ liệu ảnh/tiện ích được tải theo nhóm cho trang kết quả. Trang chủ chỉ có một thẻ cho mỗi cơ sở: WHOLE hiển thị căn nhà; BY_ROOM hiển thị cơ sở và cho xem từng phòng trong chi tiết. Điều kiện loại phòng/giá/trạng thái/tiện ích phải khớp trên cùng một phòng.

Migration mới `V13__listing_belongs_to_property.sql` chuyển liên kết listings.room_id sang property_id qua rooms.property_id, giữ nguyên ID và lịch sử tin/Boost. Những tin cũ cùng cơ sở vẫn có trong lịch sử/admin; công khai chỉ lấy tin PUBLISHED còn hạn mới nhất (publishedAt rồi ID). Không sửa V11/V12. Chạy migration bằng Flyway khi cập nhật BE; chưa áp dụng trên database ứng dụng.

Kiểm chứng mới: test API và migration dùng MySQL riêng; FE gọi API công khai trả đầy đủ cơ sở, không gọi API quản lý phòng. 

`87 test qua: 67 PackageCatalogApiTest, 7 PropertyApiTest, 13 SubscriptionSchemaMigrationTest; Maven package thành công.`
