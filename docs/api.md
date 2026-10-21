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
- Tất cả endpoint protected (ngoại trừ Auth) yêu cầu header:
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
  "message": "Đăng xuất thành công",
  "data": null
}
```

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
  "message": "Xóa tiện ích thành công",
  "data": null
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
  "message": "Xóa loại phòng thành công",
  "data": null
}
```

---

## 4. Quyền truy cập
Các API dưới đây cần quyền thích hợp:
- `Auth` APIs: public
- `GET /api/amenities`, `GET /api/room-types`, `GET /api/room-types/categories`, `GET /api/amenities/{id}`, `GET /api/room-types/{id}`: yêu cầu `isAuthenticated()`
- `POST/PUT/DELETE` amenity / room-type: yêu cầu `hasAuthority('AMENITY_CREATE')` hoặc `hasRole('ADMIN')` / `hasAuthority('ROOM_TYPE_CREATE')` hoặc `hasRole('ADMIN')`

---

## 5. Error response mẫu
Khi có lỗi, server trả về format lỗi Spring Boot khác (nếu không bị bắt ở controller), ví dụ:
```json
{
  "code": 400,
  "message": "Email không đúng định dạng",
  "data": null
}
```
hoặc
```json
{
  "code": 401,
  "message": "Không có quyền truy cập",
  "data": null
}
```

Nếu cần, tôi có thể tiếp tục viết thêm:
- file Postman collection,
- table danh sách API dạng Excel/Markdown ngắn hơn,
- hoặc cập nhật doc này theo chuẩn Swagger/OpenAPI.
