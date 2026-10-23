# API Documentation - Rental Management

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

## 4. Users

Tất cả endpoint trong nhóm này yêu cầu header `Authorization: Bearer <access_token>`.
Các API `/me` lấy ID người dùng từ access token.

### 4.1 Lấy hồ sơ cá nhân

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

### 4.2 Cập nhật hồ sơ cá nhân

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

### 4.3 Đổi mật khẩu

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

### 4.4 Tải lên hoặc thay thế avatar

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

### 4.5 Xóa avatar

- Method: DELETE
- URL: /api/users/me/avatar
- Không có request body.
- Response: HTTP 200, `code: 200`, `message: "Success"`, `data` là `UserResponse` với `avatarUrl` được đặt về `null`.

### 4.6 Lấy danh sách người dùng

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

### 4.7 Lấy người dùng theo ID

- Method: GET
- URL: /api/users/{id}
- Quyền: ADMIN.
- Path param: `id` là ID người dùng.
- Response: HTTP 200, `code: 200`, `message: "Success"`, `data` là `UserResponse` như mục 4.1.
- Lỗi: 404 nếu không tìm thấy người dùng.

### 4.8 Tạo người dùng

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

### 4.9 Cập nhật người dùng

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

### 4.10 Xóa người dùng

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

## 5. Administrative Units (Tỉnh/Thành phố, Phường/Xã)

Tất cả endpoint trong nhóm này yêu cầu header `Authorization: Bearer <access_token>`, kể cả GET.
`provinceId` và `id` là ID bản ghi trong hệ thống, khác với mã hành chính `code`.

### 5.1 Lấy danh sách tỉnh/thành phố

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

### 5.2 Lấy danh sách phường/xã

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

### 5.3 Lấy phường/xã theo ID

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

### 5.4 Tạo phường/xã

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

### 5.5 Cập nhật phường/xã

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

### 5.6 Xóa phường/xã

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

## 6. Quyền truy cập
Các API dưới đây cần quyền thích hợp:
- Public: `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/forgot-password`, `POST /api/auth/reset-password`, `POST /api/auth/send-verify-email`, `POST /api/auth/verify-email`.
- `POST /api/auth/logout`: yêu cầu đăng nhập.
- `GET /api/amenities`, `GET /api/room-types`, `GET /api/room-types/categories`, `GET /api/amenities/{id}`, `GET /api/room-types/{id}`: yêu cầu `isAuthenticated()`
- `POST/PUT/DELETE` amenity / room-type: yêu cầu `hasAuthority('AMENITY_CREATE')` hoặc `hasRole('ADMIN')` / `hasAuthority('ROOM_TYPE_CREATE')` hoặc `hasRole('ADMIN')`
- API hồ sơ cá nhân, mật khẩu, avatar và các URL thay thế: yêu cầu đăng nhập.
- Danh sách, chi tiết theo ID, tạo, cập nhật và xóa người dùng: vai trò ADMIN.
- GET provinces/wards: yêu cầu đăng nhập.
- POST/PUT wards: authority `WARD_WRITE` hoặc vai trò ADMIN.
- DELETE wards: authority `WARD_DELETE` hoặc vai trò ADMIN.
- Thiếu hoặc sai access token: HTTP 401. Đã đăng nhập nhưng thiếu quyền: HTTP 403.

---

## 7. Error response mẫu

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
