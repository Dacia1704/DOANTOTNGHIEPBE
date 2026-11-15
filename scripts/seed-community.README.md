# Seed dữ liệu cộng đồng

Seed cộng đồng đã tích hợp vào quy trình khởi tạo dự án qua [V12__seed_community_demo_data.sql](../src/main/resources/db/migration/V12__seed_community_demo_data.sql), khoảng 2,3 MB UTF-8. Flyway chạy một lần sau V2 dữ liệu nghiệp vụ và V11 schema cộng đồng. File `scripts/seed-community.sql` chạy thủ công trước đây đã được chuyển sang migration này.

## Dữ liệu có sẵn

| Nhóm | Số lượng mặc định |
| --- | ---: |
| Bài viết | 600 |
| Bình luận gốc | 2.520 |
| Trả lời cấp hai | 4.664 |
| Tổng bình luận | 7.184 |
| Ảnh đính kèm | 880 |
| Hồ sơ kiểm duyệt | 268 |
| Đề nghị xem xét lại | 10 |
| Report | 30 |
| Nhật ký gỡ nội dung | 64 |
| Cảm xúc, lưu bài, theo dõi, liên kết listing | Phụ thuộc tài khoản và listing hiện có |

Với bộ thử nghiệm gồm 15 tài khoản demo và 12 listing công khai, dữ liệu có hơn 44.000 cảm xúc, hơn 5.600 lượt lưu và hơn 100 quan hệ theo dõi. Các con số tương tác thay đổi theo dữ liệu có trong database; không phải bộ đếm giả trên frontend.

600 bài gồm 520 VISIBLE (trong đó 20 bài đã xóa mềm), 20 RESTRICTED, 20 PENDING, 20 HIDDEN và 20 REMOVED. Có bài ghim, bài đã chỉnh sửa, các bài nhiều hơn 20 bình luận gốc để thử phân trang, bình luận đã xóa nhưng còn trả lời và nhiều tình huống kiểm duyệt.

Nội dung có hơn 20 chủ đề về tìm phòng, ở ghép, chi phí, hợp đồng, nấu ăn, góc học tập, chuyển trọ, tiếng ồn, thú cưng và an toàn; phân bố trên 16 chuỗi khu vực tương thích bộ lọc Quanh tôi hiện tại. Bài viết, bình luận và lý do kiểm duyệt không chứa nhãn DEMO hoặc số thứ tự dữ liệu mẫu. Thời gian trải trong khoảng 42 ngày và tính tương đối lúc chạy seed.

Ảnh chỉ dùng đúng ba URL được cung cấp, giữ nguyên query string và dấu `&`. Caption được viết như chia sẻ ý tưởng bố trí không gian, không có đoạn giải thích kỹ thuật về seed trên giao diện. Các ảnh này không được dùng làm bằng chứng một listing ngoài đời. Không tải ảnh, upload Cloudinary hoặc tạo Cloudinary public ID giả; `public_id=''` đánh dấu ảnh ngoài.

## Cách chạy

Sau khi bạn đã dừng BE/AI và xóa hai database để dựng lại, chạy theo [README chung](../../AIService/README.md), mục 2.2 và 2.4:

```powershell
Set-Location E:\SpringBoot\DoAn\RentalManagement
.\scripts\setup-demo.ps1 -CommunityPosts 600

Set-Location E:\SpringBoot\DoAn\AIService
.\scripts\setup_demo.ps1 -Users 1000
```

Lệnh backend bật hạ tầng, dựng schema V1, đồng bộ địa chỉ, sinh V2/V12 và áp dụng migration đến V12. Lệnh AI tạo lại `rental_ai`, áp dụng Alembic, seed AI và build embedding. Giữ `.env` hiện có và chuẩn bị venv theo README chung nếu đây là lần chạy đầu tiên. Hai script không xóa database thay bạn.

Không còn bước nạp cộng đồng riêng qua DataGrip hay SOURCE. Sau setup, khởi động BE/AI/FE bình thường và mở `/community`, `/community/posts/{id}`, `/admin/moderation`. Ba tài khoản chính admin@gmail.com, landlord@gmail.com, tenant@gmail.com được ưu tiên có bài để demo Bài của tôi.

Số lượng mặc định nằm trong [community-counts.json](data/community-counts.json). Đổi số bài **trước khi dựng database mới** bằng `-CommunityPosts 2000`. Nếu V2 đã được áp dụng, setup giữ nguyên file V2/V12 để không làm thay đổi checksum; tham số số lượng mới không ghi đè seed của database đó.

