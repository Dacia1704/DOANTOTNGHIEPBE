# Phân biệt Room và bài đăng

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
