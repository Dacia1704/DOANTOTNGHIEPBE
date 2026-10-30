# ROOM / PROPERTY REFACTOR RULES

## 1. Mục tiêu

Refactor module `room` để hỗ trợ mô hình cho thuê thực tế:

1. Một chủ trọ có thể tạo một bất động sản/cơ sở cho thuê (`Property`).
2. Một `Property` có thể có nhiều đơn vị cho thuê (`Room`).
3. Ví dụ:
    - Nhà trọ 5 tầng, mỗi tầng 2 phòng.
    - Chung cư mini có nhiều căn/phòng cho thuê riêng.
    - Một căn/nhà chỉ có một đơn vị cho thuê.
4. Chủ trọ không phải nhập lại toàn bộ thông tin chung cho từng phòng.
5. Có thể nhập thông tin mặc định rồi override thông tin ở từng phòng.
6. Phân biệt rõ:
    - `Property`: bất động sản/cơ sở vật lý.
    - `Room`: đơn vị có thể được thuê độc lập.
    - `RoomType`: loại của đơn vị cho thuê.
    - `Amenity`: tiện ích.
7. Chưa refactor nghiệp vụ `Listing` trong task này.

---

# 2. Nguyên tắc domain

Domain sau khi refactor:

```text
User (Landlord)
       |
       | 1:N
       v
    Property
       |
       | 1:N
       v
      Room
       |
       +------ N:1 ------ RoomType
       |
       +------ N:M ------ Amenity

Property
       |
       +------ N:M ------ Amenity
```

Ý nghĩa:

```text
Property
= một bất động sản/cơ sở cho thuê

Ví dụ:
- Nhà trọ ABC
- Chung cư mini ABC
- Nhà số 10 Nguyễn Trãi
- Căn hộ ABC
```

```text
Room
= một đơn vị có thể được tenant lựa chọn để thuê

Ví dụ:
- Phòng 101
- Phòng 102
- Phòng 301
- Căn A01
- Căn A02
```

Không hiểu `Room` theo nghĩa bắt buộc là "phòng ngủ".

Trong domain hiện tại, `Room` là rental unit / đơn vị cho thuê.

---

# 3. RentalMode

Tạo enum:

```java
PropertyRentalMode
```

Giá trị:

```java
WHOLE
BY_ROOM
```

Ý nghĩa:

### WHOLE

Property được cho thuê nguyên căn/toàn bộ.

Ví dụ:

```text
Nhà nguyên căn
Căn hộ nguyên căn
```

### BY_ROOM

Property chứa nhiều Room và các Room được thuê độc lập.

Ví dụ:

```text
Nhà trọ ABC
├── 101
├── 102
├── 201
└── 202
```

`PropertyRentalMode` là enum cố định.

KHÔNG tạo table để admin CRUD rental mode.

KHÔNG sử dụng `RoomType` để biểu diễn:

```text
Cho thuê nguyên căn
Cho thuê theo phòng
```

Đây là business mode, không phải room type.

---

# 4. Tạo entity Property

Tạo package phù hợp với cấu trúc project hiện tại, ưu tiên:

```text
com.tamdaptit.rentalmanagement.property
```

hoặc nếu project đang tổ chức domain room chung thì có thể đặt trong module phù hợp, nhưng phải giữ cấu trúc nhất quán.

Tạo:

```java
Property
```

Property chịu trách nhiệm lưu thông tin chung của bất động sản.

Các field tối thiểu:

```java
String id;

User landlord;

String name;

PropertyRentalMode rentalMode;

Integer totalFloors;

Province province;

Ward ward;

String addressLine;

BigDecimal latitude;

BigDecimal longitude;

LocalDateTime createdAt;

LocalDateTime updatedAt;
```

