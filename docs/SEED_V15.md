# Seed hành chính và dữ liệu mẫu — V2 (bản V15 ban đầu)

File: `src/main/resources/db/migration/V2__seed_reference_and_demo_data.sql`.

## Phạm vi

File seed (được soạn ban đầu dưới tên V15, hiện đã đổi thành V2 trong workspace) chỉ seed dữ liệu trên schema Property/Room hiện tại (V1), không tạo lại bảng và không tự chạy trên database ứng dụng trong quá trình thực hiện task.

Trên schema V1 trống, kết quả:

- 34 tỉnh/thành và 3.321 xã/phường/đặc khu.
- 3 role: ADMIN, LANDLORD, TENANT; 11 permission; đủ user_roles/role_permissions.
- 5 tài khoản mẫu: một admin, hai chủ trọ, hai người thuê.
- 4 loại cơ sở, 12 loại phòng theo cây, 14 tiện ích.
- 6 cơ sở gồm 4 BY_ROOM, 2 WHOLE; tổng 14 đơn vị Room.
- Tiện ích chung gắn Property; thiết bị riêng gắn Room. Không seed Listing, hợp đồng, đánh giá hay tài chính.

## Nguồn địa giới hành chính

Dữ liệu lấy trực tiếp từ [Danh mục hành chính — Cục Thống kê](https://danhmuchanhchinh.nso.gov.vn/), chọn Cấp = Xã, Đến ngày = **27/10/2026**, Thực Hiện → Xuất Excel. Bản xuất chứa toàn bộ 3.321 dòng, không chỉ trang đang hiển thị. Chỉ chuẩn hóa Unicode NFC và khoảng trắng/xuống dòng/NBSP; không tự suy luận hoặc sinh mã địa phương.

SHA256 file XLS nguồn: `d4a8ece8c02334bd428b661d835357c6736ad2646ac32f473707b0842fe6895a`.

Danh mục gồm 2.599 xã, 709 phường, 13 đặc khu trực thuộc 34 tỉnh/thành. Mã tỉnh là chuỗi hai chữ số, mã xã là chuỗi năm chữ số, giữ số 0 đầu. Bảng `wards` theo model của dự án chứa cả xã/phường/đặc khu; không chèn cấp huyện cũ.

Cơ sở pháp lý gốc: [Quyết định 19/2025/QĐ-TTg](https://chinhphu.vn/?docid=214409&pageid=27160). Snapshot hiện hành đã gồm các cập nhật sau năm 2025, được kiểm tra thêm bằng văn bản chính thức:

- `75`: Thành phố Đồng Nai, [NQ30/2026/QH16](https://chinhphu.vn/?classid=1&docid=218009&pageid=27160), hiệu lực 30/04/2026.
- `22`: Thành phố Quảng Ninh, [NQ36/2026/QH16](https://vanban.chinhphu.vn/?docid=219325&orggroupid=1&pageid=27160), hiệu lực 01/09/2026.
- `24`: Thành phố Bắc Ninh, [NQ39/2026/QH16](https://chinhphu.vn/?classid=1&docid=219328&pageid=27160), hiệu lực 20/09/2026.

Một dòng nguồn viết thường đầu tên `xã Bắc Sơn` (06325, Lạng Sơn); giữ nguyên cách viết nguồn thay vì sửa nội dung một cách âm thầm.

Mỗi phường/cơ sở mẫu tra theo mã chính thức, không theo tên có thể trùng:

- Hà Nội 01: Bạch Mai 00292, Cầu Giấy 00166.
- Huế 46: Thuận Hóa 19789.
- Đà Nẵng 48: Hòa Cường 20257.
- TP.HCM 79: Linh Xuân 26800.
- Cần Thơ 92: Ninh Kiều 31135.

## Tài khoản mẫu

Mật khẩu ban đầu dùng chung: `TroTotDemo@2026`, hash bằng BCrypt cost 10 theo PasswordEncoder của dự án, không có prefix `{bcrypt}`.

- Admin: `admin.demo@trotot.example.test`.
- Chủ trọ: `landlord.hanoi@trotot.example.test`, `landlord.south@trotot.example.test`.
- Người thuê: `tenant.an@trotot.example.test`, `tenant.binh@trotot.example.test`.

Đây là tài khoản demo có thông tin nhận diện rõ trong tên và domain `.test`, không phải user thật. Không seed điện thoại, CCCD, ảnh hoặc tọa độ giả thành thông tin xác thực. Dữ liệu mẫu bao gồm tài khoản ADMIN và mật khẩu đã công bố để kiểm thử, nên file này được thiết kế cho database phát triển/demo.

ADMIN nhận 11 quyền đã liệt kê; LANDLORD nhận Room CRUD + Listing read/create; TENANT nhận quyền xem Room/Listing. `WARD_WRITE`, `WARD_DELETE` là hai permission được controller hiện tại dùng trực tiếp. Các API kiểm tra role tiếp tục tuân thủ @PreAuthorize; permission không tự thay thế role.

## Ngữ nghĩa cơ sở/phòng

PropertyType mô tả loại tòa/cơ sở (nhà trọ, tòa căn hộ dịch vụ, nhà riêng, khu lưu trú sinh viên). RoomType mô tả đơn vị thuê, tổ chức cha/con; các Room chỉ dùng loại lá. Không dùng một tiện ích như điều hòa làm loại cơ sở.

WHOLE có đúng một Room mã `WHOLE`, floor_number NULL; giá, cọc, diện tích, số người áp dụng cho cả nhà. BY_ROOM có mã phòng riêng, tầng 0 là tầng trệt, mã duy nhất trong cùng Property. Mọi Room đều AVAILABLE; không giả lập trạng thái RENTED khi chưa có hợp đồng.

Giá/cọc/phí là VND, giá thuê theo tháng; điện VND/kWh, nước VND/m³. Với hai nhà nguyên căn, điện/nước NULL thể hiện thanh toán thực tế với nhà cung cấp; không ghi 0 để ám chỉ điện nước miễn phí. Phí dịch vụ WHOLE là 0 vì mẫu không thu phí dịch vụ riêng. Tên cơ sở có `[Mẫu]`, address_line ghi lô DEMO và “địa chỉ giả lập, không dùng giao dịch”, không mượn địa chỉ cụ thể của cơ sở có thật.

## Chạy lại và dữ liệu đã có

Danh mục role/permission/type/amenity tìm theo code/name; account theo email; FK resolve tới ID thực tế thay vì giả định một ID seed cũ. Code hành chính upsert tên/quan hệ chính thức theo snapshot và giữ ID đã có. User/password/auth_version và thuộc tính tài sản đã tồn tại không bị seed đặt lại. Các mapping chỉ thêm khi cặp FK chưa có.

Không xóa user/địa chỉ/phòng cũ, không tự đoán mã hành chính cho ward giả lập từ V2 như HN001. Vì vậy 34/3.321 là số lượng trên schema mới; database đã có địa chỉ cũ ngoài danh mục có thể nhiều hơn. Với ward mang mã chính thức đã có, tên và tỉnh cha được đồng bộ theo nguồn; nếu database cũ có Property lưu sai tỉnh so với ward, cần xử lý dữ liệu cũ riêng thay vì tự suy luận địa chỉ trong seed.

Thư mục migration hiện được tổ chức lại thành hai file: `V1__fullsql.sql` tạo schema và `V2__seed_reference_and_demo_data.sql` seed dữ liệu. Thứ tự này dùng trực tiếp được trên database mới/trống qua Flyway. Không tự repair/baseline hoặc đặt lại lịch sử Flyway của database cũ; lịch sử đã áp dụng các version trước cần được chuyển đổi riêng.

## Kiểm chứng

`SeedDataMigrationTest` dùng MySQL riêng, chỉ tạo/xóa schema ngẫu nhiên `seed_test_*`. Test kiểm tra counts/mã hành chính/quan hệ, cây loại phòng không chu trình, chủ sở hữu có role LANDLORD, WHOLE/BY_ROOM, các Room sử dụng loại lá, không tạo Listing; đăng nhập bằng BCrypt và tạo/đọc authority JWT bằng service thực; Hibernate validate 44 entity; chạy lại seed hai lần không tạo trùng hoặc đặt lại mật khẩu/auth_version đã sửa.

Chạy từ thư mục BE với `SUBSCRIPTION_TEST_MYSQL_URL=jdbc:mysql://127.0.0.1:<port>/` và `./mvnw.cmd test -Dtest=SeedDataMigrationTest`. Đây là kiểm tra database/JPA/JWT nội bộ, không phải chạy toàn bộ ứng dụng qua HTTP, email, Redis hay Elasticsearch.

Kiểm tra thêm chuỗi Flyway V1 → V2 trên schema mới: migrate lần đầu chạy hai migration, lần sau chạy 0; tổng 45 bảng gồm 44 bảng entity và flyway_schema_history. Task không chạy migration trên database ứng dụng.
