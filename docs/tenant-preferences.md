# Hồ sơ tìm trọ và địa điểm muốn ở gần

Nút **Đặt phòng** tại trang chi tiết chuyển tới `/tro/:listingId/dat-phong/:roomId`. Trang mới tải lại tin đăng và phòng từ API, nên có thể mở trực tiếp hoặc tải lại trình duyệt. Yêu cầu thuê tiếp tục gửi tới API hiện có, với thông tin người thuê và đúng `listingId`, `roomId`; giá và phí lấy từ dữ liệu phòng.

Trong **Thông tin cá nhân → Hồ sơ tìm trọ**, người thuê có thể lưu khu vực, tối đa 5 phường/xã, ngân sách, diện tích, loại phòng, số người, ngày chuyển đến, tiện ích ưu tiên/bắt buộc và lựa chọn cá nhân hóa.

## Địa điểm

Migration `V8__nearby_places.sql` tạo bảng `nearby_places` và seed 13 trường đại học/công viên mẫu ở Hà Nội, TP.HCM, Đà Nẵng, Cần Thơ. Tọa độ seed là vị trí trung tâm xấp xỉ, không phải điểm dẫn đường chính xác tới cổng.

`tenant_profiles` đã có `anchor_label`, `anchor_latitude`, `anchor_longitude`, `max_distance_km`. Migration thêm `anchor_place_id` liên kết danh mục. Chọn địa điểm có sẵn sẽ lấy tên/tọa độ chuẩn từ server; chấm lại trên bản đồ sẽ xóa liên kết danh mục và lưu tọa độ riêng, cho phép bỏ trống tên. Khi xóa địa điểm khỏi danh mục, tọa độ đã lưu trong hồ sơ vẫn được giữ.

AIService hiện đã đọc tọa độ, bán kính, ngân sách, khu vực và các tiện ích từ những bảng này. Không cần tạo thêm database riêng cho địa điểm hoặc thay đổi mô hình AI để sử dụng hồ sơ vừa lưu. Mỗi hồ sơ có một địa điểm muốn ở gần.

## API

- `GET /api/nearby-places?q=...&category=UNIVERSITY`: tìm danh mục, tối đa 100 kết quả/lần, yêu cầu đăng nhập. Có thể dùng `PARK` hoặc bỏ trống loại.
- `GET /api/users/me/tenant-profile`: đọc hồ sơ của người đang đăng nhập; trả `data: null` nếu chưa thiết lập.
- `PUT /api/users/me/tenant-profile`: thay toàn bộ thiết lập; chỉ vai trò TENANT. Không nhận user ID từ client.

API kiểm tra cặp kinh/vĩ độ, giới hạn tọa độ, khoảng giá/diện tích, phường/xã thuộc tỉnh/thành và các ID tham chiếu. Việc cập nhật hồ sơ, phường/xã và tiện ích nằm trong cùng transaction.

## Tạo lại database

Sau khi bạn chủ động tạo database sạch, chạy quy trình seed như trước từ thư mục backend:

```powershell
.\scripts\setup-demo.ps1
```

Script đồng bộ đơn vị hành chính ở V1, sinh seed V2 rồi áp dụng các migration còn lại, bao gồm danh mục địa điểm V8. Bước V1 tạm bỏ kiểm tra entity vì bảng/cột mới chưa được áp dụng; bước cuối vẫn kiểm tra đầy đủ bằng Hibernate validate. Không cần sửa hoặc chèn lại seed V2 để có danh mục địa điểm.

Nếu V2 đã được áp dụng, chạy lại script sẽ giữ nguyên dữ liệu và file seed, bỏ qua tải địa chỉ/sinh lại V2, rồi áp dụng migration còn thiếu và kiểm tra schema. Script không tự xóa database. Trên Windows PowerShell, thông báo tiến độ/warning từ stderr được hiển thị bình thường; mã thoát khác 0 vẫn dừng quy trình.

Database hiện có cũng có thể cập nhật bằng cách khởi động backend để Flyway áp dụng V8. Không xóa lịch sử Flyway hoặc chạy SQL V8 thủ công trên database đã được Flyway quản lý.

Bản đồ và tìm địa chỉ sử dụng cấu hình MapTiler hiện có của frontend. Nếu dịch vụ bản đồ không tải được, người dùng vẫn chọn và lưu được địa điểm trong danh mục.

Khi kiểm tra lần triển khai này, key hiện có trả HTTP 403 `Key usage restricted`. Cần cập nhật quyền domain/origin của key trong MapTiler Cloud cho địa chỉ frontend đang chạy, hoặc cấu hình key hợp lệ tại `src/environments/maptiler.config.ts` của frontend để sử dụng thao tác chấm bản đồ.

## Kiểm tra

`TenantPreferencesIntegrationTest` chạy trên schema MySQL ngẫu nhiên, kiểm tra migration, seed, JPA validate, lưu địa điểm có sẵn, tọa độ riêng, xóa địa điểm và dữ liệu không hợp lệ. Các biến `SUBSCRIPTION_TEST_MYSQL_URL`, `SUBSCRIPTION_TEST_MYSQL_USER`, `SUBSCRIPTION_TEST_MYSQL_PASSWORD` dùng cùng quy ước với các integration test hiện có.

Frontend có kiểm tra form/điều hướng và `scripts/booking-profile-ui.smoke.cjs` để kiểm tra trình duyệt bằng API fixture, không ghi vào database ứng dụng.