Mapping:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "landlord_id", nullable = false)
private User landlord;
```

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "province_id", nullable = false)
private Province province;
```

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "ward_id", nullable = false)
private Ward ward;
```

```java
@Enumerated(EnumType.STRING)
@Column(name = "rental_mode", nullable = false, length = 20)
private PropertyRentalMode rentalMode;
```

Không bắt buộc tạo bidirectional:

```java
@OneToMany
List<Room>
```

trong Property nếu project hiện tại không cần navigate theo hướng entity.

Ưu tiên query thông qua repository thay vì tạo collection entity không cần thiết.

---

# 5. PropertyType

Tạo riêng:

```java
PropertyType
```

KHÔNG dùng `RoomType` để biểu diễn cả loại Property và loại Room.

`PropertyType` là danh mục admin có thể CRUD.

Ví dụ:

```text
Nhà trọ
Chung cư mini
Căn hộ
Nhà riêng
Biệt thự
```

Entity tối thiểu:

```java
@Entity
@Table(name = "property_types")
public class PropertyType {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

Property có:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "property_type_id", nullable = false)
private PropertyType propertyType;
```

Không cần `parent_id` cho PropertyType trong task này trừ khi codebase hiện tại có requirement thực tế.

---

# 6. Refactor Room

Entity `Room` hiện tại đang chứa:

```java
User landlord;

Province province;
Ward ward;
String addressLine;

BigDecimal latitude;
BigDecimal longitude;
```

Sau refactor, REMOVE các field trên khỏi `Room`.

Lý do:

Room thuộc Property.

Các thông tin:

```text
landlord
địa chỉ
province
ward
latitude
longitude
```

được lấy thông qua:

```text
Room
 -> Property
 -> landlord/address
```

Không duplicate dữ liệu này ở mỗi Room.

---

# 7. Quan hệ Room -> Property

Thêm vào Room:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "property_id", nullable = false)
private Property property;
```

Thêm:

```java
@Column(name = "room_number", nullable = false, length = 30)
private String roomNumber;
```

Thêm:

```java
@Column(name = "floor_number")
private Integer floorNumber;
```

Không dùng Integer cho roomNumber vì có thể có:

```text
101
A101
A-01
P301
Căn A1
```

RoomNumber phải là String.

---

# 8. RoomType

Giữ entity:

```java
RoomType
```

RoomType chỉ mô tả loại đơn vị cho thuê.

Ví dụ:

```text
Phòng đơn
Phòng đôi
Phòng khép kín
Studio
Căn 1PN
Căn 2PN
```

Quan hệ hiện tại:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "room_type_id", nullable = false)
private RoomType roomType;
```

GIỮ NGUYÊN.

`parent_id` hiện tại có thể giữ:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_id")
private RoomType parent;
```

Có thể dùng cho hierarchy:

```text
Phòng trọ
├── Phòng đơn
├── Phòng đôi
└── Phòng khép kín

Căn hộ
├── Studio
├── Căn 1PN
└── Căn 2PN
```

Tuy nhiên:

KHÔNG tạo:

```text
Cho thuê nguyên căn
└── ...

Cho thuê theo phòng
└── ...
```

trong RoomType.

---

# 9. Room sau refactor

Room tối thiểu phải có:

```java
String id;

Property property;

RoomType roomType;

String roomNumber;

Integer floorNumber;

BigDecimal price;

BigDecimal electricityPrice;

BigDecimal waterPrice;

BigDecimal serviceFee;

BigDecimal defaultDeposit;

BigDecimal areaM2;

Integer maxOccupants;

RoomRentalStatus rentalStatus;

LocalDateTime createdAt;

LocalDateTime updatedAt;
```

Giữ:

```java
RoomRentalStatus
```

và các enum hiện tại nếu hợp lý.

Default:

```java
@Builder.Default
private RoomRentalStatus rentalStatus = RoomRentalStatus.AVAILABLE;
```

Nếu field sử dụng `@Builder`, phải đảm bảo default thực sự hoạt động khi builder được sử dụng.

---

# 10. Unique Room Number

Một Property không được có hai Room cùng `room_number`.

Ví dụ:

