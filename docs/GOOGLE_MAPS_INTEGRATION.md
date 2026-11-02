# Tích hợp Google Maps: backend và kế hoạch frontend

## Backend đã triển khai

Vị trí thuộc bất động sản (Property). Các phòng và tin của cùng cơ sở dùng chung địa chỉ, vĩ độ và kinh độ. Backend lưu tọa độ người dùng chọn, trả dữ liệu vị trí qua API và truy vấn tin trong database; phạm vi này không cần Google API key ở backend, không gọi geocoding hoặc tính tuyến đường.

### Tạo bất động sản

`POST /api/properties` giữ cấu trúc tạo cơ sở/phòng hiện có và nhận thêm cặp tọa độ tùy chọn:

```json
{
  "latitude": 10.762622,
  "longitude": 106.660172
}
```

Đây chỉ là phần tọa độ của body; cần gửi đầy đủ các trường tạo cơ sở trong [PROPERTY_API.md](PROPERTY_API.md). Hai tọa độ phải cùng có giá trị hoặc cùng null. Vĩ độ trong [-90,90], kinh độ trong [-180,180]; số 0 hợp lệ. Tỉnh/phường và địa chỉ vẫn dùng dữ liệu của hệ thống, phường phải thuộc tỉnh đã chọn.

### Sửa địa chỉ và vị trí

`PUT /api/properties/{id}/location` yêu cầu JWT LANDLORD sở hữu bất động sản:

```json
{
  "provinceId": "province-id",
  "wardId": "ward-id",
  "addressLine": "123 Nguyễn Trãi",
  "latitude": 10.762622,
  "longitude": 106.660172
}
```

- Thay thế toàn bộ địa chỉ và tọa độ trong một transaction với khóa cơ sở; trả HTTP 200, `ApiResponse<PropertyResponse>`.
- Tỉnh, phường và địa chỉ bắt buộc; phường phải thuộc tỉnh. Địa chỉ được trim, tối đa 255 ký tự.
- Gửi hai tọa độ null hoặc bỏ cả hai để xóa ghim. Không gửi riêng một tọa độ.
- Trả 400 khi dữ liệu không hợp lệ; 403 khi sai vai trò hoặc không sở hữu; 404 khi cơ sở/tỉnh/phường không tồn tại. Chưa đăng nhập bị từ chối bởi cơ chế JWT hiện có.
- Không sửa giá, tiện ích hoặc danh sách phòng. Tất cả phòng và tin tham chiếu cơ sở sẽ nhận vị trí mới; endpoint này không tự đổi trạng thái duyệt tin.

### API trả vị trí cho FE

- Chủ trọ: `GET /api/properties/{id}`, `GET /api/properties/mine`.
- Tin công khai: `GET /api/listings`, `GET /api/listings/{id}`.
- Admin: `GET /api/listings/admin`, `GET /api/listings/pending`.
- Trong ListingResponse, tọa độ nằm tại `property.latitude`, `property.longitude`; địa chỉ tại `property.addressLine`, `property.wardName`, `property.provinceName`.
- Cơ sở cũ thiếu tọa độ vẫn sử dụng được. FE xử lý cả trường null và trường không có trong JSON.

### Tìm tin theo vị trí

Mở rộng `GET /api/listings`, kết hợp các bộ lọc phòng hiện có:

```http
GET /api/listings?latitude=10.762622&longitude=106.660172&radiusKm=3&page=0&size=20
GET /api/listings?south=10.7&north=10.9&west=106.6&east=106.8&page=0&size=50
```

- Bán kính: cần đủ `latitude`, `longitude`, `radiusKm`; bán kính > 0, đơn vị km. Khoảng cách đường chim bay dùng Haversine, bán kính Trái Đất trung bình 6371.0088 km, không phải quãng đường di chuyển.
- Khung bản đồ: cần đủ `south`, `north`, `west`, `east`; vĩ độ [-90,90], kinh độ [-180,180], `south <= north`. Bao gồm điểm trên cạnh. `west > east` hỗ trợ khung qua kinh tuyến 180°.
- Nếu gửi cả hai nhóm, lấy phần giao. Có thể kết hợp `provinceId`, `wardId`, `roomTypeName`, `rentalStatus`, `minPrice`, `maxPrice`, `amenityNames`.
- Thiếu bộ tham số, số sai kiểu hoặc sai phạm vi trả 400.
- Chỉ trả tin công khai còn hạn; mỗi cơ sở lấy tin công khai mới nhất. Cơ sở thiếu một hoặc cả hai tọa độ bị loại khi lọc vị trí, vẫn có thể xuất hiện trong tìm kiếm thường.
- Lọc và đếm tại database trước phân trang; size 1–100, có `totalElements` và `totalPages`.
- Giữ thứ tự ưu tiên cao → thấp → thường, rồi thời gian đăng và ID; không đổi sang sắp xếp theo khoảng cách và chưa thêm trường khoảng cách vào response.

Không cần migration mới vì các cột tọa độ đã có. Không tự suy đoán tọa độ cho dữ liệu cũ. Khi dữ liệu tăng lớn, đánh giá hiệu năng và chỉ mục không gian trước khi bổ sung tối ưu riêng.

