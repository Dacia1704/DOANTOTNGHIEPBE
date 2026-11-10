# API Documentation - Rental Management

Nguồn API chung cho BE và FE. Chỉ cập nhật contract tại file này. [Mục lục tài liệu](../README.md).

Base URL:
- Local: http://localhost:8080
- API prefix: /api

Mẫu response chung:
```json
{
  "code": 200,
  "message": "Thành công",
  "data": {}
}
```

Lưu ý:
- Các trường null ở lớp bọc ApiResponse bị bỏ khỏi JSON, nên response không có dữ liệu sẽ không có trường data. HTTP 200 là mặc định; tạo người dùng và phường/xã trả HTTP 201.
- Tất cả endpoint protected, bao gồm đăng xuất, yêu cầu header:
  ```http
  Authorization: Bearer <access_token>
  ```
- Response thành công đều dùng format `ApiResponse<T>`.

---

## 1. Authentication

### 1.1 Đăng ký tài khoản
- Method: POST
- URL: /api/auth/register
- Request body:
```json
{
  "email": "tenant@gmail.com",
  "phone": "0901234567",
  "password": "Abc12345",
  "fullName": "Nguyễn Văn A",
  "citizenId": "123456789",
  "roleCode": "TENANT"
}
```
- Request fields:
  - `email`: email hợp lệ, bắt buộc
  - `phone`: định dạng Việt Nam, ví dụ 0901234567
  - `password`: tối thiểu 8 ký tự
  - `fullName`: bắt buộc
  - `citizenId`: tối đa 20 ký tự
  - `roleCode`: `TENANT` hoặc `LANDLORD`
- Response thành công:
```json
{
  "code": 200,
  "message": "Đăng ký thành công",
  "data": {
    "id": "d5f8c0ba-7f79-4a4a-8d87-1cbb0d0f4ef1",
    "email": "tenant@gmail.com",
    "phone": "0901234567",
    "fullName": "Nguyễn Văn A",
    "status": "ACTIVE",
    "roleCode": "TENANT",
    "authorities": [
      "ROLE_TENANT",
      "LISTING_READ"
    ],
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

### 1.2 Đăng nhập
- Method: POST
- URL: /api/auth/login
- Request body:
```json
{
  "email": "tenant@gmail.com",
  "password": "Abc12345"
}
```
- Response thành công:
```json
{
  "code": 200,
  "message": "Đăng nhập thành công",
  "data": {
    "id": "d5f8c0ba-7f79-4a4a-8d87-1cbb0d0f4ef1",
    "email": "tenant@gmail.com",
    "phone": "0901234567",
    "fullName": "Nguyễn Văn A",
    "avatarUrl": null,
    "status": "ACTIVE",
    "roles": ["TENANT"],
    "authorities": [
      "ROLE_TENANT",
      "LISTING_READ"
    ],
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessTokenExpiresAt": "2026-10-21T10:00:00"
  }
}
```

### 1.3 Làm mới token
- Method: POST
- URL: /api/auth/refresh
- Request body:
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```
- Response thành công:
```json
{
  "code": 200,
  "message": "Làm mới token thành công",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessTokenExpiresAt": "2026-10-21T10:30:00"
  }
}
```

### 1.4 Đăng xuất
- Method: POST
- URL: /api/auth/logout
- Header:
```http
Authorization: Bearer <access_token>
```
- Request body:
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```
- Response thành công:
```json
{
  "code": 200,
  "message": "Đăng xuất thành công"
}
```

---

### 1.5 Quên mật khẩu — gửi OTP

- Method: POST
- URL: /api/auth/forgot-password
- Quyền: Public, không cần access token.
- Content-Type: application/json
- Request body:

```json
{
  "email": "tenant@gmail.com"
}
```

- `email`: bắt buộc, email hợp lệ, tối đa 255 ký tự; được bỏ khoảng trắng hai đầu và chuyển về chữ thường.
- Nếu email tồn tại, gửi OTP loại `FORGOT_PASSWORD` qua email. API không trả OTP trong response.
- Response thành công (HTTP 200), kể cả khi email không tồn tại, để tránh tiết lộ tài khoản:

```json
{
  "code": 200,
  "message": "Nếu email tồn tại trong hệ thống, mã OTP đã được gửi"
}
```

- Lỗi: 400 nếu request không hợp lệ; 429 nếu gửi lại trong thời gian chờ.

### 1.6 Đặt lại mật khẩu khi quên mật khẩu

- Method: POST
- URL: /api/auth/reset-password
- Quyền: Public, không cần access token.
- Content-Type: application/json
- Request body:

```json
{
  "email": "tenant@gmail.com",
  "otp": "012345",
  "newPassword": "NewPassword123",
  "confirmPassword": "NewPassword123"
}
```

- Cả bốn trường đều bắt buộc; `email` phải hợp lệ, `otp` là chuỗi đúng 6 ký tự (giữ số 0 ở đầu).
- `newPassword`: từ 8 đến 100 ký tự, phải khác mật khẩu hiện tại; `confirmPassword` phải khớp.
- Dùng OTP nhận từ `/api/auth/forgot-password`; endpoint này tự kiểm tra OTP, không gọi `/api/auth/verify-email` trước đó.
- Khi OTP được xác thực thành công, mã bị xóa ngay. Nếu bước kiểm tra mật khẩu mới sau đó báo lỗi, cần lấy OTP mới để thử lại.
- Sau khi đặt lại thành công, tăng `authVersion` để vô hiệu hóa các access token và refresh token cũ; cần đăng nhập lại.
- Response thành công (HTTP 200):

```json
{
  "code": 200,
  "message": "Đặt lại mật khẩu thành công"
}
```

- Lỗi: 400 nếu OTP sai/hết hạn/đã dùng, request không hợp lệ, xác nhận mật khẩu không khớp hoặc mật khẩu mới trùng mật khẩu hiện tại; 404 nếu không tìm thấy email sau khi kiểm tra OTP.

### 1.7 Gửi OTP xác thực email

- Method: POST
- URL: /api/auth/send-verify-email
- Quyền: Public, không cần access token.
- Content-Type: application/json
- Request body:

```json
{
  "email": "tenant@gmail.com"
}
```

- `email`: bắt buộc, email hợp lệ, tối đa 255 ký tự; được bỏ khoảng trắng hai đầu và chuyển về chữ thường.
- Chỉ gửi cho tài khoản đã tồn tại và chưa xác thực email. OTP có loại `EMAIL_VERIFY`, không dùng để đặt lại mật khẩu.
- Response thành công (HTTP 200):

```json
{
  "code": 200,
  "message": "Mã OTP xác thực email đã được gửi"
}
```

- Lỗi: 400 nếu request không hợp lệ hoặc email đã được xác thực; 404 nếu email không tồn tại; 429 nếu gửi lại trong thời gian chờ.

### 1.8 Xác thực email bằng OTP

- Method: POST
- URL: /api/auth/verify-email
- Quyền: Public, không cần access token.
- Content-Type: application/json
- Request body:

```json
{
  "email": "tenant@gmail.com",
  "otp": "012345"
}
```

- `email`: bắt buộc, email hợp lệ; `otp`: bắt buộc, chuỗi đúng 6 ký tự, lấy từ `/api/auth/send-verify-email`.
- Thành công sẽ cập nhật `emailVerifiedAt` và xóa OTP đã dùng.
- Response thành công (HTTP 200):

```json
{
  "code": 200,
  "message": "Xác thực email thành công"
}
```

- Lỗi: 400 nếu request không hợp lệ, email đã xác thực hoặc OTP sai/hết hạn/đã dùng; 404 nếu email không tồn tại.

### 1.9 Quy tắc OTP và thứ tự gọi API

- Cấu hình hiện tại: OTP gồm 6 chữ số, hết hạn sau 300 giây (5 phút); thời gian chờ gửi lại là 60 giây cho mỗi email và mỗi mục đích.
- Gửi lại thành công sẽ thay thế OTP cũ của cùng email và mục đích. OTP chỉ dùng một lần; dùng xong vẫn phải chờ hết thời gian chờ nếu muốn gửi lại.
- **Đổi mật khẩu khi đã đăng nhập:** gọi `PUT /api/users/me/password` với access token và mật khẩu hiện tại; không cần OTP hoặc xác thực email trước đó. Xem mục 4.3.
- **Quên mật khẩu:** gọi `POST /api/auth/forgot-password` → lấy OTP mới trong email → gọi `POST /api/auth/reset-password`.
- **Xác thực email:** gọi `POST /api/auth/send-verify-email` → lấy OTP trong email → gọi `POST /api/auth/verify-email`.
- OTP của hai mục đích được lưu riêng biệt. Xác thực email thành công không cấp quyền đặt lại mật khẩu và không thay thế OTP quên mật khẩu.

---

## 2. Amenities

### 2.1 Lấy danh sách tiện ích
- Method: GET
- URL: /api/amenities
- Query params:
  - `page` (default: 0)
  - `size` (default: 10)
- Header:
```http
Authorization: Bearer <access_token>
```
- Response thành công:
```json
{
  "code": 200,
  "message": "Lấy danh sách tiện ích thành công",
  "data": {
    "currentPage": 0,
    "pageSize": 10,
    "totalPages": 2,
    "totalElements": 18,
    "data": [
      {
        "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "name": "Máy lạnh",
        "icon": "air-conditioner",
        "createdAt": "2026-10-20T10:15:00",
        "updatedAt": "2026-10-20T10:15:00"
      }
    ]
  }
}
```

### 2.2 Lấy tiện ích theo ID
- Method: GET
- URL: /api/amenities/{id}
- Header:
```http
Authorization: Bearer <access_token>
```
- Response thành công:
```json
{
  "code": 200,
  "message": "Lấy tiện ích thành công",
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Máy lạnh",
    "icon": "air-conditioner",
    "createdAt": "2026-10-20T10:15:00",
    "updatedAt": "2026-10-20T10:15:00"
  }
}
```

### 2.3 Tạo tiện ích
- Method: POST
- URL: /api/amenities
- Header:
```http
Authorization: Bearer <access_token>
```
- Request body:
```json
{
  "name": "Máy lạnh",
  "icon": "air-conditioner"
}
```
- Response thành công:
```json
{
  "code": 200,
  "message": "Tạo tiện ích thành công",
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Máy lạnh",
    "icon": "air-conditioner",
    "createdAt": "2026-10-20T10:15:00",
    "updatedAt": "2026-10-20T10:15:00"
  }
}
```

### 2.4 Cập nhật tiện ích
- Method: PUT
- URL: /api/amenities/{id}
- Header:
```http
Authorization: Bearer <access_token>
```
- Request body:
```json
{
  "name": "Máy lạnh mới",
  "icon": "air-conditioner-2"
}
```
- Response thành công:
```json
{
  "code": 200,
  "message": "Cập nhật tiện ích thành công",
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Máy lạnh mới",
    "icon": "air-conditioner-2",
    "createdAt": "2026-10-20T10:15:00",
    "updatedAt": "2026-10-21T09:40:00"
  }
}
```

### 2.5 Xóa tiện ích
- Method: DELETE
- URL: /api/amenities/{id}
- Header:
```http
Authorization: Bearer <access_token>
```
- Response thành công:
```json
{
  "code": 200,
  "message": "Xóa tiện ích thành công"
}
```

---

## 3. Room Types

### 3.1 Lấy danh sách loại phòng
- Method: GET
- URL: /api/room-types
- Query params:
  - `page` (default: 0)
  - `size` (default: 10)
- Header:
```http
Authorization: Bearer <access_token>
```
- Response thành công:
```json
{
  "code": 200,
  "message": "Lấy danh sách loại phòng thành công",
  "data": {
    "currentPage": 0,
    "pageSize": 10,
    "totalPages": 3,
    "totalElements": 24,
    "data": [
      {
        "id": "r1f0ef26-9a56-4f5e-8737-1d2d8b960ba3",
        "name": "Phòng Studio",
        "description": "Phòng 1 người, có bếp nhỏ",
        "parentId": null,
        "parentName": null,
        "createdAt": "2026-10-15T08:00:00",
        "updatedAt": "2026-10-15T08:00:00"
      }
    ]
  }
}
```

### 3.2 Lấy danh mục loại phòng kèm danh sách phòng
- Method: GET
- URL: /api/room-types/categories
- Header:
```http
Authorization: Bearer <access_token>
```
- Response thành công:
```json
{
  "code": 200,
  "message": "Lấy danh mục phòng thành công",
  "data": [
    {
      "id": "r1f0ef26-9a56-4f5e-8737-1d2d8b960ba3",
      "name": "Phòng Studio",
      "description": "Phòng 1 người, có bếp nhỏ",
      "parentId": null,
      "rooms": [
        {
          "id": "room-001",
          "price": 9500000,
          "areaM2": 28.5,
          "maxOccupants": 2,
          "addressLine": "12 Nguyễn Huệ",
          "provinceName": "Hà Nội",
          "wardName": "Phường Bến Nghé",
          "rentalStatus": "AVAILABLE"
        }
      ]
    }
  ]
}
```

### 3.3 Lấy loại phòng theo ID
- Method: GET
- URL: /api/room-types/{id}
- Header:
```http
Authorization: Bearer <access_token>
```
- Response thành công:
```json
{
  "code": 200,
  "message": "Lấy loại phòng thành công",
  "data": {
    "id": "r1f0ef26-9a56-4f5e-8737-1d2d8b960ba3",
    "name": "Phòng Studio",
    "description": "Phòng 1 người, có bếp nhỏ",
    "parentId": null,
    "parentName": null,
    "createdAt": "2026-10-15T08:00:00",
    "updatedAt": "2026-10-15T08:00:00"
  }
}
```

### 3.4 Tạo loại phòng
- Method: POST
- URL: /api/room-types
- Header:
```http
Authorization: Bearer <access_token>
```
- Request body:
```json
{
  "name": "Phòng Studio",
  "description": "Phòng 1 người, có bếp nhỏ",
  "parentId": null
}
```
- Response thành công:
```json
{
  "code": 200,
  "message": "Tạo loại phòng thành công",
  "data": {
    "id": "r1f0ef26-9a56-4f5e-8737-1d2d8b960ba3",
    "name": "Phòng Studio",
    "description": "Phòng 1 người, có bếp nhỏ",
    "parentId": null,
    "parentName": null,
    "createdAt": "2026-10-15T08:00:00",
    "updatedAt": "2026-10-15T08:00:00"
  }
}
```

### 3.5 Cập nhật loại phòng
- Method: PUT
- URL: /api/room-types/{id}
- Header:
```http
Authorization: Bearer <access_token>
```
- Request body:
```json
{
  "name": "Phòng Deluxe",
  "description": "Phòng có máy lạnh, giường đôi, view thành phố",
  "parentId": null
}
```
- Response thành công:
```json
{
  "code": 200,
  "message": "Cập nhật loại phòng thành công",
  "data": {
    "id": "r1f0ef26-9a56-4f5e-8737-1d2d8b960ba3",
    "name": "Phòng Deluxe",
    "description": "Phòng có máy lạnh, giường đôi, view thành phố",
    "parentId": null,
    "parentName": null,
    "createdAt": "2026-10-15T08:00:00",
    "updatedAt": "2026-10-21T09:40:00"
  }
}
```

### 3.6 Xóa loại phòng
- Method: DELETE
- URL: /api/room-types/{id}
- Header:
```http
Authorization: Bearer <access_token>
```
- Response thành công:
```json
{
  "code": 200,
  "message": "Xóa loại phòng thành công"
}
```

---

## 4. Rooms

Tất cả API ROOM yêu cầu `Authorization: Bearer <access_token>`. Chỉ `LANDLORD` được tạo/cập nhật/xóa phòng, ảnh và quản lý phòng của mình; admin và tenant không có quyền truy cập nhóm API rooms. Chủ trọ chỉ có thể xem/sửa/xóa phòng và ảnh do chính mình sở hữu. **Phòng do chủ trọ đăng lên là dữ liệu nội bộ — chưa hiển thị cho TENANT hoặc ADMIN xem; TENANT/ADMIN xem phòng qua Listings (mục 12) khi tin đã được duyệt.** Các response tuân theo `ApiResponse<T>`.

### 4.1 Lấy danh sách phòng và lọc
- Method: GET
- URL: `/api/rooms/mine` hoặc `/api/rooms/mine/filter`
- Quyền: `LANDLORD`; chỉ trả phòng thuộc tài khoản đang đăng nhập.
- Query params: `page` (mặc định 0), `size` (mặc định 10, 1–100), `roomTypeName`, `provinceId`, `wardId`, `rentalStatus` (`AVAILABLE`, `RENTED`, `MAINTENANCE`), `minPrice`, `maxPrice`, `amenityNames` (lặp tham số theo từng tên; phòng cần có đủ tiện ích đã chọn).
- Trả về `PageResponse<RoomResponse>`; lỗi 400 nếu phân trang/giá không hợp lệ hoặc `minPrice > maxPrice`.
- Ví dụ: `/api/rooms/mine/filter?page=0&size=12&provinceId=province-id&minPrice=2500000&maxPrice=4000000&roomTypeName=Studio&amenityNames=Wifi&amenityNames=Máy lạnh`

- Endpoint filter riêng (alias): `GET /api/rooms/filter` với cùng các tham số trên — cũng chỉ dành cho `LANDLORD`, trả phòng của chính chủ trọ đang đăng nhập.
- Ví dụ: `/api/rooms/filter?page=0&size=12&roomTypeName=Studio&amenityNames=Wifi`

### 4.2 Lấy danh sách phòng của tôi
- Method: GET
- URL: `/api/rooms/mine`
- Quyền: `LANDLORD`; trả về phân trang các phòng thuộc tài khoản đang đăng nhập.
- Query params: `page` (mặc định 0), `size` (mặc định 10, 1–100).

Endpoint lọc loại phòng: `GET /api/room-types/filter?name=studio&parentId=parent-id&page=0&size=10`. Endpoint lọc tiện ích: `GET /api/amenities/filter?name=wifi&page=0&size=10`. `name` và `parentId` đều tùy chọn. Cả hai yêu cầu đăng nhập.

### 4.3 Lấy chi tiết phòng
- Method: GET
- URL: `/api/rooms/{id}`
- Quyền: `LANDLORD`; chỉ chủ phòng mới xem được — kiểm tra ownership. Lỗi 403 nếu không sở hữu phòng; lỗi 404 nếu không tìm thấy.
- Response `data` gồm thông tin phòng, loại phòng, tỉnh/phường, tiện ích và ảnh.

### 4.4 Tạo phòng
- Method: POST
- URL: `/api/rooms`
- Content-Type: `multipart/form-data`. Gửi phần `room` kiểu `application/json` chứa JSON request body bên dưới; gửi thêm một hoặc nhiều file trong field `files` để tải ảnh ngay khi tạo phòng (không bắt buộc, tối đa 10 file). Response `RoomResponse.images` chứa các ảnh đã tải.
- Quyền: `LANDLORD`; `landlordId` được lấy từ access token.
- Request body:
```json
{
  "roomTypeId": "type-001",
  "price": 4500000,
  "electricityPrice": 3500,
  "waterPrice": 100000,
  "serviceFee": 150000,
  "defaultDeposit": 4500000,
  "areaM2": 25,
  "maxOccupants": 2,
  "propertyId": "property-001",
  "roomNumber": "A101",
  "floorNumber": 1,
  "rentalStatus": "AVAILABLE",
  "amenityIds": ["amenity-001", "amenity-002"]
}
```
- Ví dụ:
```bash
curl -X POST "http://localhost:8080/api/rooms" \
  -H "Authorization: Bearer <access_token>" \
  -F 'room={"roomTypeId":"type-001","price":4500000,"areaM2":25,"maxOccupants":2,"propertyId":"property-001","roomNumber":"A101","floorNumber":1};type=application/json' \
  -F "files=@room-1.jpg" -F "files=@room-2.jpg"