```text
Property A
101
102
```

hợp lệ.

Property B vẫn có thể có:

```text
101
102
```

Do đó tạo unique constraint:

```java
@Table(
    name = "rooms",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_rooms_property_room_number",
            columnNames = {"property_id", "room_number"}
        )
    }
)
```

Unique là:

```text
(property_id, room_number)
```

KHÔNG unique toàn hệ thống theo `room_number`.

---

# 11. Amenity

Giữ:

```java
Amenity
```

là master data do Admin CRUD.

Ví dụ:

```text
WiFi
Điều hòa
Tủ lạnh
Máy giặt
Camera
Thang máy
Chỗ để xe
Ban công
Bình nóng lạnh
```

Không hard-code amenity bằng enum.

---

# 12. RoomAmenity

Giữ:

```java
RoomAmenity
```

Quan hệ:

```text
Room N:M Amenity
```

qua:

```text
room_amenities
```

Nhưng thêm unique constraint:

```text
(room_id, amenity_id)
```

để không thể thêm cùng một tiện ích hai lần cho cùng một Room.

Ví dụ:

```java
@Table(
    name = "room_amenities",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_room_amenity",
            columnNames = {"room_id", "amenity_id"}
        )
    }
)
```

Không cần chuyển sang `@ManyToMany`.

Giữ entity mapping `RoomAmenity` riêng để dễ mở rộng sau này.

---

# 13. PropertyAmenity

Tạo entity:

```java
PropertyAmenity
```

table:

```text
property_amenities
```

Fields:

```java
String id;

Property property;

Amenity amenity;
```

Quan hệ:

```text
Property N:M Amenity
```

PropertyAmenity dùng cho tiện ích chung.

Ví dụ:

```text
Nhà trọ ABC
├── Camera
├── Thang máy
├── Chỗ để xe
└── WiFi chung
```

RoomAmenity dùng cho tiện ích riêng:

```text
Room 301
├── Điều hòa
├── Tủ lạnh
└── Ban công
```

Thêm unique:

```text
(property_id, amenity_id)
```

---

# 14. RoomImage

Hiện tại:

```java
RoomImage -> Room
```

GIỮ NGUYÊN.

RoomImage dùng cho ảnh riêng của từng Room.

Ví dụ:

```text
Room 301
├── room301_1.jpg
├── room301_2.jpg
└── room301_3.jpg
```

---

# 15. PropertyImage

Tạo thêm:

```java
PropertyImage
```

để lưu ảnh chung của Property.

Ví dụ:

```text
Nhà trọ ABC
├── ảnh mặt tiền
├── ảnh hành lang
├── ảnh cầu thang
├── ảnh bãi xe
└── ảnh khu vực chung
```

Structure tương tự `RoomImage`:

```java
String id;

Property property;

String url;

String publicId;

Integer sortOrder;

LocalDateTime createdAt;
```

Table:

```text
property_images
```

Quan hệ:

```text
Property 1:N PropertyImage
```

---

# 16. Default value khi tạo nhiều Room

Mục tiêu UX:

Chủ trọ KHÔNG phải nhập giá và thông tin giống nhau cho từng phòng.

Ví dụ FE gửi:

```json
{
  "name": "Nhà trọ ABC",
  "propertyTypeId": "...",
  "rentalMode": "BY_ROOM",

  "provinceId": "...",
  "wardId": "...",
  "addressLine": "123 Nguyễn Trãi",

  "defaultRoom": {
    "roomTypeId": "...",
    "price": 3000000,
    "electricityPrice": 3500,
    "waterPrice": 100000,
    "serviceFee": 100000,
    "defaultDeposit": 3000000,
    "areaM2": 25,
    "maxOccupants": 2
  },

  "floors": [
    {
      "floorNumber": 1,
      "rooms": [
        {
          "roomNumber": "101"
        },
        {
          "roomNumber": "102"
        }
      ]
    },
    {
      "floorNumber": 2,
      "rooms": [
        {
          "roomNumber": "201"
        },
        {
          "roomNumber": "202",
          "price": 3500000,
          "areaM2": 30
        }
      ]
    }
  ]
}
```

