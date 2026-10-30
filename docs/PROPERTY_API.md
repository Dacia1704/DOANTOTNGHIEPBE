# Property và đơn vị cho thuê

API dùng `ApiResponse<T>` hiện tại. JWT bắt buộc; tạo thành công trả HTTP 200.

## Cơ sở cho thuê (LANDLORD)

- `POST /api/properties`: JSON tạo Property, tiện ích chung, các Room và tiện ích riêng trong một transaction.
- `GET /api/properties/mine?page=0&size=10`: danh sách cơ sở của tài khoản hiện tại; size 1–100.
- `GET /api/properties/{id}`: chi tiết cơ sở và các Room; chỉ chủ cơ sở được xem (403 nếu không sở hữu, 404 nếu không tồn tại).

```json
{
  "name": "Nhà trọ ABC",
  "propertyTypeId": "property-type-id",
  "rentalMode": "BY_ROOM",
  "totalFloors": 3,
  "provinceId": "province-id",
  "wardId": "ward-id",
  "addressLine": "123 Nguyễn Trãi",
  "amenityIds": ["camera-id", "parking-id"],
  "defaultRoom": {
    "roomTypeId": "room-type-id",
    "price": 3000000,
    "electricityPrice": 3500,
    "waterPrice": 100000,
    "serviceFee": 100000,
    "defaultDeposit": 3000000,
    "areaM2": 25,
    "maxOccupants": 2
  },
  "floors": [
    {"floorNumber": 1, "rooms": [
      {"roomNumber": "A101", "amenityIds": ["air-conditioner-id"]},
      {"roomNumber": "A102", "price": 3500000, "areaM2": 30}
    ]}
  ]
}
```

`landlordId` lấy từ Principal. `floors` và mỗi danh sách `rooms` phải có phần tử. `floorNumber` có thể null, khi có phải >= 0; không sinh số phòng từ tầng. `totalFloors` tùy chọn, >= 0. `latitude`/`longitude` tùy chọn, trong khoảng [-90,90]/[-180,180]. Ward phải thuộc Province.

`defaultRoom` có thể bỏ qua khi mỗi Room có đủ dữ liệu. Mỗi trường null của Room dùng giá trị mặc định tương ứng; số 0 là override thực sự đối với phí. Kết quả phải có roomType, price > 0, areaM2 > 0, maxOccupants > 0; các phí không âm. Default chỉ là input, không lưu vào Property. `amenityIds` của Property và Room độc lập, không kế thừa; ID trùng được loại bỏ.

`WHOLE` gửi cùng cấu trúc nhưng đúng một Room, ví dụ `roomNumber: "HOUSE"`; `BY_ROOM` có ít nhất một Room. Số phòng bắt buộc, tối đa 30 ký tự, trim và không trùng trong Property. Lỗi validation/count/default là 400, trùng số phòng 409, master data không tồn tại 404. Một Room lỗi khiến toàn bộ request rollback.

Response Property có `id`, `landlordId`, `name`, `propertyTypeId`, `propertyTypeName`, `rentalMode`, `totalFloors`, thông tin địa chỉ/tọa độ, `amenities`, `images`, `rooms`, `createdAt`, `updatedAt`. Mỗi Room dùng RoomResponse hiện tại và bổ sung `propertyId`, `roomNumber`, `floorNumber`; địa chỉ lấy từ Property. `images` Property hiện có entity/repository phục vụ lưu dữ liệu, chưa thêm API upload ảnh chung trong phạm vi này.

## Danh mục PropertyType

- `GET /api/property-types?page=0&size=10`, `GET /api/property-types/{id}`: tài khoản đã đăng nhập.
- `POST /api/property-types`, `PUT /api/property-types/{id}`, `DELETE /api/property-types/{id}`: ADMIN.
- Body tạo/sửa: `{"name":"Nhà trọ","description":"Mô tả"}`. Tên bắt buộc, tối đa 100 ký tự, duy nhất không phân biệt hoa thường. Trùng tên hoặc xóa loại đang dùng trả 409.
- Response có id, name, description, createdAt, updatedAt. Không có cây cha/con; RoomType vẫn giữ cây hiện tại cho loại đơn vị cho thuê.

## Migration và phạm vi tương thích

`V11__property_rental_units.sql` tạo bốn bảng properties/property_types/property_amenities/property_images; chuyển dữ liệu địa chỉ/chủ sở hữu khỏi rooms sau khi backfill. Mỗi Room cũ có một Property riêng với cùng UUID, rentalMode BY_ROOM, roomNumber LEGACY, PropertyType tạm “Cơ sở chuyển đổi”. Không tự gom phòng theo địa chỉ hoặc suy luận loại bất động sản. Cần phân loại lại dữ liệu cũ bằng một tác vụ riêng; scope này chưa có API sửa Property.

Room UUID được giữ nguyên nên Listing, RentalAgreement và RoomImage vẫn tham chiếu Room hiện tại. Listing vẫn `Listing -> Room`; chỉ sửa đường dẫn truy cập owner và query qua `Room.property`. Chat lấy landlord qua Property. RentalRequest vẫn tham chiếu Listing và giữ landlord snapshot, RentalAgreement vẫn tham chiếu Room và giữ snapshot, không đổi nghiệp vụ của các module này.