```
- `propertyId`, `roomNumber`, `roomTypeId`, `price`, `areaM2`, `maxOccupants` bắt buộc; giá/diện tích phải lớn hơn 0, số người ở ít nhất 1. Các phí không được âm. Property phải thuộc chủ trọ đang đăng nhập và có rentalMode BY_ROOM. `floorNumber` không âm khi được cung cấp. Số phòng duy nhất trong Property. `amenityIds` có thể bỏ qua hoặc gửi danh sách rỗng.

### 4.5 Cập nhật phòng
- Method: PUT
- URL: `/api/rooms/{id}`
- Quyền: chủ phòng (`LANDLORD`). Body có cùng cấu trúc và validation như tạo phòng; `amenityIds` là danh sách tiện ích mới (bỏ qua hoặc `[]` sẽ xóa các tiện ích đang gắn).
- Không chuyển Room sang Property khác. Lỗi 403 nếu không sở hữu phòng; 404 nếu phòng/loại phòng/tiện ích không tồn tại; 409 nếu số phòng bị trùng.

### 4.6 Xóa phòng
- Method: DELETE
- URL: `/api/rooms/{id}`
- Quyền: chủ phòng (`LANDLORD`). Xóa phòng và toàn bộ ảnh lưu trong thư mục của phòng. Không xóa đơn vị cuối cùng của Property (400), để giữ invariant WHOLE đúng một Room và BY_ROOM ít nhất một Room.

### 4.7 Tải ảnh phòng
- Method: POST
- URL: `/api/rooms/{id}/images`
- Content-Type: `multipart/form-data`; quyền: chủ phòng (`LANDLORD`).
- Form field `files`: một hoặc nhiều file JPEG/PNG hợp lệ; tối đa 10 ảnh mỗi request. Quy định kích thước/kiểm tra ảnh dùng chung với upload avatar (tối đa 5 MB, tối đa 4096×4096).
- Response `data` là danh sách ảnh đã tải lên (`id`, `url`, `sortOrder`, `createdAt`). Lỗi 400 nếu ảnh không hợp lệ/vượt số lượng; lỗi 502/503 nếu kho ảnh lỗi/chưa cấu hình.
- Ví dụ: `curl -X POST "http://localhost:8080/api/rooms/{id}/images" -H "Authorization: Bearer <access_token>" -F "files=@room.jpg"`

### 4.8 Xóa ảnh phòng
- Method: DELETE
- URL: `/api/rooms/{id}/images/{imageId}`
- Quyền: chủ phòng (`LANDLORD`); xóa bản ghi ảnh và ảnh trên kho lưu trữ.
- Lỗi 403 nếu không sở hữu phòng; 404 nếu phòng hoặc ảnh không tồn tại/ảnh không thuộc phòng.

`RoomResponse` gồm: `id`, `propertyId`, `roomNumber`, `floorNumber`, `landlordId`, `roomTypeId`, `roomTypeName`, `price`, `electricityPrice`, `waterPrice`, `serviceFee`, `defaultDeposit`, `areaM2`, `maxOccupants`, `provinceId`, `provinceName`, `wardId`, `wardName`, `addressLine`, `latitude`, `longitude`, `rentalStatus`, `amenities`, `images`, `createdAt`, `updatedAt`. Tạo phòng nhận multipart gồm phần JSON `room` và field file `files` tùy chọn; endpoint tải ảnh riêng vẫn dùng được sau khi tạo.

Các trường chủ trọ và địa chỉ trong response được lấy qua `Room.property`; request Room không nhận địa chỉ riêng. Tạo cơ sở và phòng lần đầu dùng `POST /api/properties` (xem [Property API](#property-api)).

---

## 5. Users

Tất cả endpoint trong nhóm này yêu cầu header `Authorization: Bearer <access_token>`.
Các API `/me` lấy ID người dùng từ access token.

### 5.1 Lấy hồ sơ cá nhân

- Method: GET
- URL: /api/users/me
- URL thay thế: /api/users/get/me
- Response thành công (HTTP 200):

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "id": "user-001",
    "email": "tenant@gmail.com",
    "phone": "0901234567",
    "fullName": "Nguyễn Văn A",
    "avatarUrl": "https://res.cloudinary.com/demo/image/upload/avatar.png",
    "citizenId": "012345678901",
    "status": "ACTIVE",
    "emailVerifiedAt": null,
    "phoneVerifiedAt": null,
    "createdAt": "2026-10-21T08:00:00",
    "updatedAt": "2026-10-22T09:00:00",
    "roles": ["TENANT"]
  }
}
```

Đây là cấu trúc `UserResponse` được dùng cho các API trả về một người dùng bên dưới. Các thông tin chưa có có thể là `null`.

### 5.2 Cập nhật hồ sơ cá nhân

- Method: PUT hoặc PATCH
- URL: /api/users/me
- URL thay thế (cả PUT và PATCH): /api/users/update/me
- Content-Type: application/json
- Request body:

```json
{
  "fullName": "Nguyễn Văn B",
  "phone": "0912345678",
  "citizenId": "012345678902"
}
```

- `fullName`: tối đa 100 ký tự, không được chỉ chứa khoảng trắng.
- `phone`: tiền tố `0`, `84` hoặc `+84`, theo sau là 9 chữ số; gửi chuỗi rỗng để xóa.
- `citizenId`: tối đa 20 ký tự; gửi chuỗi rỗng hoặc chỉ khoảng trắng để xóa.
- Bỏ qua trường hoặc gửi `null` để giữ nguyên, áp dụng cho cả PUT và PATCH.
- Request không hỗ trợ thay đổi email, vai trò hoặc trạng thái. Thay đổi số điện thoại sẽ xóa trạng thái xác minh số điện thoại.
- Response: HTTP 200, `code: 200`, `message: "Success"`, `data` là `UserResponse` sau cập nhật.
- Lỗi: 400 khi dữ liệu không hợp lệ hoặc trùng số điện thoại/CCCD; 404 nếu không tìm thấy người dùng.

### 5.3 Đổi mật khẩu

- Method: PUT
- URL: /api/users/me/password
- Yêu cầu đã đăng nhập, dùng access token và mật khẩu hiện tại; không cần OTP hoặc gọi API xác thực email.
- Nếu quên mật khẩu hiện tại, dùng luồng `/api/auth/forgot-password` → `/api/auth/reset-password` ở mục 1.5–1.6.
- Content-Type: application/json
- Request body:

```json
{
  "currentPassword": "Abc12345",
  "newPassword": "NewPassword123",
  "confirmPassword": "NewPassword123"
}
```