`defaultRoom` là INPUT DTO phục vụ batch creation.

KHÔNG bắt buộc lưu:

```text
default_price
default_area
default_room_type
```

vào Property.

Sau khi tạo, mỗi Room phải chứa giá trị thực tế cuối cùng của chính nó.

Ví dụ:

```text
101 -> 3.000.000
102 -> 3.000.000
201 -> 3.000.000
202 -> 3.500.000
```

---

# 17. Default -> Override rule

Khi tạo Room:

Nếu Room request có giá trị riêng:

```text
room.price
```

thì dùng giá trị đó.

Nếu null:

```text
room.price = defaultRoom.price
```

Áp dụng cùng nguyên tắc cho:

```text
roomTypeId
price
electricityPrice
waterPrice
serviceFee
defaultDeposit
areaM2
maxOccupants
```

Pseudo code:

```java
room.setPrice(
    roomRequest.getPrice() != null
        ? roomRequest.getPrice()
        : defaultRoom.getPrice()
);
```

Không mutate DTO.

Có thể tách helper/private method để merge default với override, tránh service method quá dài.

---

# 18. Batch Create Property + Rooms

Tạo API/service phù hợp với convention hiện tại của project.

Ví dụ:

```text
POST /api/properties
```

Request tạo:

```text
Property
+
PropertyAmenities
+
Rooms
+
RoomAmenities nếu request có
```

Toàn bộ operation phải:

```java
@Transactional
```

Nếu tạo Room thứ 8/10 lỗi thì rollback toàn bộ Property và Rooms đã tạo trong request đó.

Không để trạng thái:

```text
Property tồn tại
nhưng chỉ tạo được một phần Room
```

---

# 19. Trường hợp BY_ROOM

Nếu:

```text
rentalMode = BY_ROOM
```

thì request phải có ít nhất một Room.

Ví dụ:

```text
Nhà trọ ABC
├── 101
├── 102
├── 201
└── 202
```

Mỗi Room có:

```text
roomNumber
floorNumber
roomType
price
area
rentalStatus
...
```

Tenant sau này sẽ chọn chính xác Room để thuê.

---

# 20. Trường hợp WHOLE

Để giữ cho downstream rental flow đơn giản, trong task này áp dụng rule:

> Mọi thứ có thể được thuê đều phải có một `Room` record.

Với:

```text
rentalMode = WHOLE
```

tạo đúng MỘT Room đại diện cho toàn bộ Property.

Ví dụ:

```text
Property:
Nhà nguyên căn Nguyễn Trãi
rentalMode = WHOLE

Room:
roomNumber = "WHOLE"
roomType = Nhà nguyên căn / loại rental unit phù hợp
price = 15.000.000
areaM2 = 120
maxOccupants = 6
rentalStatus = AVAILABLE
```

Không tạo nhiều Room cho `WHOLE`.

Business validation:

```text
WHOLE -> exactly 1 rental unit
BY_ROOM -> >= 1 rental unit
```

Không hard-code UI label `"WHOLE"` làm nội dung hiển thị cho tenant.

`roomNumber = "WHOLE"` chỉ có thể dùng như internal identifier nếu cần.

Nếu có cách sạch hơn trong codebase hiện tại để đánh dấu rental unit duy nhất thì có thể áp dụng, nhưng phải giữ nguyên invariant:

```text
WHOLE Property có đúng 1 Room có thể thuê.
```

---

# 21. RoomType với WHOLE

Do `Room` hiện tại bắt buộc có:

```java
RoomType roomType;
```

không được bỏ relation này.

Admin vẫn quản lý RoomType.

Có thể có các type như:

```text
Phòng đơn
Phòng đôi
Studio
Căn 1PN
Căn 2PN
Nhà nguyên căn
```

