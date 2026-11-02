# Database demo: V1 + V2

Thư mục `src/main/resources/db/migration` chỉ còn:

- `V1__fullsql.sql`: schema đầy đủ, gồm cả booking/hợp đồng/PDF/outbox và hồ sơ tenant phục vụ AI; không chứa seed.
- `V2__seed_reference_and_demo_data.sql`: danh mục, địa chỉ và dữ liệu demo.

V3/V6 cũ đã được gộp vào V1. Bộ này dành cho database trống như yêu cầu; không chạy trên database đã có lịch sử migration cũ.

## Dữ liệu

| Nhóm | Số lượng |
| --- | ---: |
| Tài khoản | 127: 3 admin, 24 landlord, 100 tenant |
| Tỉnh/thành và xã/phường | 34 và 3.321, giữ snapshot địa chỉ sẵn có trong project |
| Cơ sở cho thuê | 192, gồm 168 cho thuê từng phòng và 24 nguyên căn |
| Phòng | 1.368 |
| Ảnh phòng / ảnh cơ sở | 4.104 / 576 |
| Tin đăng | 192, gồm 72 đang công khai |
| Gói service / lựa chọn giá | 7 / 24 |
| Gói Boost | 10 |
| Service subscription | 24: 12 đang hoạt động, 12 đã hết hạn |
| Boost subscription | 84: active, hết hạn, hủy, chờ thanh toán |
| Thanh toán / hóa đơn | 132 / 66 |
| Hồ sơ tenant | 100 |
| Tin đã lưu | 500 |
| Cuộc trò chuyện / tin nhắn | 100 / 400 |
| Yêu cầu thuê | 60: chờ xử lý, từ chối, hủy, hết hạn |

Tin đăng có đủ trạng thái published/pending/rejected/hidden/expired. Seed có lịch sử xem/liên hệ, thống kê theo ngày, thông báo, tiêu chí tìm trọ và lịch sử tìm kiếm cho AI.
Chưa tạo hợp đồng/PDF giả: có thể dùng yêu cầu thuê đang chờ để chạy luồng chấp nhận và tạo hợp đồng thật của ứng dụng.

## Đăng nhập

**Mật khẩu tất cả tài khoản: `123456`**, được lưu bằng BCrypt cost 10, khớp `PasswordEncoderConfig`.
Các tài khoản đều ACTIVE, đã xác minh email và có role tương ứng.

| Email | Kịch bản |
| --- | --- |
| `admin@gmail.com` | Quản trị |
| `landlord@gmail.com` | Service PRO và Boost đang hoạt động, có tin đã phân suất ưu tiên |
| `landlord02@gmail.com` | Service STANDARD, không có Boost active |
| `landlord03@gmail.com` | Boost active, dùng quyền đăng tin FREE |
| `landlord04@gmail.com` | Quyền FREE, không có service/Boost active |
| `tenant@gmail.com` | Người thuê, có hồ sơ, tin đã lưu và chat |

Tài khoản tiếp theo dùng `admin02@gmail.com` … `admin03@gmail.com`, `landlord02@gmail.com` … `landlord24@gmail.com`, `tenant02@gmail.com` … `tenant100@gmail.com`.
Email, tên, cơ sở, địa chỉ đường và tọa độ là dữ liệu demo; seed không gửi email và không gọi thanh toán thật.

Avatar của tất cả users: `https://img.icons8.com/nolan/1200/user-default.jpg`.
Mỗi phòng/cơ sở có đủ 3 URL ảnh được cung cấp trong yêu cầu, sắp xếp `sort_order` 0–2. `public_id` để NULL vì đây là ảnh bên ngoài Cloudinary.
Ngày seed dùng múi giờ +07:00 như clock nghiệp vụ backend và được tính tương đối lúc chạy. Service/Boost dùng thời hạn theo tháng lịch. Session timezone được khôi phục cuối V2.

## Chạy lại từ database mới

Sau khi xóa/tạo lại database theo cấu hình của bạn, chạy Maven clean trước để bỏ bản copy V3/V6 cũ trong `target/classes`:

```powershell
./mvnw.cmd clean spring-boot:run
```

Flyway tự chạy V1 rồi V2. Không cần baseline hoặc repair cho database trống. V2 là versioned migration: không chạy thủ công lần thứ hai trên dữ liệu đã seed.

Để thay số lượng/nội dung demo, sửa generator rồi tạo lại V2 trước khi dựng database mới:

```powershell
python scripts/generate-demo-seed.py
```

Generator dùng Python standard library, UUID cố định theo khóa nghiệp vụ, giữ nguyên khối `BEGIN REFERENCE DATA` của V2 để không cần tải lại dữ liệu hành chính.

## Kiểm tra tích hợp trên MySQL tạm

Hai test sau tự tạo và xóa database tên ngẫu nhiên của chúng. URL phải trỏ đến instance MySQL local dành cho test:

```powershell
$env:SUBSCRIPTION_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:13316/'
./mvnw.cmd clean '-Dtest=SeedDataMigrationTest,RentalMigrationTest' test
```

Kiểm tra migration V1/V2, Flyway chạy lại không seed trùng, BCrypt, role/JWT, Hibernate validate, quan hệ địa chỉ–phòng–tin đăng, ảnh, quota/Boost, tiền thanh toán/hóa đơn và ràng buộc chống trùng yêu cầu/hợp đồng.