## Kiểm chứng backend

Đã chạy trên Java 21 và MySQL 8.0.45 riêng, áp dụng Flyway V1–V3 và JPA validate. `PropertyApiTest` và `PackageCatalogApiTest`: tổng 81 test qua, 0 lỗi, 0 bỏ qua trong worktree triển khai. Fixture test được cập nhật cho seed V2; database ứng dụng không dùng làm database kiểm thử.

```powershell
mvn.cmd '-Dtest=PropertyApiTest,PackageCatalogApiTest' test
```

Cấu hình `SUBSCRIPTION_TEST_MYSQL_URL` theo dạng `jdbc:mysql://127.0.0.1:<port>/`, `SUBSCRIPTION_TEST_MYSQL_USER`, `SUBSCRIPTION_TEST_MYSQL_PASSWORD` cho máy chủ MySQL kiểm thử. Test tự tạo/xóa schema ngẫu nhiên.

Các ca kiểm chứng gồm tọa độ 0, thiếu/sai cặp tọa độ, cập nhật/xóa ghim, phân quyền, phường không thuộc tỉnh, bán kính nhỏ, khung rỗng, điểm trên cạnh, giao hai bộ lọc, kinh tuyến 180°, tin ẩn/chờ duyệt/hết hạn, dữ liệu thiếu tọa độ, loại trùng cơ sở, phân trang, thứ tự ưu tiên, lọc giá/địa chỉ và response vị trí cho public/admin.

## Kế hoạch frontend Angular

### 1. Cấu hình và bản đồ dùng chung

- Cấu hình Maps JavaScript API và Places API (New), key cho trình duyệt giới hạn theo website/API được dùng.
- Tạo thành phần chọn vị trí và thành phần xem bản đồ; tải thư viện khi cần.
- Xử lý lỗi tải bản đồ và dữ liệu thiếu vị trí; không thay tọa độ null thành (0,0).

### 2. Form tạo và sửa cơ sở

- Thêm gợi ý địa chỉ, chọn ghim/kéo ghim; gửi tọa độ cùng body tạo cơ sở.
- Màn hình sửa vị trí đọc dữ liệu cơ sở, gửi đầy đủ địa chỉ và cặp tọa độ tới `PUT /api/properties/{id}/location`.
- Tỉnh/phường vẫn lấy từ danh mục backend. Tên Google trả về không phải ID nội bộ; cho người dùng xác nhận lựa chọn trước khi lưu.
- Khi đổi địa chỉ, xác nhận lại ghim hoặc xóa vị trí cũ để tránh lệch địa chỉ và tọa độ.

### 3. Chi tiết tin và admin duyệt tin

- Chi tiết tin lấy tọa độ từ `property`, hiển thị ghim, địa chỉ và nút chỉ đường mở Google Maps bằng Maps URL.
- Admin hiển thị cùng vị trí trong màn hình duyệt tin để đối chiếu địa chỉ.
- Dữ liệu thiếu tọa độ hiển thị địa chỉ; chủ trọ bổ sung ở form sửa vị trí.

### 4. Tìm kiếm phòng trên bản đồ

- Thêm chế độ danh sách/bản đồ; ghim có popup giá, ảnh và liên kết chi tiết tin.
- Khi bản đồ dừng di chuyển, gửi bốn cạnh khung cùng bộ lọc hiện có. Debounce và hủy/bỏ kết quả request cũ; đổi vùng hoặc bộ lọc thì về trang 0.
- “Tìm quanh tôi” xin quyền định vị trên trình duyệt rồi gửi tâm và bán kính. Khi chọn địa điểm qua autocomplete, dùng tọa độ địa điểm làm tâm.
- Chọn rõ chế độ bán kính/khung; chỉ gửi cả hai nếu UI thể hiện lấy phần giao. Tắt lọc thì bỏ cả nhóm tham số.
- Xử lý phân trang và tổng số kết quả; một trang không phải toàn bộ ghim. Không ghi đè thứ tự ưu tiên từ backend.

### 5. Nghiệm thu FE

Kiểm tra tạo/sửa/xóa ghim, vị trí dùng chung giữa cơ sở/phòng/tin, cơ sở cũ chưa có tọa độ, bản đồ lỗi, từ chối định vị, kết hợp lọc giá/tiện ích, kéo bản đồ nhanh, phân trang, admin xem vị trí và người khác không sửa được vị trí.

Tiện ích xung quanh và thời gian di chuyển triển khai sau khi có yêu cầu cụ thể, dùng Places Nearby Search/Routes.

## Tài liệu tham khảo

- [API chung](api.md), [Property API](PROPERTY_API.md).
- [Maps JavaScript API](https://developers.google.com/maps/documentation/javascript).
- [Places Autocomplete](https://developers.google.com/maps/documentation/places/web-service/place-autocomplete).
- [Maps URLs](https://developers.google.com/maps/documentation/urls/get-started).
- [Giới hạn API key](https://developers.google.com/maps/api-security-best-practices).