Nhưng tuyệt đối không dùng:

```text
Cho thuê nguyên căn
Cho thuê theo phòng
```

làm RoomType.

`WHOLE/BY_ROOM` thuộc `PropertyRentalMode`.

---

# 22. API tạo nhanh nhiều tầng/phòng

Không bắt backend phụ thuộc vào convention:

```text
floor 1 -> 101
floor 2 -> 201
```

FE có thể tự generate roomNumber rồi gửi danh sách.

Backend phải coi:

```text
roomNumber
floorNumber
```

là dữ liệu explicit.

Không tự suy luận:

```java
roomNumber = floor * 100 + index;
```

trong domain service.

Lý do có thể tồn tại:

```text
A101
A102
P01
G01
101A
```

---

# 23. Validation

Phải validate tối thiểu:

### Property

```text
landlord != null
propertyType != null
rentalMode != null
province != null
ward != null
addressLine not blank
```

### Room

```text
roomNumber not blank
roomType != null
price > 0
areaM2 > 0
maxOccupants > 0
```

Nếu:

```text
floorNumber != null
```

thì không được âm.

### BY_ROOM

```text
rooms.size >= 1
```

### WHOLE

```text
rooms.size == 1
```

### Duplicate

Trong cùng một Property không được có:

```text
101
101
```

Check ở application/service để trả lỗi đẹp.

Đồng thời vẫn phải có UNIQUE CONSTRAINT ở database.

---

# 24. Authorization

Không nhận:

```text
landlordId
```

từ client để quyết định owner nếu hệ thống hiện tại lấy user từ JWT/SecurityContext.

Landlord phải được xác định từ authenticated user theo security convention hiện tại của project.

Không cho client giả landlord khác.

Admin operations giữ theo authorization hiện tại của project.

---

# 25. Repository

Tạo repository cần thiết:

```text
PropertyRepository
PropertyTypeRepository
PropertyAmenityRepository
PropertyImageRepository
```

Update `RoomRepository` nếu cần.

Các query hữu ích:

```text
findByPropertyId(...)
findByPropertyIdAndRentalStatus(...)
existsByPropertyIdAndRoomNumber(...)
```

Không tạo repository method thừa nếu chưa được sử dụng.

---

# 26. DTO

Không expose JPA entity trực tiếp từ controller.

Tạo DTO theo convention hiện tại của project.

Có thể tổ chức:

```text
property/
├── controller/
├── dto/
│   ├── request/
│   └── response/
├── entity/
├── repository/
├── service/
└── mapper/
```

Request chính:

```text
CreatePropertyRequest
DefaultRoomRequest
CreateRoomItemRequest
CreateFloorRequest
```

Response:

```text
PropertyResponse
RoomResponse
```

Nếu project đang dùng MapStruct thì tiếp tục sử dụng MapStruct theo style hiện tại.

Không đưa business logic merge default/override vào Mapper.

Business logic đó thuộc service/helper.

---

# 27. Không tạo Floor entity

KHÔNG tạo table:

```text
floors
```

trong task này.

Floor hiện chỉ là thuộc tính grouping để UI hiển thị.

Lưu:

```java
Integer floorNumber;
```

trong Room là đủ.

DTO có thể có:

```text
floors[]
```

để FE gửi dễ hơn, nhưng database không cần `floors`.

---

# 28. Listing — CHƯA REFACTOR TRONG TASK NÀY

Hiện tại Listing có:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "room_id", nullable = false)
private Room room;
```

Kiến trúc tương lai dự kiến sẽ là:

```text
Listing
   |
   v
Property
   |
   v