Script yêu cầu MySQL 8.0.16+ và các tài khoản ACTIVE đúng role có ID trong [seed-accounts.json](data/seed-accounts.json), do generator lõi tạo ra trước V12. Không chọn tác giả theo regex email. Cần ít nhất một tài khoản mỗi role, tổng ít nhất bốn tài khoản, trong đó ít nhất bốn tài khoản đã xác minh email và tạo từ 24 giờ để có dữ liệu report. Script kiểm tra điều kiện trước khi ghi dữ liệu chính.

Hàm `account_identity()` trong [seed_profiles.py](seed_profiles.py) sinh tên Việt và email theo tên một cách xác định. Ba email đăng nhập chính vẫn được giữ; các tài khoản khác dùng địa chỉ có tên tại domain dành riêng `trotot.example.test`, không gửi email tới hộp thư người thật. Danh sách tên/email/ID/role nằm trong manifest; không có mật khẩu trong file đó. Mật khẩu tài khoản seed vẫn là `123456`.

Nếu không có listing công khai hợp lệ, script vẫn nạp bài/bình luận/tương tác và bỏ phần gắn tin phòng. Chỉ chọn listing của chủ trọ demo, đã PUBLISHED, còn hạn và là bản công khai mới nhất của property; tối đa ba listing khác nhau mỗi bài.

Nếu migration lỗi, script setup dừng với exit code lỗi; xử lý nguyên nhân rồi chạy lại theo hướng dẫn Flyway, không dùng repair để bỏ qua checksum. Seed không tắt khóa ngoại, không xóa bảng và không reset database.

## Chạy lại và thay đổi số lượng

Flyway ghi V12 vào lịch sử nên khởi động backend lần sau không nạp lại seed. ID dữ liệu vẫn ổn định theo namespace riêng: database V11 từng được seed thủ công có thể nâng lên V12 mà không tạo trùng bài/bình luận đã có. Không dùng ON DUPLICATE KEY UPDATE để ghi đè nội dung hoặc quyết định kiểm duyệt. Nếu cố ý chạy lại SQL, quan hệ demo đã xóa cứng như reaction/follow/save có thể được thêm lại.

Sau khi ghép `main`, V9/V10 dành cho xử lý báo cáo; schema và seed cộng đồng chuyển sang V11/V12. Quy trình dựng database mới dùng thứ tự này. Nếu database đã chạy bản community V9/V10 cũ, không chạy tiếp hoặc dùng repair để ép lịch sử; cần dựng lại database phát triển như kế hoạch reset từ đầu.

Không tạo tài khoản mới, không đổi mật khẩu/quyền/tình trạng xác minh của người dùng, không sinh thông báo hàng loạt và không tạo hợp đồng/đánh giá listing. Quy tắc chỉ người đang/đã thuê mới được đánh giá được giữ nguyên. Nội dung kiểm duyệt mẫu chỉ nằm trong phần cộng đồng.

Có thể xem trước bộ lớn hơn bằng generator Python chuẩn, xuất ra ngoài thư mục migration để giữ nguyên V12 hiện có:

```powershell
python scripts/generate-community-seed.py --posts 2000 --output scripts/seed-community-large.sql
```

Generator hỗ trợ 60–10.000 bài, có file `.counts.json` đi kèm bản xem trước. Khi dựng mới, setup tự gọi generator với đầu ra mặc định V12 và cập nhật `scripts/data/community-counts.json`. Không chạy generator mặc định để đổi V12 đã áp dụng; chỉ tái sinh khi chuẩn bị database mới. Giữ nguyên V1/V2/V11 đã áp dụng.

## Kiểm chứng

Seed từng được kiểm tra trên MySQL 9.7.2 và 8.0.46 với schema tạm riêng, gồm luồng Flyway V2/V11/V12, cây bình luận hai cấp, giới hạn listing, ảnh và khả năng chạy lại. Các bộ test đã được tạm gỡ ngày 15/11/2026; đây là kết quả kiểm chứng trước đó, không phải bộ test hiện có.

Đã thử trường hợp không có listing và thiếu tài khoản; thiếu tài khoản bị chặn trước khi thêm bài. Cú pháp và CHECK yêu cầu tối thiểu MySQL 8.0.16.