- Cả ba trường đều bắt buộc. `currentPassword` tối đa 100 ký tự; `newPassword` từ 8–72 ký tự và tối đa 72 byte UTF-8; `confirmPassword` tối đa 72 ký tự.
- Mật khẩu mới phải khác mật khẩu hiện tại và khớp xác nhận.
- Sau khi đổi thành công, access token cũ bị vô hiệu hóa, các refresh token của người dùng bị thu hồi; cần đăng nhập lại.
- Response thành công (HTTP 200):

```json
{
  "code": 200,
  "message": "Đổi mật khẩu thành công. Vui lòng đăng nhập lại."
}
```

- Lỗi: 400 khi mật khẩu hiện tại sai, xác nhận không khớp, mật khẩu mới trùng mật khẩu cũ hoặc vượt giới hạn.

### 5.4 Tải lên hoặc thay thế avatar

- Method: PUT hoặc POST
- URL: /api/users/me/avatar
- Content-Type: multipart/form-data
- Form field: `file` (bắt buộc, kiểu file).
- Chỉ chấp nhận JPEG/PNG hợp lệ, tối đa 5 MB, kích thước mỗi chiều từ 1 đến 4096 pixel.
- Ví dụ:

```bash
curl -X PUT "http://localhost:8080/api/users/me/avatar" \
  -H "Authorization: Bearer <access_token>" \
  -F "file=@avatar.png"
```

- Response: HTTP 200, `code: 200`, `message: "Success"`, `data` là `UserResponse` với `avatarUrl` mới.
- Lỗi: 400 nếu thiếu file/ảnh không hợp lệ; 413 nếu vượt giới hạn multipart của server; 502 nếu upload thất bại; 503 nếu chưa cấu hình Cloudinary.

### 5.5 Xóa avatar

- Method: DELETE
- URL: /api/users/me/avatar
- Không có request body.
- Response: HTTP 200, `code: 200`, `message: "Success"`, `data` là `UserResponse` với `avatarUrl` được đặt về `null`.

### 5.6 Lấy danh sách người dùng

- Method: GET
- URL: /api/users
- Quyền: ADMIN.
- Query params:
  - `q`: không bắt buộc, tối đa 100 ký tự; tìm theo họ tên, email hoặc số điện thoại.
  - `status`: không bắt buộc, `ACTIVE`, `INACTIVE` hoặc `BLOCKED`.
  - `page`: mặc định 0, phải >= 0.
  - `size`: mặc định 20, từ 1 đến 100.
- Sắp xếp theo thời gian tạo giảm dần, sau đó theo ID.
- Ví dụ: `GET /api/users?q=tenant&status=ACTIVE&page=0&size=20`.
- Response thành công (HTTP 200; mỗi phần tử trong `data.data` là `UserResponse` như mục 4.1):

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "currentPage": 0,
    "pageSize": 20,
    "totalPages": 0,
    "totalElements": 0,
    "data": []
  }
}
```

- Lỗi: 400 khi tham số tìm kiếm, phân trang hoặc trạng thái không hợp lệ.

### 5.7 Lấy người dùng theo ID

- Method: GET
- URL: /api/users/{id}
- Quyền: ADMIN.
- Path param: `id` là ID người dùng.
- Response: HTTP 200, `code: 200`, `message: "Success"`, `data` là `UserResponse` như mục 4.1.
- Lỗi: 404 nếu không tìm thấy người dùng.

### 5.8 Tạo người dùng

- Method: POST
- URL: /api/users
- Quyền: ADMIN.
- Content-Type: application/json
- Request body:

```json
{
  "email": "landlord@gmail.com",
  "password": "Abc12345",
  "fullName": "Trần Văn B",
  "phone": "0912345678",
  "citizenId": "012345678902",
  "roleCode": "LANDLORD"
}
```

- `email`: bắt buộc, email hợp lệ, tối đa 255 ký tự; được bỏ khoảng trắng hai đầu và chuyển về chữ thường.
- `password`: bắt buộc, 8–72 ký tự và tối đa 72 byte UTF-8.
- `fullName`: bắt buộc, không chỉ chứa khoảng trắng, tối đa 100 ký tự.
- `phone`, `citizenId`: không bắt buộc, ràng buộc giống mục 4.2.
- `roleCode`: bắt buộc, `TENANT`, `LANDLORD` hoặc `ADMIN`; vai trò phải tồn tại trong hệ thống.
- Tài khoản mới có trạng thái `ACTIVE`; API này không trả token.
- Response: **HTTP 201**, `code: 201`, `message: "Created"`, `data` là `UserResponse` vừa tạo.
- Lỗi: 400 nếu dữ liệu không hợp lệ hoặc trùng email/số điện thoại/CCCD; 404 nếu vai trò không tồn tại.

### 5.9 Cập nhật người dùng

- Method: PUT hoặc PATCH
- URL: /api/users/{id}
- Quyền: ADMIN.
- Content-Type: application/json
- Request body:

```json
{
  "email": "tenant.new@gmail.com",
  "fullName": "Nguyễn Văn A",
  "phone": "0901234567",
  "citizenId": "012345678901",
  "status": "BLOCKED"
}
```

- Tất cả trường không bắt buộc; bỏ qua hoặc gửi `null` để giữ nguyên, cả PUT và PATCH.
- `email`: email hợp lệ, không chỉ chứa khoảng trắng, tối đa 255 ký tự.
- `fullName`, `phone`, `citizenId`: ràng buộc giống mục 4.2.
- `status`: `ACTIVE`, `INACTIVE` hoặc `BLOCKED`.
- Request không hỗ trợ thay đổi mật khẩu hoặc vai trò.
- Thay đổi email xóa trạng thái xác minh email. Thay đổi email hoặc trạng thái vô hiệu hóa access token cũ và thu hồi refresh token của người dùng.
- Không được đặt trạng thái tài khoản ADMIN thành `INACTIVE` hoặc `BLOCKED`.
- Response: HTTP 200, `code: 200`, `message: "Success"`, `data` là `UserResponse` sau cập nhật.
- Lỗi: 400 nếu dữ liệu không hợp lệ/trùng thông tin; 404 nếu không tìm thấy; 409 nếu vô hiệu hóa tài khoản ADMIN.

### 5.10 Xóa người dùng

- Method: DELETE
- URL: /api/users/{id}
- Quyền: ADMIN.
- Không có request body.
- Không được xóa tài khoản ADMIN hoặc người dùng còn dữ liệu nghiệp vụ liên quan; nên khóa tài khoản còn dữ liệu liên quan.
- Response thành công (HTTP 200):

```json
{
  "code": 200,
  "message": "Deleted"
}
```

- Lỗi: 404 nếu không tìm thấy; 409 nếu là tài khoản ADMIN hoặc còn dữ liệu liên quan.

---

## 6. Administrative Units (Tỉnh/Thành phố, Phường/Xã)

GET danh sách tỉnh/phường là public; thao tác ghi yêu cầu token và quyền tương ứng.
`provinceId` và `id` là ID bản ghi trong hệ thống, khác với mã hành chính `code`.

Response tỉnh và phường có`centroidLat`, `centroidLng` (number hoặc null), tương ứng các cột DB `centroid_lat`, `centroid_lng` trong V1 hiện tại. Hai giá trị là tọa độ đại diện WGS84 từ tinhthanhpho.com, không phải vị trí hiện tại của người dùng. Script setup demo đồng bộ sau V1 rồi seed V2 theo mã hành chính; xem README backend.

### 6.1 Lấy danh sách tỉnh/thành phố

- Method: GET
- URL: /api/provinces
- Không có query params, không phân trang; sắp xếp theo mã tỉnh/thành phố tăng dần.
- Response thành công (HTTP 200):

```json
{
  "code": 200,
  "message": "Success",
  "data": [
    {
      "id": "province-001",
      "code": "01",
      "name": "Thành phố Hà Nội"
    }
  ]
}
```

### 6.2 Lấy danh sách phường/xã

- Method: GET
- URL: /api/wards
- Query params:
  - `provinceId`: không bắt buộc, lọc theo ID tỉnh/thành phố.
  - `q`: không bắt buộc, tối đa 100 ký tự; tìm theo tên hoặc mã phường/xã.
  - `page`: mặc định 0, phải >= 0.
  - `size`: mặc định 20, từ 1 đến 100.
- Sắp xếp theo mã phường/xã tăng dần.
- Ví dụ: `GET /api/wards?provinceId=province-001&q=Ba&page=0&size=20`.
- Response thành công (HTTP 200):

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "currentPage": 0,
    "pageSize": 20,
    "totalPages": 1,
    "totalElements": 1,
    "data": [
      {
        "id": "ward-001",
        "code": "00004",
        "name": "Phường Ba Đình",
        "provinceId": "province-001",
        "provinceCode": "01",
        "provinceName": "Thành phố Hà Nội"
      }
    ]
  }
}
```

- Lỗi: 400 nếu phân trang hoặc từ khóa không hợp lệ; 404 nếu `provinceId` được cung cấp nhưng không tồn tại.

### 6.3 Lấy phường/xã theo ID