Rooms
```

tức Listing nhiều khả năng sẽ chuyển từ:

```text
Listing -> Room
```

sang:

```text
Listing -> Property
```

để một listing detail hiển thị nhiều Room option.

TUY NHIÊN:

### Trong task này:

KHÔNG triển khai lại Listing API/service/controller.

KHÔNG refactor:

```text
ListingContactEvent
ListingDailyStat
ListingViewHistory
SavedListing
```

Các entity này vẫn liên kết với `Listing` như hiện tại.

Nếu việc compile bắt buộc phải sửa import/mapping tối thiểu do Room refactor thì thực hiện thay đổi nhỏ nhất cần thiết.

Không tự ý xóa Listing hoặc các bảng thống kê.

---

# 29. Lưu ý compatibility với Listing hiện tại

Do Listing hiện tại bắt buộc:

```java
private Room room;
```

Room vẫn tồn tại sau refactor nên code có thể tiếp tục compile.

Tạm thời Listing vẫn trỏ tới một Room.

Việc chuyển:

```java
Listing.room
```

thành:

```java
Listing.property
```

sẽ thực hiện ở task Listing riêng sau.

Không implement nửa vời cả hai relation nếu chưa cần.

---

# 30. Flyway

Project sử dụng Flyway làm source of truth.

KHÔNG dựa vào:

```text
ddl-auto=create
ddl-auto=update
```

để thay schema.

Tạo migration Flyway mới theo version tiếp theo của project.

KHÔNG sửa migration cũ đã được áp dụng nếu project convention không cho phép.

Migration cần xử lý:

```text
properties
property_types
property_amenities
property_images

rooms:
+ property_id
+ room_number
+ floor_number

rooms:
- landlord_id
- province_id
- ward_id
- address_line
- latitude
- longitude
```

Tuy nhiên phải xem schema/migration hiện tại trước khi viết migration.

Không đoán tên constraint/index.

Nếu database hiện có dữ liệu cần migration, phải có strategy migrate data trước khi chuyển column sang NOT NULL hoặc drop column cũ.

---

# 31. Existing data migration

Nếu `rooms` hiện đã có dữ liệu:

Mỗi Room cũ hiện chứa:

```text
landlord
address
province
ward
coordinates
```

Không được đơn giản drop các column đó trước khi migrate.

Có thể cần:

1. tạo Property tương ứng;
2. gán `rooms.property_id`;
3. populate `room_number`;
4. sau đó mới set NOT NULL;
5. sau cùng mới drop các column duplicated.

Nếu không thể xác định chính xác grouping Room cũ thành Property nào thì KHÔNG tự động group sai.

Trong trường hợp không đủ dữ liệu để grouping, ưu tiên migration an toàn hoặc báo rõ assumption.

---

# 32. Index

Xem xét index:

```text
rooms.property_id
rooms.room_type_id
rooms.rental_status

properties.landlord_id
properties.property_type_id
properties.province_id
properties.ward_id

property_amenities.property_id
property_amenities.amenity_id

room_amenities.room_id
room_amenities.amenity_id
```

Không tạo index duplicate với index được tạo implicit/explicit bởi schema hiện tại nếu không cần.

---

# 33. Không sử dụng BaseEntity

Project hiện tại không sử dụng BaseEntity chung cho schema này.

Tiếp tục sử dụng explicit:

```text
created_at
updated_at
```

ở entity cần thiết.

Mapping table:

```text
room_amenities
property_amenities
```

không cần timestamps nếu không có business requirement.

---

# 34. Không thêm soft delete tùy tiện

Không tự thêm:

```text
deleted
deleted_at
is_deleted
```

vào Property/Room/Amenity nếu schema hiện tại chưa có requirement.

Deletion/lifecycle sẽ được xử lý ở task riêng.

---

# 35. Không dùng cascade bừa bãi

Không thêm:

```java
CascadeType.ALL
```

chỉ để code tạo dữ liệu ngắn hơn.

Đặc biệt:

```text
Property -> Room
Room -> Amenity
Property -> Amenity
```

phải kiểm soát lifecycle rõ ràng.

Amenity là master data dùng chung, tuyệt đối không cascade delete Amenity khi xóa Room/Property.

Service có thể save explicit trong transaction.

---

# 36. Example cuối cùng

Ví dụ landlord tạo:

```text
Property:
    Nhà trọ ABC
    propertyType = Nhà trọ
    rentalMode = BY_ROOM
    address = 123 Nguyễn Trãi
    floors = 3