Unique `(property_id, room_number)` và `(property_id, amenity_id)` bảo vệ DB; unique RoomAmenity có sẵn từ V1 được tái sử dụng. Index FK do InnoDB tạo tự động và unique composite đã bao phủ property_id; chỉ bổ sung index rental_status. Không thêm Floor table, BaseEntity, soft delete hoặc cascade ALL.

## Kiểm thử

`PropertyApiTest`: API qua MockMvc với MySQL thật ở schema tách riêng, JPA validate, mặc định/override, tiện ích chung/riêng, phân quyền, phòng trùng, WHOLE, cập nhật Room và rollback. `SubscriptionSchemaMigrationTest` bổ sung case nâng V10 -> V11 giữ Room ID, địa chỉ, tọa độ và Listing FK. Các test MySQL dùng cùng biến môi trường có sẵn `SUBSCRIPTION_TEST_MYSQL_URL`, `SUBSCRIPTION_TEST_MYSQL_USER`, `SUBSCRIPTION_TEST_MYSQL_PASSWORD` và chỉ tạo/xóa schema test ngẫu nhiên.

Kết quả kiểm tra trong task: 88 test qua, không lỗi/skip (6 Property API, 64 catalogue/Listing/payment API, 12 migration, 6 VNPay unit test). `mvnw.cmd -q -DskipTests package` thành công. Flyway được chạy trên MySQL 8.0.46 riêng với các schema test ngẫu nhiên; chưa áp dụng V11 vào database ứng dụng. Test `RentalManagementApplicationTests.contextLoads` yêu cầu cấu hình hạ tầng ứng dụng và chưa chạy trong lần kiểm chứng này.

## File được tạo

- `docs/PROPERTY_API.md`
- `src/main/java/com/tamdaptit/rentalmanagement/property/controller/PropertyController.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/controller/PropertyTypeController.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/dto/request/CreateFloorRequest.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/dto/request/CreatePropertyRequest.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/dto/request/CreateRoomItemRequest.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/dto/request/DefaultRoomRequest.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/dto/request/PropertyTypeRequest.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/dto/response/PropertyResponse.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/dto/response/PropertyTypeResponse.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/entity/Property.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/entity/PropertyAmenity.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/entity/PropertyImage.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/entity/PropertyType.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/entity/enums/PropertyRentalMode.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/mapper/PropertyMapper.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/mapper/PropertyTypeMapper.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/repository/PropertyAmenityRepository.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/repository/PropertyImageRepository.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/repository/PropertyRepository.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/repository/PropertyTypeRepository.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/service/PropertyService.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/service/PropertyTypeService.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/service/RoomDefaultsResolver.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/service/impl/PropertyServiceImpl.java`
- `src/main/java/com/tamdaptit/rentalmanagement/property/service/impl/PropertyTypeServiceImpl.java`
- `src/main/resources/db/migration/V11__property_rental_units.sql`
- `src/test/java/com/tamdaptit/RentalManagement/property/PropertyApiTest.java`

## File được sửa

- `docs/api.md`
- `src/main/java/com/tamdaptit/rentalmanagement/chat/service/impl/ChatServiceImpl.java`
- `src/main/java/com/tamdaptit/rentalmanagement/common/exception/ErrorCode.java`
- `src/main/java/com/tamdaptit/rentalmanagement/listing/mapper/ListingMapper.java`
- `src/main/java/com/tamdaptit/rentalmanagement/listing/repository/ListingRepository.java`
- `src/main/java/com/tamdaptit/rentalmanagement/listing/service/impl/ListingServiceImpl.java`
- `src/main/java/com/tamdaptit/rentalmanagement/room/dto/request/RoomRequest.java`
- `src/main/java/com/tamdaptit/rentalmanagement/room/dto/response/RoomResponse.java`
- `src/main/java/com/tamdaptit/rentalmanagement/room/entity/Room.java`
- `src/main/java/com/tamdaptit/rentalmanagement/room/entity/RoomAmenity.java`
- `src/main/java/com/tamdaptit/rentalmanagement/room/mapper/RoomMapper.java`
- `src/main/java/com/tamdaptit/rentalmanagement/room/mapper/RoomTypeMapper.java`
- `src/main/java/com/tamdaptit/rentalmanagement/room/repository/RoomRepository.java`
- `src/main/java/com/tamdaptit/rentalmanagement/room/service/impl/AmenityServiceImpl.java`
- `src/main/java/com/tamdaptit/rentalmanagement/room/service/impl/RoomServiceImpl.java`
- `src/main/java/com/tamdaptit/rentalmanagement/subscription/service/BoostAllocationService.java`
- `src/main/java/com/tamdaptit/rentalmanagement/subscription/service/impl/ServiceSubscriptionServiceImpl.java`
- `src/test/java/com/tamdaptit/RentalManagement/subscription/PackageCatalogApiTest.java`
- `src/test/java/com/tamdaptit/RentalManagement/subscription/SubscriptionSchemaMigrationTest.java`