- Method: GET
- URL: /api/wards/{id}
- Response thành công (HTTP 200):

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "id": "ward-001",
    "code": "00004",
    "name": "Phường Ba Đình",
    "provinceId": "province-001",
    "provinceCode": "01",
    "provinceName": "Thành phố Hà Nội"
  }
}
```

- Đây là cấu trúc `WardResponse` dùng cho API tạo/cập nhật bên dưới.
- Lỗi: 404 nếu phường/xã không tồn tại.

### 6.4 Tạo phường/xã

- Method: POST
- URL: /api/wards
- Quyền: ADMIN hoặc authority `WARD_WRITE`.
- Content-Type: application/json
- Request body:

```json
{
  "code": "00004",
  "name": "Phường Ba Đình",
  "provinceId": "province-001"
}
```

- Cả ba trường đều bắt buộc.
- `code`: chuỗi đúng 5 chữ số, giữ số 0 ở đầu; không được trùng mã phường/xã khác.
- `name`: không chỉ chứa khoảng trắng, tối đa 100 ký tự.
- `provinceId`: ID tỉnh/thành phố đã tồn tại, tối đa 36 ký tự.
- Response: **HTTP 201**, `code: 201`, `message: "Created"`, `data` là `WardResponse` vừa tạo.
- Lỗi: 400 nếu validation thất bại; 404 nếu tỉnh/thành phố không tồn tại; 409 nếu mã bị trùng.

### 6.5 Cập nhật phường/xã

- Method: PUT
- URL: /api/wards/{id}
- Quyền: ADMIN hoặc authority `WARD_WRITE`.
- Content-Type: application/json
- Request body:

```json
{
  "code": "00004",
  "name": "Phường Ba Đình",
  "provinceId": "province-001"
}
```

- Cả ba trường đều bắt buộc và có ràng buộc như mục 5.4.
- Response: HTTP 200, `code: 200`, `message: "Success"`, `data` là `WardResponse` sau cập nhật.
- Lỗi: 400 nếu validation thất bại; 404 nếu phường/xã hoặc tỉnh/thành phố không tồn tại; 409 nếu mã trùng bản ghi khác.

### 6.6 Xóa phường/xã

- Method: DELETE
- URL: /api/wards/{id}
- Quyền: ADMIN hoặc authority `WARD_DELETE`.
- Không có request body.
- Response thành công (HTTP 200):

```json
{
  "code": 200,
  "message": "Deleted"
}
```

- Lỗi: 404 nếu không tồn tại; 409 nếu phường/xã đang được tham chiếu bởi phòng hoặc hồ sơ người thuê.
- Đồng bộ dữ liệu hành chính được thực hiện bằng command chạy ứng dụng; hiện chưa có REST API đồng bộ hoặc API tạo/sửa/xóa tỉnh/thành phố.

---

## 7. Quyền truy cập
Các API dưới đây cần quyền thích hợp:
- Public: `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/forgot-password`, `POST /api/auth/reset-password`, `POST /api/auth/send-verify-email`, `POST /api/auth/verify-email`.
- `POST /api/auth/logout`: yêu cầu đăng nhập.
- `GET /api/amenities`, `GET /api/room-types`, `GET /api/room-types/categories`, `GET /api/amenities/{id}`, `GET /api/room-types/{id}`, `GET /api/amenities/filter`, `GET /api/room-types/filter`: yêu cầu đăng nhập (`isAuthenticated()`).
- `GET /api/rooms/mine`, `GET /api/rooms/mine/filter`, `GET /api/rooms/filter`, `GET /api/rooms/{id}`, `POST/PUT/DELETE /api/rooms`, và các API ảnh phòng: yêu cầu vai trò `LANDLORD`; thao tác đọc chi tiết và sửa/xóa còn kiểm tra chủ sở hữu phòng. `ADMIN` và `TENANT` không được phép dùng các API quản lý ROOM. **TENANT/ADMIN xem phòng qua Listings (mục 12), không qua API rooms.**
- `POST/PUT/DELETE` amenity và room-type: chỉ yêu cầu vai trò `LANDLORD`; `ADMIN` không được phép thao tác quản lý. Các API GET danh sách/chi tiết amenity và room-type vẫn yêu cầu đăng nhập.
- API hồ sơ cá nhân, mật khẩu, avatar và các URL thay thế: yêu cầu đăng nhập.
- Danh sách, chi tiết theo ID, tạo, cập nhật và xóa người dùng: vai trò ADMIN.
- GET provinces/wards: yêu cầu đăng nhập.
- POST/PUT wards: authority `WARD_WRITE` hoặc vai trò ADMIN.
- DELETE wards: authority `WARD_DELETE` hoặc vai trò ADMIN.
- Thiếu hoặc sai access token: HTTP 401. Đã đăng nhập nhưng thiếu quyền: HTTP 403.

---

## Error response mẫu

Các lỗi được xử lý bởi `GlobalExceptionHandler` trả về `ApiResponse`, với HTTP status tương ứng trường `code`. Trường `data` bị bỏ khi không có dữ liệu. Ví dụ lỗi validation (HTTP 400):
```json
{
  "code": 400,
  "message": "Email không đúng định dạng"
}
```
Ví dụ thiếu quyền (HTTP 403):
```json
{
  "code": 403,
  "message": "Bạn không có quyền truy cập tài nguyên này"
}
```

Ví dụ OTP không hợp lệ, đã dùng hoặc hết hạn (HTTP 400):

```json
{
  "code": 400,
  "message": "Mã OTP không đúng hoặc đã hết hạn"
}
```

Ví dụ gửi lại OTP quá sớm (HTTP 429):

```json
{
  "code": 429,
  "message": "Gửi OTP quá nhiều lần, vui lòng thử lại sau"
}
```

Các mã lỗi cần xử lý thêm cho Authentication, Users và Administrative Units:

- 400: request không hợp lệ, trùng email/số điện thoại/CCCD, lỗi đổi/đặt lại mật khẩu, email đã xác thực hoặc OTP không hợp lệ/đã dùng/hết hạn.
- 404: không tìm thấy người dùng, vai trò, tỉnh/thành phố hoặc phường/xã.
- 409: mã hành chính trùng, dữ liệu đang được tham chiếu hoặc tài khoản ADMIN được bảo vệ.
- 413: upload vượt giới hạn multipart.
- 429: gửi OTP quá nhiều lần trong thời gian chờ.
- 502: upload avatar lên Cloudinary thất bại.
- 503: Cloudinary chưa được cấu hình.
- 500: lỗi hệ thống chưa được xử lý cụ thể.

## 8. Gói dịch vụ và bảng giá

Các endpoint yêu cầu Bearer access token. ADMIN quản lý danh mục; LANDLORD chỉ đọc gói đang bán. TENANT không có quyền truy cập nhóm API này.

### 8.1. Danh sách và chi tiết

- GET `/api/service-packages?page=0&size=10&includeInactive=false`
- GET `/api/service-packages/{id}?includeInactive=false`
- Chỉ ADMIN được đặt `includeInactive=true` để xem cả gói ngừng bán hoặc chưa có giá khả dụng.
- Mặc định chỉ trả gói active có ít nhất một giá active, cùng gói Free mặc định.
- `page` bắt đầu từ 0; size được giới hạn trong 1–100. Danh sách dùng `ApiResponse<PageResponse<ServicePackageResponse>>`, sắp xếp tên và ID.
- Chi tiết gói không khả dụng trả 404 khi không bật chế độ xem toàn bộ của admin.

Dữ liệu một gói có các trường: id, code, name, maxListings, highPrioritySlots, lowPrioritySlots, defaultPriorityLevel, hasStats, active, createdAt, updatedAt. `maxListings=null` biểu thị không giới hạn; không dùng 0 để biểu thị Business.

### 8.2. Admin tạo hoặc cập nhật gói

- POST `/api/service-packages`
- PUT `/api/service-packages/{id}`
- Cả hai yêu cầu ADMIN; PUT gửi đủ cấu hình, không phải cập nhật từng trường.

Ví dụ Gói 2:

```json
{
  "code": "PACKAGE_2",
  "name": "Gói 2",
  "maxListings": 20,
  "highPrioritySlots": 5,
  "lowPrioritySlots": 10,
  "defaultPriorityLevel": 0,
  "hasStats": true,
  "active": true
}
```

- Code bắt buộc, tối đa 50 ký tự chữ/số/gạch ngang/gạch dưới, được chuẩn hóa thành chữ hoa và không trùng không phân biệt hoa thường.
- Tên bắt buộc, tối đa 100 ký tự.
- Giới hạn tin có thể null, nếu có phải không âm. Suất cao/thấp bắt buộc và không âm; tổng không vượt giới hạn hữu hạn.
- Mức mặc định chỉ 0 (thường) hoặc 1 (thấp). Khi mức mặc định là 1 thì lowPrioritySlots phải bằng 0 vì mọi tin còn lại đã tự có mức thấp.
- Business có maxListings=null, highPrioritySlots=20, lowPrioritySlots=0, defaultPriorityLevel=1, hasStats=true.
- Free giữ code FREE, tối đa 3 tin, không có slot/thống kê, mặc định 0 và luôn active; admin có thể sửa tên hiển thị nhưng không đổi cấu hình mặc định này.
- Tạo gói trả phí chưa làm gói xuất hiện với chủ trọ; cần thêm ít nhất một mức giá active.
- Cập nhật danh mục không sửa snapshot của đơn hoặc kỳ đã mua, bao gồm kỳ tương lai đã trả tiền.

Thành công HTTP 200, dữ liệu nằm trong data của ApiResponse.

### 8.3. Bật hoặc ngừng bán gói

PATCH `/api/service-packages/{id}/status`, chỉ ADMIN:

```json
{"active": false}
```

Gửi true để bật lại. Không hỗ trợ DELETE gói trong nhóm API này. Ngừng bán không xóa dữ liệu hoặc thay đổi quyền của kỳ đã mua. Không được ngừng gói Free.

### 8.4. Giá theo thời hạn

- GET `/api/service-packages/{packageId}/prices?includeInactive=false`: ADMIN/LANDLORD, chỉ ADMIN được yêu cầu cả giá ngừng bán.
- POST `/api/service-packages/{packageId}/prices`: ADMIN thêm giá.
- PUT `/api/service-packages/{packageId}/prices/{priceId}`: ADMIN cập nhật giá.
- PATCH `/api/service-packages/{packageId}/prices/{priceId}/status`: ADMIN bật/ngừng bán thời hạn, body giống mục 8.3.
- GET trả danh sách tối đa bốn lựa chọn, sắp xếp số tháng tăng dần. Mỗi phần tử gồm id, packageId, durationMonths, price, active, createdAt, updatedAt.
- LANDLORD không đọc được giá của gói ngừng bán; giá không active được lọc khỏi danh sách.
- Không tạo giá trả phí cho Free; không xóa mức giá đã tạo. Muốn bán lại một thời hạn đã ngừng thì cập nhật/bật lại bản ghi đó.

Body POST/PUT:

```json
{
  "durationMonths": 3,
  "price": 250000,
  "active": true
}
```

Chỉ nhận 1/3/6/12 tháng; mỗi gói có một giá cho mỗi thời hạn. Giá không âm, tối đa 10 chữ số nguyên và 2 chữ số thập phân. priceId phải thuộc packageId trên URL; không được dùng URL của gói khác để sửa hoặc bật/tắt giá.

## 9. Danh mục Boost mua riêng

Phân quyền và includeInactive giống nhóm gói dịch vụ. Danh mục Boost quy định số suất cao/thấp, giá và thời hạn; luồng mua và phân bổ được mô tả ở mục 13.

- GET `/api/boost-packages?page=0&size=10&includeInactive=false`: danh sách phân trang, sắp xếp số tháng, code rồi ID.
- GET `/api/boost-packages/{id}?includeInactive=false`: chi tiết.
- POST `/api/boost-packages`: ADMIN tạo.
- PUT `/api/boost-packages/{id}`: ADMIN cập nhật đủ cấu hình.
- PATCH `/api/boost-packages/{id}/status`: ADMIN bật/ngừng bán, body `{"active":false}`.
- Không hỗ trợ DELETE. LANDLORD chỉ thấy gói active có thời hạn tháng hợp lệ.

Body POST/PUT:

```json
{
  "code": "BOOST_HIGH_3M",
  "name": "Boost 3 tháng",
  "highPrioritySlots": 3,
  "lowPrioritySlots": 5,
  "durationMonths": 3,
  "price": 150000,
  "active": true
}
```

Code/tên/giá kiểm tra giống gói dịch vụ. Chỉ nhận 1/3/6 tháng. highPrioritySlots và lowPrioritySlots là số nguyên không âm, bắt buộc và tổng lớn hơn 0. Response có id, code, name, highPrioritySlots, lowPrioritySlots, durationMonths, price, active, createdAt, updatedAt. Không còn trường priorityLevel trong danh mục Boost.

Boost cũ chưa có durationMonths phải được admin cập nhật thời hạn trước khi bật bán. Sửa danh mục không thay đổi snapshot Boost đã mua.

Các lỗi của nhóm 8–9:

- 400: dữ liệu thiếu/sai, giá âm/quá độ chính xác, thời hạn không hỗ trợ, tổng slot vượt giới hạn hoặc mức mặc định không phù hợp.
- 401: chưa xác thực.
- 403: không phải ADMIN khi ghi, TENANT truy cập hoặc LANDLORD yêu cầu includeInactive=true.
- 404: gói/giá không tồn tại, không khả dụng cho chế độ đọc hiện tại hoặc priceId không thuộc packageId.
- 409: mã gói trùng, thời hạn đã có giá hoặc thao tác vi phạm bảo vệ gói Free.

## 10. Đăng ký, gia hạn và đổi gói dịch vụ

Nhóm API yêu cầu Bearer token. Các endpoint của LANDLORD lấy chủ sở hữu từ user ID trong principal; không nhận landlordId từ client. Response dùng ApiResponse như các nhóm trên. Danh sách phân trang dùng page từ 0, size từ 1–100, mới nhất trước rồi ID giảm dần.

### 10.1. Tạo đơn

POST `/api/subscriptions/orders`, quyền LANDLORD:

```json
{
  "priceOptionId": "uuid-cua-muc-gia",
  "type": "PURCHASE"
}
```

- `priceOptionId` xác định cả gói và thời hạn 1/3/6/12 tháng; gói và mức giá phải đang bán. Không mua gói Free bằng endpoint này.
- `type`: PURCHASE (mua mới khi không có gói trả phí còn hạn), RENEW (gia hạn cùng gói), CHANGE (nâng gói áp dụng ngay sau thanh toán; hạ gói hoặc đổi ngang áp dụng từ kỳ sau).
- RENEW sau khi hết hạn được ghi nhận thành PURCHASE để tạo subscription mới sau thanh toán. Khi còn hạn, RENEW chỉ nhận mức giá của chính gói đang dùng.
- Server lấy giá, thời hạn và toàn bộ quyền lợi để lưu snapshot. Client không đặt giá, quyền lợi, chủ sở hữu, hạn gói hay trạng thái.
- Đơn có trạng thái PENDING_PAYMENT, thời hạn chờ thanh toán tối đa 15 phút. Đơn nâng gói không được vượt hạn gói hiện tại. Ngoại lệ: nâng gói có phí chênh lệch bằng 0 được APPLIED ngay lúc tạo đơn, không checkout VNPay.
- Chỉ có một yêu cầu CHANGE đang mở trong các trạng thái PENDING_PAYMENT, AWAITING_ADMIN_CONFIRMATION hoặc SCHEDULED. Đơn chưa trả tiền quá hạn sẽ EXPIRED và giải phóng yêu cầu đang mở khi chủ trọ thao tác tiếp.
- Có SCHEDULED thì chặn tạo đơn gia hạn/đăng ký/đổi gói khác. Khoản thanh toán gia hạn của đơn cũ về sau SCHEDULED được giữ để đối soát (REQUIRES_REVIEW), không kéo dài gói hiện tại hoặc dời lịch đã xác nhận.

Dữ liệu đơn trả về: id, landlordId, packageId, priceOptionId, subscriptionId, previousSubscriptionId, type, status, durationMonths, price, immediateUpgrade, rights, expiresAt, effectiveAt, endAt, appliedAt, confirmedAt, createdAt. Những mốc và ID chưa được xác định trả null. `rights` là snapshot gồm packageCode, packageName, maxListings, highPrioritySlots, lowPrioritySlots, defaultPriorityLevel, hasStats.

### 10.2. Quyền hiện tại và lịch sử

Các endpoint sau chỉ dành cho LANDLORD và chỉ trả dữ liệu của người đang đăng nhập:

- GET `/api/subscriptions/current`: quyền và kỳ hiện tại.
- GET `/api/subscriptions/history?page=0&size=10`: lịch sử subscription; mỗi phần tử có id, packageId, status, startAt, endAt, createdAt.
- GET `/api/subscriptions/orders?page=0&size=10`: lịch sử đơn và trạng thái thanh toán/áp dụng.
- GET `/api/subscriptions/orders/{id}`: chi tiết đơn của mình; đơn người khác trả 404.
- GET `/api/subscriptions/periods?page=0&size=10`: các kỳ đã mua, bao gồm kỳ tương lai; mỗi phần tử có id, subscriptionId, orderId, periodNumber, startAt, endAt, rights.

Response current gồm subscriptionId, free, canSubmitListings, subscriptionEndsAt, currentPeriod, nextPeriod, rights, scheduledChange:

- subscriptionEndsAt là hạn tổng đã gia hạn, còn currentPeriod.endAt là lúc quyền của kỳ đang hưởng kết thúc.
- nextPeriod là kỳ gia hạn tiếp theo trong cùng subscription, chưa được áp dụng trước startAt; scheduledChange là đơn đổi gói đã xác nhận, có effectiveAt/endAt và snapshot riêng.
- Người chưa từng có subscription trả phí được Free (3 tin, không ưu tiên/thống kê). Người có gói trả phí hết hạn nhận free=false, canSubmitListings=false và hạn mức 0; không tự dùng lại Free.
- canSubmitListings biểu thị quyền theo thời hạn gói, chưa tính số tin đang chiếm quota. Kiểm tra quota/tin đăng được tích hợp ở phần triển khai tin đăng kế tiếp.
- Các mốc sử dụng giờ Asia/Ho_Chi_Minh, tháng theo lịch. Khoảng hiệu lực là [startAt, endAt), đúng endAt thì kỳ cũ mất quyền.
- Khi truy cập các API hiện tại/đơn/lịch sử subscription hoặc thực hiện giao dịch, hệ thống đồng bộ hết hạn và kích hoạt SCHEDULED đến hạn trong transaction; không cần chờ cron để lấy quyền mới. Kích hoạt trễ giữ nguyên ngày bắt đầu/kết thúc đã xác nhận.

### 10.3. Nâng gói ngay, hạ gói từ kỳ sau

Client tiếp tục gửi `type=CHANGE` và `priceOptionId`; server tự phân loại theo snapshot quyền lợi hiện tại, không theo tên/mã gói hoặc chỉ theo giá.

- `immediateUpgrade=true`: gói đích không giảm bất kỳ quyền nào của các kỳ đã mua còn hạn và tăng ít nhất một quyền hiện tại (giới hạn tin, suất cao, quyền ưu tiên thấp hoặc thống kê). NULL maxListings là không giới hạn; defaultPriorityLevel=1 tương đương ưu tiên thấp cho mọi tin.
- Nâng gói có hiệu lực ngay sau khi backend xác nhận khoản thanh toán hợp lệ qua IPN/QueryDr, không cần admin. Giữ nguyên ngày hết hạn tổng đã mua, kể cả các kỳ gia hạn trả trước. Subscription cũ chuyển CANCELLED để ngừng cấp quyền; lịch sử đơn/kỳ cũ vẫn giữ nguyên.
- Phí nâng gói = max(0, giá trị gói đích cho thời gian còn lại - giá trị chưa dùng của các kỳ cũ), làm tròn lên VND nguyên. Giá trị gói đích dùng đơn giá mỗi tháng (giá priceOptionId chia durationMonths), nhân tổng billing_months chưa dùng. Mỗi kỳ quy đổi phần tháng còn lại theo tỷ lệ số giây chưa dùng trên độ dài kỳ gốc; nhờ đó tháng 28/30/31 ngày và kỳ 1/3/6/12 tháng không bị tính lệch. Giá trị cũ dùng entitlement_value lưu tại kỳ, tính cùng tỷ lệ thời gian chưa dùng; không dùng giá danh mục hiện tại. Hai giá trị được làm tròn đến 2 chữ số thập phân trước khi lấy chênh lệch và làm tròn lên VND nguyên.
- Đơn lưu cố định giá báo trong cửa sổ thanh toán. Nếu gói nguồn hoặc ngày hết hạn đã thay đổi trước checkout, checkout bị chặn; nếu thanh toán đã thu rồi điều kiện thay đổi, đơn REQUIRES_REVIEW. Không tự thu thêm hoặc áp dụng một báo giá lỗi thời.
- Phí bằng 0: server áp dụng ngay khi tạo đơn và trả APPLIED; frontend không gọi checkout. Giá trị đã trả còn lại được bảo toàn làm cơ sở tính cho lần nâng tiếp theo, không tự hoàn tiền.
- Nâng gói không reset quota: 10 tin đang chiếm chỗ trong gói 10 tin, nâng lên gói 20 tin thì còn 10 chỗ. Các lựa chọn ưu tiên hiện tại được chuyển sang kỳ mới; nếu mức thấp trở thành mặc định thì giải phóng lựa chọn thấp riêng. Tin/Boost giữ nguyên hạn và publishedAt.
- `immediateUpgrade=false`: hạ gói, đổi ngang hoặc đổi có giảm một quyền lợi. Sau thanh toán tự tạo SCHEDULED tại hạn tổng của gói hiện tại, không cần admin. Nếu gói cũ đã hết hạn lúc áp dụng thì bắt đầu ngay tại lúc cấp quyền. Thu đủ giá kỳ mới, quyền cũ giữ nguyên đến hạn.
- RENEW cùng gói vẫn nối thêm kỳ tại endAt tổng mới nhất, không đổi quyền trước khi kỳ mới bắt đầu.

API GET `/api/subscriptions/orders/awaiting-confirmation` và POST `/api/subscriptions/orders/{id}/confirm` giữ lại để xử lý đơn cũ AWAITING_ADMIN_CONFIRMATION từ trước thay đổi này. Đơn đã APPLIED/SCHEDULED được xác nhận lặp mà không tạo thêm kỳ. Đơn CHANGE cũ chưa trả tiền vẫn giữ báo giá cũ, không được tự chuyển thành báo giá nâng gói theo tỷ lệ; nên tạo đơn mới để dùng luồng mới.

Mọi thao tác cùng chủ trọ khóa chung user để tuần tự hóa nâng/gia hạn/đổi gói. Callback lặp không tạo thêm kỳ hoặc hóa đơn.

Luồng VNPay được mô tả ở mục 11. Không có endpoint cho client tự đánh dấu đã thanh toán. Service nội bộ chỉ áp dụng khi có Payment SUCCESS đúng chủ sở hữu, loại và số tiền, không nằm trong nhóm khoản thu cần đối soát. Khoản thu trùng được giữ riêng để không ngăn xử lý giao dịch hợp lệ ban đầu.

Lỗi nhóm 10:

- 400: thiếu/sai request, gói/mức giá ngừng bán, mua Free hoặc tài khoản không hoạt động.
- 401/403: chưa đăng nhập hoặc không đúng vai trò.
- 404: không có mức giá, đơn hoặc đơn không thuộc chủ trọ.
- 409: thao tác không phù hợp gói hiện tại, đã có yêu cầu đổi gói đang mở, chưa có thanh toán hợp lệ hoặc đơn không thể xác nhận.

## 11. Thanh toán VNPay Sandbox

### 11.1. Tạo URL thanh toán

POST `/api/payments/vnpay/checkout`, quyền LANDLORD:

```json
{"type":"SERVICE_PACKAGE","targetId":"uuid-don-dang-ky"}
```

Thanh toán Boost dùng `type=BOOST_PACKAGE`, targetId là ID đăng ký Boost ở mục 13. Server lấy số tiền từ snapshot và dùng VNPay 2.1.0/HMAC-SHA512, VND nhân 100. Chỉ thanh toán số VND nguyên dương, tối đa 12 chữ số sau khi nhân 100; không tự làm tròn. Phương thức MoMo/chuyển khoản chưa có trong MVP.

Response `data` gồm payment và paymentUrl; frontend chuyển người dùng đến paymentUrl. payment gồm id, type, targetId, merchantOrderId, amount, status, expiresAt, paidAt, requiresReview, reviewReason, invoiceId.

- Đơn gói và đăng ký Boost có cửa sổ chờ thanh toán 15 phút; payment không kéo dài cửa sổ này.
- Gọi lại checkout khi còn payment PENDING hợp lệ trả lại cùng ID/mã tham chiếu. Sau lần thanh toán thất bại có thể tạo lần thử mới nếu đơn còn hạn.
- Chủ trọ chỉ thanh toán đơn/Boost của mình. Checkout chặn đơn đã thanh toán, hết hạn hoặc không còn chờ thanh toán; đơn dịch vụ còn được kiểm tra tính phù hợp với gói hiện tại. Boost được mua độc lập với tin và cho phép nhiều gói cùng hoạt động.
- Không nhận số tiền, ngày hết hạn, trạng thái hoặc user ID do client tự đặt.
- Chưa cấu hình khóa VNPay: trả 503; số tiền không hợp lệ: 400; đơn không thể thanh toán: 409; đối tượng không thuộc người dùng: 404.

### 11.2. IPN và Return

- GET `/api/payments/vnpay/ipn`: không cần JWT, dành cho VNPay. Kiểm tra chữ ký, terminal, mã tham chiếu, số tiền, cả vnp_ResponseCode/vnp_TransactionStatus và mã/thời gian giao dịch.
- GET `/api/payments/vnpay/return`: không cần JWT, trả ApiResponse gồm signatureValid, merchantOrderId, responseCode, transactionStatus, message. Endpoint này **không cập nhật thanh toán hoặc cấp quyền**.
- Chỉ hai callback GET trên được mở công khai trong module payment; checkout và lịch sử vẫn yêu cầu xác thực.

IPN trả JSON trực tiếp theo giao thức VNPay, không bọc ApiResponse:

```json
{"RspCode":"00","Message":"Confirm success"}
```

Mã IPN: 00 đã xử lý, 01 không có tham chiếu, 02 đã xác nhận, 04 sai số tiền, 97 chữ ký/terminal không hợp lệ, 99 dữ liệu không hợp lệ hoặc transaction chưa hoàn tất. Lỗi transaction rollback để VNPay có thể gửi lại.

IPN thành công ghi Payment SUCCESS và hóa đơn đúng một lần, rồi áp dụng quyền trong cùng transaction. CHANGE nâng gói được APPLIED ngay, CHANGE hạ/đổi ngang tự SCHEDULED; PURCHASE/RENEW và Boost áp dụng nếu còn đủ điều kiện. Callback lặp không nối thêm kỳ hoặc thêm hóa đơn.

Khoản thu quá hạn, thu trùng hoặc không còn đủ điều kiện vẫn giữ SUCCESS và hóa đơn, đồng thời đặt requiresReview=true cùng reviewReason; không cấp chồng quyền hoặc tự hoàn tiền. Nếu provider ghi nhận đã trả tiền trong hạn nhưng IPN đến sau khi hệ thống đánh dấu EXPIRED, hệ thống kiểm tra lại điều kiện trước khi áp dụng. Thời gian bắt đầu sử dụng tính tại lúc cấp quyền thực tế; CHANGE nâng gói giữ hạn gốc, hạ/đổi ngang theo mốc đã xếp lịch.

### 11.3. Theo dõi kết quả

- GET `/api/payments?page=0&size=10`: LANDLORD xem lịch sử của mình.
- GET `/api/payments/all?page=0&size=10`: ADMIN xem toàn bộ giao dịch của mọi chủ trọ, gồm tất cả trạng thái; mới nhất trước. Không thay đổi phạm vi lịch sử của LANDLORD.
- GET `/api/payments/{id}`: LANDLORD sở hữu hoặc ADMIN xem trạng thái đã ghi nhận trong hệ thống.
- GET `/api/payments/{id}/invoice`: cùng quyền trên, trả id, invoiceNo, taxAmount, totalAmount, buyerName, issuedAt; chưa có hóa đơn trả 404.
- GET `/api/payments/review?page=0&size=10`: ADMIN xem các khoản thu cần đối soát. MVP chưa có API hoàn tiền hoặc hủy SCHEDULED.

Sau Return, frontend dùng ID payment đã nhận từ checkout để gọi API trạng thái. Nếu IPN chưa tới, có thể chủ động kiểm tra lại bằng QueryDr ở mục 11.4. Kết quả trên URL trình duyệt không tự thay đổi trạng thái. Hướng dẫn cấu hình và kiểm tra trực tiếp: [vận hành VNPay](OPERATIONS.md#vnpay).

### 11.4. Kiểm tra lại thanh toán qua QueryDr

POST `/api/payments/{id}/check`, không có request body. Chỉ ADMIN hoặc LANDLORD sở hữu payment được gọi; TENANT trả 403, chưa xác thực trả 401, payment người khác trả 404 trước khi gọi VNPay.

Backend chủ động gọi API QueryDr của VNPay, nên có thể kiểm tra khi chạy localhost mà không cần URL IPN công khai. IPN vẫn giữ nguyên để xác nhận tự động; Return không cấp quyền và không tự gọi QueryDr. Endpoint này chỉ truy vấn giao dịch đã có, không tạo khoản thanh toán mới.

Response ApiResponse.data gồm payment (cùng cấu trúc mục 11.1), providerResponseCode, providerTransactionStatus và message. providerResponseCode=00 chỉ có nghĩa truy vấn thành công; phải xét providerTransactionStatus để biết giao dịch có thành công hay không.

- QueryDr xác nhận giao dịch 00: cập nhật qua cùng luồng cấp quyền/hóa đơn/đối soát của IPN. Payment EXPIRED/FAILED vẫn có thể được ghi nhận SUCCESS nếu VNPay xác nhận đã thu tiền. Nếu thời điểm trả tiền trong hạn và điều kiện còn phù hợp, áp dụng quyền; thu quá hạn hoặc điều kiện đã thay đổi thì requiresReview=true.
- Giao dịch 01 chưa hoàn tất: giữ trạng thái hiện tại, không tạo hóa đơn/quyền. Giao dịch 02 lỗi: PENDING chuyển FAILED. Các trạng thái còn lại không tự cấp quyền hoặc thực hiện hoàn tiền.
- Payment đã SUCCESS/REFUNDED: trả kết quả đã ghi nhận, không gọi VNPay và không tạo quyền/hóa đơn lần nữa. IPN tới trong lúc QueryDr đang chờ cũng không gây áp dụng trùng.
- Lỗi mạng, provider không tìm thấy giao dịch (91), từ chối truy vấn hoặc phản hồi sai chữ ký/terminal/tham chiếu/số tiền/loại giao dịch: trả 502, không tự ghi nhận thành công. Không tự tạo lại thanh toán khi gặp lỗi truy vấn.
- Lời gọi mạng nằm ngoài transaction database; khi có phản hồi hợp lệ, backend mới khóa chủ trọ và đọc lại trạng thái mới nhất để áp dụng.

Schema lưu payments.vnp_create_date để QueryDr dùng đúng vnp_CreateDate ban đầu. Gọi lại checkout của payment đang chờ giữ nguyên mốc này. Payment cũ được backfill từ created_at; nếu URL cũ từng được tạo lại với mốc khác hoặc thời điểm persist chênh giây, đây chỉ là mốc gần đúng và VNPay có thể trả 91. Khi đó cần đối chiếu mốc vnp_CreateDate của URL checkout cũ, không sửa trạng thái thành công thủ công.

## 12. Tin đăng, quota và slot ưu tiên

### 12.1. Tạo và gửi duyệt lại

POST `/api/listings`, quyền LANDLORD, tạo và gửi PENDING ngay:

```json
{"propertyId":"uuid-co-so","title":"Phòng cho thuê","description":"Nội dung tin","priorityLevel":0,"boostSubscriptionId":"uuid-goi-boost-da-mua","boostPriorityLevel":2}
```

Cơ sở phải thuộc chủ trọ và có phòng/căn AVAILABLE. Title bắt buộc, tối đa 255 ký tự; description tối đa 20.000 ký tự. Server tạo slug, trạng thái và các mốc thời gian.

- `priorityLevel` tùy chọn 0–2: chọn ưu tiên gói dịch vụ, mặc định 0.
- `boostSubscriptionId` và `boostPriorityLevel` (1 hoặc 2) cùng có hoặc cùng bỏ. Khi dùng Boost, `priorityLevel` phải là 0 hoặc bỏ. Boost phải thuộc chủ trọ, ACTIVE, còn hạn và còn lượt tương ứng. Không mua gói mới trong request tạo tin.
- Tạo tin và trừ một lượt đăng cùng lượt ưu tiên đã chọn trong một transaction. Bất kỳ bước nào thất bại đều rollback, không để lại tin hoặc trừ lượt một phần.

- Quota là lượt đã sử dụng của kỳ, bao gồm tin ẩn/hết hạn. Chỉ admin từ chối ngay lần duyệt đầu mới hoàn đúng các lượt đã trừ về nguồn gói tương ứng. Sau khi ẩn, hết hạn, đã duyệt hoặc gửi duyệt lại, từ chối không hoàn lượt.
- Kiểm tra gói, quota và tin cùng cơ sở khi tạo, gửi lại và duyệt; bản ghi chủ trọ được khóa để chống thao tác đồng thời vượt quota.
- PENDING giữ chỗ 7 ngày; duyệt tại quota đầy không đếm trùng chính tin đang duyệt. Quá hạn chờ thì admin không duyệt trực tiếp được.
- PUT `/api/listings/{id}` với body `{"title":"Nội dung mới","description":"..."}`: chủ tin sửa nội dung khi tin không còn hiệu lực chờ duyệt/hiển thị (đã ẩn, từ chối hoặc quá hạn). Tin PENDING/PUBLISHED đang hiệu lực phải ẩn trước khi sửa; sửa không tự xuất bản hay tự kéo dài hạn.
- POST `/api/listings/{id}/submit`: chủ tin gửi duyệt lại khi tin không còn hiệu lực chờ duyệt/hiển thị; kiểm tra quyền/quota và bắt đầu cửa sổ PENDING 7 ngày mới.
- POST `/api/listings/{id}/hide`: chủ tin ẩn tin và ngừng lựa chọn ưu tiên/Boost, giữ lượt đã dùng; không hoàn lượt hoặc kéo dài Boost.

### 12.2. Duyệt và hiển thị

- GET `/api/listings/pending?page=0&size=10`: ADMIN xem PENDING còn thời hạn chờ.
- POST `/api/listings/{id}/approve`: ADMIN duyệt PENDING hợp lệ, kiểm tra lại gói/quota/phòng; đặt PUBLISHED, publishedAt tại lúc duyệt, expiresAt = publishedAt.plusMonths(1).
- POST `/api/listings/{id}/reject`, body `{"reason":"Lý do"}`: ADMIN từ chối PENDING hợp lệ, reason bắt buộc và tối đa 2.000 ký tự.
- GET `/api/listings?page=0&size=10`: công khai, chỉ PUBLISHED còn hạn; ưu tiên cao → thấp → thường, trong cùng mức theo publishedAt mới nhất, cuối cùng theo ID để phân trang ổn định.
- GET `/api/listings/{id}`: công khai, chỉ chi tiết PUBLISHED còn hạn, các trường hợp khác trả 404.
- GET `/api/listings/mine?page=0&size=10`: LANDLORD xem tin của mình ở mọi trạng thái; endpoint này vẫn yêu cầu xác thực.

ListingResponse gồm id, propertyId, property, propertyName, rentalMode, landlordId, landlordName, title, slug, description, status, rejectionReason, priorityLevel, packagePriorityLevel, submittedAt, pendingExpiresAt, publishedAt, expiresAt, createdAt. Bổ sung `initialReview` (tin còn ở lần duyệt đầu) và `boostAllocation` (`{listingId,boostSubscriptionId,level}` hoặc null). Boost đã chọn xuất hiện cả khi PENDING còn hạn; priorityLevel vẫn là 0 đến khi được duyệt. packagePriorityLevel trả lựa chọn còn hiệu lực của kỳ hiện tại. `property` chứa địa chỉ, latitude, longitude và các phòng của cơ sở.

Hết gói/hạ quota không xóa hoặc ẩn tin cũ trước expiresAt. Tin quá hạn không xuất hiện dù tác vụ nền chưa chuyển trạng thái. Đăng lại luôn PENDING, phải được duyệt lại để bắt đầu tháng hiển thị mới.

### 12.2.1. Tìm tin theo vị trí bản đồ

GET `/api/listings` nhận thêm các bộ tham số tùy chọn:

- `latitude`, `longitude`, `radiusKm`: gửi đủ cả ba, bán kính > 0, đơn vị km; tính khoảng cách đường chim bay bằng Haversine.
- `south`, `north`, `west`, `east`: gửi đủ bốn cạnh của khung bản đồ; `south <= north`. `west > east` nghĩa là khung đi qua kinh tuyến 180°.
- Vĩ độ trong [-90,90], kinh độ trong [-180,180]. Thiếu bộ tham số, sai phạm vi hoặc sai kiểu số trả 400.
- Có thể kết hợp cả hai bộ và các bộ lọc phòng hiện có; kết quả phải thỏa tất cả điều kiện. Tin thiếu một trong hai tọa độ bị loại khi lọc vị trí, vẫn được trả trong tìm kiếm thông thường.
- Phân trang `page`/`size` (1–100), tổng số kết quả, điều kiện công khai, loại trùng cơ sở và thứ tự ưu tiên được giữ nguyên; không sắp xếp theo khoảng cách.
- Ví dụ bán kính: `/api/listings?latitude=10.762622&longitude=106.660172&radiusKm=3&minPrice=2000000&size=20`.
- Ví dụ khung: `/api/listings?south=10.7&north=10.9&west=106.6&east=106.8&size=50`.

Tọa độ để FE đặt ghim nằm tại `data.data[].property.latitude` và `property.longitude`; chi tiết tin tại `data.property`. API danh sách admin/chờ duyệt cũng trả cùng cấu trúc vị trí. Backend không gọi Google API. Xem [giao diện bản đồ](../../DOANTOTNGHIEPFE/docs/UI.md#ban-do).

### 12.3. Chọn slot ưu tiên

PUT `/api/listings/{id}/priority`, quyền LANDLORD sở hữu:

```json
{"level":2}
```

- Level 2 = suất cao, 1 = suất thấp, 0 = rút lựa chọn theo gói.
- Chỉ gán cho PUBLISHED còn hạn, dùng hạn mức snapshot kỳ hiện tại. Gán vượt hạn mức trả 409. Cấp/đổi/rút dưới cùng khóa chủ trọ nên không chiếm trùng slot khi thao tác đồng thời.
- Business mặc định thấp cho mọi tin còn lại; level 0 chỉ rút slot đã chọn, không tắt mức mặc định Business. Chọn level 1 ở Business không chiếm slot hữu hạn.
- Sang kỳ mới, lựa chọn theo kỳ cũ mất hiệu lực; chủ trọ chọn lại theo quota mới. Không tự giữ quyền cao vượt giới hạn mới.
- Slot không bị Boost ghi đè: Boost hết hạn sẽ quay về mức theo gói còn hiệu lực. Mua Boost không đổi publishedAt, expiresAt hoặc quota tin.

Lỗi tin đăng: 400 khi sai trạng thái/phòng không sẵn sàng/gói hết quyền/quota đầy, 403 khi sai vai trò hoặc tạo tin cho phòng không thuộc mình, 404 khi không có tin/tin không thuộc chủ trọ, 409 khi hết slot ưu tiên. Các request DTO có validation, không nhận các trường kích hoạt do client đặt.

## 13. Mua gói suất Boost và phân bổ cho tin

Tất cả API dưới đây yêu cầu LANDLORD có tài khoản ACTIVE và chỉ thao tác dữ liệu của chính mình.

### 13.1. Mua trước, phân bổ sau

- POST `/api/boost-subscriptions`: tạo đơn PENDING_PAYMENT, không cần có tin đăng.
- GET `/api/boost-subscriptions?page=0&size=10`: lịch sử mua, sắp xếp createdAt/id giảm dần.

Body mua:

```json
{"boostPackageId":"uuid-goi-boost"}
```

Response gồm id, boostPackageId, packageNameSnapshot, highPrioritySlotsSnapshot, lowPrioritySlotsSnapshot, durationMonthsSnapshot, price, status, startAt, endAt, paymentExpiresAt, createdAt. Không còn listingId/priorityLevelSnapshot. Giá, tên, số suất và thời hạn được chốt lúc tạo đơn; sửa/ngừng bán danh mục không đổi đơn đã tạo.

Thanh toán trong 15 phút bằng POST `/api/payments/vnpay/checkout`, body `{"type":"BOOST_PACKAGE","targetId":"uuid-don-boost"}`. Sau khi backend xác nhận thanh toán hợp lệ qua IPN hoặc đối soát, gói ACTIVE trong 1/3/6 tháng lịch tính từ lúc kích hoạt. Chưa tin nào được tự nâng ưu tiên. Mỗi chủ trọ được mua và sử dụng nhiều gói đồng thời; mỗi gói giữ hạn riêng. Không yêu cầu gói dịch vụ trả phí còn hạn để mua Boost.

### 13.2. Xem số suất và các tin đã gán

GET `/api/boost-subscriptions/active` trả mảng các gói đang hiệu lực. Mỗi phần tử gồm:

- `subscription`: thông tin gói đã mua theo response ở trên.
- `usedHighPrioritySlots`, `usedLowPrioritySlots`: lượt đã dùng, kể cả phân bổ đã bỏ hoặc tin ẩn/hết hạn.
- `remainingHighPrioritySlots`, `remainingLowPrioritySlots`: lượt còn lại.
- `allocations`: danh sách `{listingId, boostSubscriptionId, level}`.

Tổng quyền khả dụng là tổng số suất của các gói còn hiệu lực. Mỗi phân bổ thuộc một gói cụ thể; không tự chuyển phân bổ sang gói khác khi hết hạn.

### 13.3. Gán, đổi mức hoặc chuyển gói cho một tin

PUT `/api/boost-subscriptions/allocations/{listingId}`:

```json
{"boostSubscriptionId":"uuid-goi-da-mua","level":2}
```

- `level` chỉ nhận 1 (thấp) hoặc 2 (cao), bắt buộc. Tin phải thuộc chủ trọ, PENDING còn hạn chờ duyệt hoặc PUBLISHED còn hạn hiển thị.
- Gói đích phải ACTIVE và còn thời hạn; giới hạn lấy từ snapshot số suất tương ứng.
- Mỗi tin chỉ giữ một phân bổ Boost. Gọi lại cùng body không tốn thêm suất. Đổi level hoặc boostSubscriptionId thay thế phân bổ cũ trong một transaction; nếu gói đích hết suất, phân bổ cũ được giữ nguyên.
- Khóa chủ trọ ngăn các request đồng thời vượt suất. Các suất cao/thấp không hoán đổi với nhau và không reset theo lượt sử dụng.
- Response `{listingId, boostSubscriptionId, level}` là mức Boost được gán, có thể khác mức hiển thị cuối cùng.

DELETE `/api/boost-subscriptions/allocations/{listingId}` bỏ phân bổ, giữ lượt đã dùng. Lặp lại với tin thuộc chủ trọ đã không còn phân bổ vẫn thành công. Response theo ApiResponse, data null.

Mức hiển thị cuối cùng = max(mức từ gói dịch vụ, mức Boost đang được gán). Boost không trừ lượt ưu tiên gói dịch vụ, không thay đổi publishedAt/expiresAt hoặc số tin được phép đăng. Ẩn tin, hết hạn, bỏ/đổi Boost không hoàn lượt. Chỉ từ chối ở lần duyệt đầu mới hoàn lượt; không ảnh hưởng giá, thanh toán, hạn gói hoặc những tin khác.

Lỗi: 400 khi dữ liệu/mức ưu tiên/trạng thái tin không hợp lệ hoặc gói hết hiệu lực; 401 khi chưa xác thực; 403 khi sai vai trò; 404 khi tin/gói đã mua không thuộc chủ trọ hoặc không tồn tại; 409 `BOOST_SLOTS_EXCEEDED` khi hết suất đúng mức. Cấu hình cả hai số suất bằng 0 trả 400 `INVALID_BOOST_PACKAGE`.

Migration V6 chuyển mỗi Boost cũ thành gói 1 suất cao, 0 suất thấp; giữ giá, trạng thái và thời hạn. Boost cũ ACTIVE còn hạn trên tin PUBLISHED còn hạn được gán lại cho chính tin đó. Các gói cũ không tự kích hoạt lại và Boost không tự gia hạn tin.

## 14. Đồng bộ hết hạn

Tác vụ chạy mỗi phút, từng chủ trọ trong transaction riêng: đồng bộ subscription ACTIVE/SCHEDULED, đơn và payment quá hạn, PENDING quá 7 ngày, PUBLISHED hết tháng, Boost hết hạn và giá trị ưu tiên lưu sẵn. Chạy lặp không thêm kỳ hoặc hóa đơn, không tự gia hạn và không xóa lịch sử.

Quota và truy vấn hiển thị xét thời gian thực tế. Danh sách tin tính ưu tiên từ kỳ/Boost còn hạn ngay trong truy vấn sắp xếp; response tính cùng các điều kiện hiệu lực, không dùng riêng listings.priority_level để quyết định quyền. Vì vậy tác vụ chạy chậm không giữ chỗ PENDING hoặc hiển thị ưu tiên đã hết hạn. Cột priority_level được giữ để tương thích, cập nhật khi thao tác tin/Boost và đồng bộ nền.

## 15. Xem đánh giá người thuê

- GET `/api/tenants/{tenantId}/reviews?page=0&size=10`: danh sách review đang hiển thị về người thuê, trả `ApiResponse<PageResponse<ReviewResponse>>`.
- GET `/api/tenants/{tenantId}/reviews/summary`: tổng hợp điểm từ các review đang hiển thị, trả `ApiResponse<ReviewSummaryResponse>`.

Cả hai API yêu cầu Bearer access token của tài khoản đang hoạt động. LANDLORD được xem review của tenant mà không cần có hợp đồng giữa hai bên, kể cả trước khi lập hợp đồng hoặc khi hợp đồng còn DRAFT/PENDING_CONFIRMATION. FE lấy tenantId từ RentalAgreementResponse để gọi trực tiếp lúc xem hợp đồng. Chính tenant và ADMIN cũng được đọc; tenant khác không được xem thay người thuê này.

Review bị ẩn không xuất hiện trong danh sách và không được tính vào tổng hợp. API không trả ID hợp đồng hoặc ghi chú kiểm duyệt. Chưa đăng nhập trả 401, sai quyền trả 403, không tìm thấy người thuê trả 404. Thay đổi quyền đọc này không thay đổi điều kiện tạo review hoặc báo cáo landlord theo hợp đồng.

<a id="review"></a>

## 16. Review

- Tenant tạo đánh giá `ROOM` và `LANDLORD`; landlord tạo đánh giá `TENANT`.
- Mỗi hợp đồng chỉ có một đánh giá cho mỗi loại. Hai đánh giá của tenant được gửi độc lập; thành công một phần phải được giữ lại.
- Hợp đồng phải có trạng thái `ACTIVE`, `EXPIRED` hoặc `TERMINATED`, hai bên đã xác nhận, ngày bắt đầu không nằm trong tương lai. Không tự đánh giá mình.
- Luôn lấy quyền tạo từ `GET /api/rental-agreements/{id}/review-eligibility`: từng loại trả `canCreate`, `reviewId`, `reason`. Không chỉ suy ra từ status ở FE.
- Điểm là số nguyên 1–5; nhận xét không bắt buộc, tối đa 2.000 ký tự. Payload tạo chỉ gồm `rentalAgreementId`, `rating`, `comment`; BE suy ra đối tượng từ hợp đồng.
- Chưa có API sửa/xóa đánh giá của tác giả. Đánh giá bị ẩn vẫn tồn tại và không cho gửi lại.
- Landlord có thể gửi hoặc cập nhật phản hồi tối đa 2.000 ký tự cho review phòng/chủ trọ thuộc hợp đồng của mình. Review bị ẩn không được phản hồi. Không có phản hồi cho review người thuê.
- Review phòng và chủ trọ được xem công khai theo các điều kiện của BE. Review người thuê chỉ cho chính người thuê, admin hoặc landlord đang hoạt động; quyền đọc không yêu cầu quan hệ hợp đồng. Điều kiện tạo review vẫn dựa trên hợp đồng.
- API công khai loại bỏ review bị ẩn, không trả ID tác giả, hợp đồng hay thông tin kiểm duyệt. Không dùng tên/avatar để suy ra quyền.
- Review của listing không chọn phòng được BE tổng hợp theo toàn bộ cơ sở; chọn phòng dùng API riêng theo `roomId`. Không cộng hai tập này với nhau.
- Summary không có review trả `averageRating = null`: hiển thị “Chưa có đánh giá”, không trình bày thành điểm 0/5.

Tất cả đường dẫn có tiền tố `/api`.

- `GET /rental-agreements/{id}/review-eligibility`.
- `POST /reviews/rooms`, `/reviews/landlords`, `/reviews/tenants`.
- `GET /reviews/mine?type=ROOM|LANDLORD|TENANT&page=0&size=10`.
- `GET /listings/{listingId}/reviews` và `/reviews/summary`.
- `GET /listings/{listingId}/rooms/{roomId}/reviews` và `/reviews/summary`.
- `GET /landlords/{id}/reviews` và `/reviews/summary`.
- `GET /tenants/{id}/reviews` và `/reviews/summary` với quyền tương ứng.
- `PUT /reviews/rooms/{id}/reply`, `/reviews/landlords/{id}/reply`, body `{reply}`.
- `GET /admin/reviews?type=...&hidden=...&targetId=...&page=0&size=10`; bỏ `hidden` để lấy tất cả.
- `PATCH /admin/reviews/{type}/{id}/visibility`, body `{hidden, reason}`. `type` tại đây là enum viết hoa.

- `GET /api/landlord/reviews?type=ROOM|LANDLORD&page=0&size=10`: LANDLORD xem review nhận được, trả `PageResponse<ReceivedReviewResponse>` với ngữ cảnh và `canReply`.
- Danh sách admin yêu cầu `REVIEW_READ`; ẩn/khôi phục yêu cầu `REVIEW_MODERATE`.
- Review public dùng `ReviewResponse`, danh sách của tác giả/admin dùng `ReviewDetailResponse`; không suy quyền từ tên/avatar. Page bắt đầu từ 0, size tối đa 50.

## Public listing reviews

`GET /api/listings/{id}/reviews?target=ROOM&roomId={roomId}&page=0&size=4`
is a compatibility response adapter for the listing UI. It delegates all access,
visibility, pagination and rating calculations to the canonical `ReviewService`.
The `target` parameter selects this adapter. Without `target`, the same route
returns the standard review page from `ReviewController`, documented in `api.md`.

It returns visible reviews for the selected room. Omitting `roomId` includes rooms
in the listing's property. `target=LANDLORD` returns reviews for that property's
landlord across their properties, independent of the selected room.

The normal API envelope contains `summary` and `reviews`:

- Summary: average rating, total visible reviews, reply percentage and 1–5 star
  distribution across all matching reviews, independent of pagination.
- Reviews: the normal page response, newest first with ID as a stable tie breaker.
  Items include author display name, score, comment, landlord reply, date and a
  flag for a matching rental agreement that satisfies the canonical confirmed-stay
  eligibility rule. No contact,
  identity or contract identifiers are included.

Hidden reviews are excluded from both the list and summary. Hidden, expired or
missing listings return 404. Selecting a room outside the listing's property
returns 404. Page sizes follow the canonical API limit of 50.

An empty summary has null average/reply percentage and zero counts. The UI must
not replace these with demo ratings. Existing reviews have an overall score,
not separate cleanliness/location/amenity scores or chat response metrics.

Review creation, replies and moderation use the canonical endpoints documented
in `api.md`; the adapter does not duplicate their business logic.

<a id="report"></a>

## 17. Report

- Chỉ tenant được tạo, xem danh sách báo cáo của mình và bổ sung minh chứng. Quyền `REPORT_READ` cho phép đọc hồ sơ/ảnh theo kiểm tra BE; `REPORT_HANDLE` cho phép nhận và kết thúc. Landlord không được cấp quyền xem mặc định.
- Đối tượng gồm `LISTING`, `LANDLORD`. Báo cáo tin đăng yêu cầu tin còn công khai hợp lệ; báo cáo chủ trọ yêu cầu quan hệ hợp đồng đã xác nhận trong các trạng thái được BE chấp nhận. Điều kiện này không kiểm tra ngày bắt đầu như review.
- Một tenant không được tạo báo cáo mới cho cùng đối tượng khi đã có báo cáo `PENDING` hoặc `PROCESSING`. Không tự báo cáo mình.
- Lý do lấy từ `GET /api/reports/reasons?targetType=...`; FE dịch nhãn sang tiếng Việt. `INVALID_PRICE` chỉ áp dụng tin đăng.
- Mã lý do hiện có: `SCAM` (Lừa đảo), `MISLEADING_INFORMATION` (Thông tin sai lệch), `INVALID_PRICE` (Giá không chính xác), `UNSAFE_CONDITION` (Điều kiện không an toàn), `INAPPROPRIATE_CONTENT` (Nội dung không phù hợp), `OTHER` (Khác).
- Mô tả tối đa 5.000 ký tự; chọn “Khác” phải có nội dung sau khi trim.
- Luồng trạng thái: `PENDING` → `PROCESSING` → `RESOLVED` hoặc `REJECTED`. Chỉ admin đã nhận xử lý mới được kết thúc; ghi chú kết quả bắt buộc, tối đa 5.000 ký tự.
- Tối đa 5 ảnh JPEG/PNG, mỗi ảnh tối đa 5 MiB, chiều rộng và chiều cao không quá 4.096 px. BE kiểm tra nội dung ảnh thực.
- Tạo report trước bằng JSON, sau đó upload từng ảnh qua multipart field `file`. Chỉ bổ sung ảnh khi báo cáo còn `PENDING`; hiện chưa có API xóa ảnh, sửa/hủy hoặc mở lại báo cáo.
- Nội dung minh chứng là dữ liệu riêng tư: tải bằng HttpClient có Bearer token dưới dạng Blob, tạo object URL để hiển thị và thu hồi khi đóng/chuyển trang. Không gắn trực tiếp URL API vào `<img>` vì interceptor không chạy cho request ảnh của trình duyệt.
- API resolve/reject chỉ ghi nhận kết quả báo cáo và thông báo cho tenant; không tự ẩn tin, khóa tài khoản hay ẩn review.

Tất cả đường dẫn có tiền tố `/api`.

- `GET /reports/reasons?targetType=LISTING|LANDLORD`.
- `POST /reports`, body `{targetType, targetId, reasonCode, description}`.
- `GET /reports/mine?status=...&page=0&size=10`.
- `GET /reports/{id}` cho tác giả hoặc admin.
- `POST /reports/{id}/evidences`, multipart `file`; `GET /reports/{id}/evidences/{evidenceId}/content`, response Blob.
- `GET /admin/reports?status=...&reasonCode=...&page=0&size=10`.
- `GET /admin/reports/summary`.
- `POST /admin/reports/{id}/assign` nhận xử lý cho chính admin đang đăng nhập, không phải chọn người khác.
- `POST /admin/reports/{id}/resolve` hoặc `/reject`, body `{resolutionNote}`.

Report detail trả `{report, evidences}`; nội dung ảnh trả Blob qua API có token. Các API quản trị đọc/summary yêu cầu `REPORT_READ`, assign/resolve/reject yêu cầu `REPORT_HANDLE` và kiểm tra người nhận xử lý.


<a id="property-api"></a>

## 18. Property và PropertyType



API dùng `ApiResponse<T>` hiện tại. JWT bắt buộc; tạo thành công trả HTTP 200.

### Cơ sở cho thuê (LANDLORD)

- `POST /api/properties`: JSON tạo Property, tiện ích chung, các Room và tiện ích riêng trong một transaction.
- `GET /api/properties/mine?page=0&size=10`: danh sách cơ sở của tài khoản hiện tại; size 1–100.
- `GET /api/properties/{id}`: chi tiết cơ sở và các Room; chỉ chủ cơ sở được xem (403 nếu không sở hữu, 404 nếu không tồn tại).
- `PUT /api/properties/{id}/location`: chủ cơ sở thay thế địa chỉ và tọa độ; trả PropertyResponse. Body và kế hoạch FE: [../../DOANTOTNGHIEPFE/docs/UI.md#ban-do](../../DOANTOTNGHIEPFE/docs/UI.md#ban-do).

```json
{
  "name": "Nhà trọ ABC",
  "propertyTypeId": "property-type-id",
  "rentalMode": "BY_ROOM",
  "totalFloors": 3,
  "provinceId": "province-id",
  "wardId": "ward-id",
  "addressLine": "123 Nguyễn Trãi",
  "latitude": 10.762622,
  "longitude": 106.660172,
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

`landlordId` lấy từ Principal. `floors` và mỗi danh sách `rooms` phải có phần tử. `floorNumber` có thể null, khi có phải >= 0; không sinh số phòng từ tầng. `totalFloors` tùy chọn, >= 0. `latitude`/`longitude` tùy chọn nhưng phải cùng có giá trị hoặc cùng null, trong khoảng [-90,90]/[-180,180]. Tọa độ 0 hợp lệ. Ward phải thuộc Province.

`defaultRoom` có thể bỏ qua khi mỗi Room có đủ dữ liệu. Mỗi trường null của Room dùng giá trị mặc định tương ứng; số 0 là override thực sự đối với phí. Kết quả phải có roomType, price > 0, areaM2 > 0, maxOccupants > 0; các phí không âm. Default chỉ là input, không lưu vào Property. `amenityIds` của Property và Room độc lập, không kế thừa; ID trùng được loại bỏ.

`WHOLE` gửi cùng cấu trúc nhưng đúng một Room, ví dụ `roomNumber: "HOUSE"`; `BY_ROOM` có ít nhất một Room. Số phòng bắt buộc, tối đa 30 ký tự, trim và không trùng trong Property. Lỗi validation/count/default là 400, trùng số phòng 409, master data không tồn tại 404. Một Room lỗi khiến toàn bộ request rollback.

Response Property có `id`, `landlordId`, `name`, `propertyTypeId`, `propertyTypeName`, `rentalMode`, `totalFloors`, thông tin địa chỉ/tọa độ, `amenities`, `images`, `rooms`, `createdAt`, `updatedAt`. Mỗi Room dùng RoomResponse hiện tại và bổ sung `propertyId`, `roomNumber`, `floorNumber`; địa chỉ lấy từ Property. `images` Property hiện có entity/repository phục vụ lưu dữ liệu, chưa thêm API upload ảnh chung trong phạm vi này.

### Danh mục PropertyType

- `GET /api/property-types?page=0&size=10`, `GET /api/property-types/{id}`: tài khoản đã đăng nhập.
- `POST /api/property-types`, `PUT /api/property-types/{id}`, `DELETE /api/property-types/{id}`: ADMIN.
- Body tạo/sửa: `{"name":"Nhà trọ","description":"Mô tả"}`. Tên bắt buộc, tối đa 100 ký tự, duy nhất không phân biệt hoa thường. Trùng tên hoặc xóa loại đang dùng trả 409.
- Response có id, name, description, createdAt, updatedAt. Không có cây cha/con; RoomType vẫn giữ cây hiện tại cho loại đơn vị cho thuê.

### 18.1. Cập nhật vị trí cơ sở

PUT `/api/properties/{id}/location`, JWT LANDLORD sở hữu cơ sở:

```json
{
  "provinceId": "province-id",
  "wardId": "ward-id",
  "addressLine": "123 Nguyễn Trãi",
  "latitude": 10.762622,
  "longitude": 106.660172
}
```

Thay thế địa chỉ và tọa độ trong một transaction; trả `ApiResponse<PropertyResponse>`, HTTP 200. Tỉnh/phường/địa chỉ bắt buộc và phường phải thuộc tỉnh. Hai tọa độ phải cùng có giá trị hoặc cùng null; gửi/bỏ cả hai để null sẽ xóa ghim. Vĩ độ [-90,90], kinh độ [-180,180]; 0 hợp lệ. Quy tắc cặp tọa độ cũng áp dụng khi POST tạo cơ sở. Sai dữ liệu trả 400, sai quyền/sở hữu 403, không tìm thấy cơ sở/tỉnh/phường 404. Vị trí dùng chung cho tất cả phòng và tin của cơ sở; cập nhật không đổi trạng thái duyệt tin. Xem [giao diện bản đồ](../../DOANTOTNGHIEPFE/docs/UI.md#ban-do).

<a id="rbac"></a>

## 19. Quản trị phân quyền


Bản đầu giữ ADMIN, LANDLORD, TENANT. Ma trận quản lý 8 quyền đã được backend thực thi: USER_READ, USER_MANAGE, REVIEW_READ, REVIEW_MODERATE, REPORT_READ, REPORT_HANDLE, ROLE_PERMISSION_READ, ROLE_PERMISSION_MANAGE.

Các quyền cũ ngoài danh sách này chưa đưa vào ma trận, tránh hiển thị checkbox chưa có tác dụng trên endpoint. Các module nghiệp vụ khác vẫn áp dụng phân quyền hiện tại.

### Đọc ma trận

GET /api/admin/role-permissions, cần ADMIN và ROLE_PERMISSION_READ.

ApiResponse.data có:

- permissions: các dòng {id,code,name,module}.
- roles: các cột {id,code,name,version,permissionIds,lockedPermissionIds}.

FE dựng checkbox từ permissionIds; disable các ô thuộc lockedPermissionIds. Số quyền mỗi cột là permissionIds.length. Tìm kiếm theo code/name có thể làm ngay trên dữ liệu này.

Hai quyền ROLE_PERMISSION_* khóa ở mọi vai trò: chỉ ADMIN có sẵn theo migration. USER_MANAGE chỉ chỉnh được trên ADMIN để không cấp đường tạo/sửa tài khoản quản trị cho người dùng nghiệp vụ.

### Bật/tắt

PATCH /api/admin/roles/{roleId}/permissions/{permissionId}, cần ADMIN và ROLE_PERMISSION_MANAGE.

Body: {"enabled":true,"expectedVersion":0}. expectedVersion lấy từ cột role của lần đọc gần nhất.

Response data: {roleId,permissionId,enabled,version}. Lưu version mới sau mỗi lần cập nhật và tuần tự hóa thao tác cùng một role; khi 409 PERMISSION_VERSION_CONFLICT thì tải lại ma trận. API khóa role trong transaction, đối chiếu phiên bản mới nhất, cập nhật quan hệ và ghi audit atomically. Gửi trạng thái hiện có không tạo audit thừa.

409 PERMISSION_PROTECTED nếu chỉnh ô bị khóa hoặc permission chưa được quản lý. Sai quyền 403, ID không tồn tại 404. Không có API tạo role hoặc permission tùy ý.

### Quyền có tác dụng ở đâu

- USER_READ: danh sách/chi tiết người dùng quản trị.
- USER_MANAGE: tạo/sửa/xóa tài khoản quản trị, vẫn yêu cầu vai trò ADMIN và giữ các bảo vệ tài khoản ADMIN hiện có.
- REVIEW_READ: danh sách quản trị review.
- REVIEW_MODERATE: ẩn/hiện review.
- REPORT_READ: danh sách, thống kê, chi tiết report và đọc bằng chứng của hồ sơ khác.
- REPORT_HANDLE: nhận và đóng report, vẫn kiểm tra đúng người được giao xử lý và trạng thái. Muốn xem hồ sơ xử lý cần cấp thêm REPORT_READ.

Các kiểm tra sở hữu, hợp đồng, tác giả review và tài khoản ACTIVE tiếp tục áp dụng. API đọc review người thuê của landlord và API báo cáo của chính tenant vẫn theo nghiệp vụ hiện có.

### Đồng bộ FE và audit

GET /api/users/me/authorities: tài khoản đăng nhập nhận danh sách authority hiện tại từ database, gồm ROLE_* và permission code. JWT filter vốn đã nạp quyền từ database mỗi request, nên grant/revoke không chờ JWT hết hạn. FE nên đọc lại danh sách khi tải trang/quay lại ứng dụng hoặc sau thao tác phân quyền; không dùng scope cũ trong token làm nguồn duy nhất để dựng menu.

GET /api/admin/role-permissions/audit?page=0&size=20: ADMIN có ROLE_PERMISSION_READ. Trả ApiResponse.data là mảng tối đa size bản ghi, mới nhất trước, gồm actor_id/actor_name, role_id/role_code, permission_id/permission_code, old_enabled, new_enabled, role_version, created_at. page từ 0, size 1–50. Chưa có API sửa/xóa audit.


<a id="rental-api"></a>

## 20. Đặt phòng và hợp đồng

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

RentalAgreementResponse có thêm `tenantId`, `landlordId` bên cạnh `tenantName`, `landlordName` trong mọi response tạo/xem/danh sách/xác nhận/checkout. Hai ID lấy từ quan hệ hợp đồng, không suy từ tên hiển thị hoặc chủ sở hữu hiện tại của Property. FE dùng tenantId để mở `GET /api/tenants/{tenantId}/reviews`, landlordId để tạo report LANDLORD qua `POST /api/reports`; quyền xem/báo cáo vẫn được backend kiểm tra. Chủ trọ xem review nhận được qua `GET /api/landlord/reviews?type=ROOM|LANDLORD`, xem [contract Review/Report](api.md#review).



<a id="listing-contact"></a>

## 21. Liên hệ chủ trọ

`POST /api/listings/{id}/reveal-phone` is available to guests and signed-in users.
The response uses the normal API envelope with `data: { "phone": "..." }`.
The phone comes from the property's landlord account and is not added to public
listing responses. Hidden, expired and missing listings return 404.

If the landlord has no phone, the endpoint returns `data: { "phone": null }`.
A successful reveal increments `listings.contact_count` atomically, except when
the authenticated viewer owns the listing. Missing phones and failed requests
do not increment the counter. No database migration is needed.

The detail page requests the number only after clicking the reveal button. It
blocks duplicate clicks while loading and keeps the result when changing rooms
within the same listing. Once revealed, the number is a `tel:` link, so calling
does not send another reveal request. Navigating to another listing clears it.

This is a total contact counter, including historical contact data. It does not
yet represent unique visitors or a phone conversion rate. Future conversion
reporting should define the view denominator, time window, event type and
visitor deduplication before using the counter in a ratio.

<a id="notifications"></a>

## 22. Thông báo

Tất cả endpoint yêu cầu access token. Backend lấy user từ JWT, không nhận user ID từ client. Response dùng `ApiResponse<T>` hiện có.

- `GET /api/notifications?page=0&size=20&unreadOnly=false`: `PageResponse<NotificationResponse>`, mới nhất trước, thứ tự ổn định theo thời gian và ID. `size` từ 1 đến 100.
- `GET /api/notifications/unread-count`: `data` là số thông báo chưa đọc.
- `PUT /api/notifications/{id}/read`: đánh dấu đã đọc; gọi lại được; ID không thuộc tài khoản trả 404.
- `PUT /api/notifications/read-all`: đánh dấu toàn bộ thông báo của tài khoản hiện tại đã đọc.

Một mục inbox có dạng:

```json
{
  "id": "event-uuid",
  "type": "RENTAL_AGREEMENT",
  "title": "Cập nhật thuê phòng",
  "body": "Hợp đồng PDF đã sẵn sàng. Vui lòng xem và xác nhận trước hạn.",
  "referenceType": "RENTAL_AGREEMENT",
  "referenceId": "agreement-uuid",
  "read": false,
  "createdAt": "2026-11-08T10:00:00"
}
```

Thời gian không kèm offset dùng múi giờ Việt Nam, khớp dữ liệu thuê phòng hiện có. FE hiển thị giờ Việt Nam. Swagger liệt kê API tại `/swagger-ui/index.html`.

<a id="chat"></a>

## 23. Chat

Các endpoint `/api/chat` yêu cầu access token và kiểm tra thành viên hội thoại:

| Method | Đường dẫn | Kết quả |
| --- | --- | --- |
| POST | `/conversations/listing/{listingId}` | Lấy hoặc tạo hội thoại từ tin PUBLISHED còn hạn; chủ trọ lấy qua Listing → Property |
| POST | `/conversations/support` | Tenant/landlord mở hỗ trợ với tài khoản admin cố định trong seed |
| GET | `/conversations` | `ApiResponse<List<ChatConversationResponse>>` của tài khoản hiện tại |
| GET | `/conversations/{id}` | Chi tiết hội thoại của thành viên |
| GET | `/conversations/{id}/messages?page=0&size=20` | `PageResponse<ChatMessageResponse>` |
| PUT | `/conversations/{id}/read` | Đánh dấu đã đọc |

STOMP kết nối `/ws`, CONNECT mang `Authorization: Bearer <access_token>`, gửi tới `/app/chat.send`, nhận tại `/user/queue/messages` và lỗi tại `/user/queue/errors`. Payload gửi gồm `conversationId`, `content`, `messageType` (mặc định TEXT); sender lấy từ principal, không nhận senderId/receiverId từ client. Database lưu tin trước khi phát realtime sau commit. Không có API tìm người hoặc tạo direct chat tùy ý trong controller hiện tại.