```

Tiện ích chung:

```text
Camera
Thang máy
Chỗ để xe
```

Rooms:

```text
101
    floor = 1
    type = Phòng đơn
    price = 3.000.000
    amenities:
        Điều hòa
        Bình nóng lạnh

102
    floor = 1
    type = Phòng đơn
    price = 3.000.000
    amenities:
        Điều hòa

201
    floor = 2
    type = Studio
    price = 3.500.000
    amenities:
        Điều hòa
        Tủ lạnh
        Ban công
```

Database conceptual result:

```text
PROPERTY P01
│
├── PropertyAmenity -> Camera
├── PropertyAmenity -> Thang máy
├── PropertyAmenity -> Chỗ để xe
│
├── ROOM R101
│      ├── RoomType -> Phòng đơn
│      ├── price -> 3tr
│      ├── RoomAmenity -> Điều hòa
│      └── RoomAmenity -> Bình nóng lạnh
│
├── ROOM R102
│      ├── RoomType -> Phòng đơn
│      └── price -> 3tr
│
└── ROOM R201
       ├── RoomType -> Studio
       ├── price -> 3.5tr
       ├── RoomAmenity -> Điều hòa
       ├── RoomAmenity -> Tủ lạnh
       └── RoomAmenity -> Ban công
```

---

# 37. Expected architecture after this task

Sau khi hoàn thành:

```text
User
 |
 | 1:N
 v
Property -------- N:1 -------- PropertyType
 |
 +-------- N:M --------------- Amenity
 |
 | 1:N
 v
Room ------------ N:1 -------- RoomType
 |
 +-------- N:M --------------- Amenity
 |
 +-------- 1:N --------------- RoomImage


Property -------- 1:N -------- PropertyImage
```

Listing vẫn tạm thời:

```text
Listing -> Room
```

và sẽ được refactor trong task riêng thành hướng:

```text
Listing -> Property -> Rooms
```

---

# 38. Quy tắc triển khai

Trước khi code:

1. Đọc toàn bộ entity/schema/Flyway hiện tại liên quan:
    - rooms
    - room_types
    - amenities
    - room_amenities
    - room_images
    - listings
    - rental_requests
    - agreements nếu có.

2. Search toàn project các usage của:
    - `Room`
    - `RoomRepository`
    - `room.getLandlord()`
    - `room.getProvince()`
    - `room.getWard()`
    - `room.getAddressLine()`
    - `room.getLatitude()`
    - `room.getLongitude()`

3. Xác định compile impact trước khi xóa field.

4. Refactor tất cả usage bị ảnh hưởng bởi việc chuyển thông tin từ Room sang Property.

Ví dụ:

```java
room.getLandlord()
```

sẽ thành:

```java
room.getProperty().getLandlord()
```

Tương tự address.

5. Không chỉ sửa entity rồi để project compile error.

6. Không thay đổi business module không liên quan nếu không cần.

7. Sau khi code:
    - build project;
    - fix compile errors;
    - chạy test hiện có;
    - kiểm tra Flyway;
    - kiểm tra validation;
    - kiểm tra transaction.

8. Không tự ý thay đổi response contract của API không thuộc task nếu không bắt buộc.

---

# 39. Kết quả cần báo cáo

Sau khi hoàn thành, báo rõ:

1. File nào được tạo.
2. File nào được sửa.
3. Migration Flyway nào được thêm.
4. Entity relationship cuối cùng.
5. API nào được thêm/sửa.
6. Những phần Listing chưa xử lý.
7. Những assumption đã sử dụng.
8. Build/test result.
9. Nếu phát hiện RentalRequest/Agreement/Chat phụ thuộc trực tiếp vào Room cũ, liệt kê impact nhưng không refactor ngoài scope nếu chưa cần.