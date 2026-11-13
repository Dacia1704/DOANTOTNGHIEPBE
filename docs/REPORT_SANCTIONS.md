# Xử lý báo cáo và cảnh cáo chủ trọ

Sau khi nhận xử lý, quản trị viên kiểm tra minh chứng và nhập kết quả. Chỉ người nhận xử lý được chuyển `PROCESSING` sang `RESOLVED` hoặc `REJECTED`. Quyền xử lý vẫn là `REPORT_HANDLE`; quyền đọc hồ sơ là `REPORT_READ`.

Mỗi báo cáo được phê duyệt tăng `users.confirmed_report_count` đúng một lần. Báo cáo bị từ chối không tăng số lần. Khóa hàng chủ trọ trước hàng báo cáo giúp các báo cáo khác nhau được phê duyệt đồng thời nhận mức nối tiếp, không ghi đè bộ đếm.

- Lần 1: gửi thông báo cảnh báo cho chủ trọ.
- Lần 2: đặt duy nhất tin bị báo cáo (hoặc tin admin chọn khi báo cáo trực tiếp chủ trọ) thành `HIDDEN`. Đưa ưu tiên thường và ưu tiên gói của toàn bộ tin hiện có của chủ trọ về 0, ngắt liên kết kỳ gói ưu tiên và ngừng boost đang gán trên các tin đó. Các tin còn lại giữ nguyên trạng thái, vẫn hiển thị nếu đang công khai hợp lệ. Tin của chủ trọ khác không bị ảnh hưởng. Cờ `priority_suppressed` trên từng tin bảo đảm ưu tiên hiệu lực vẫn bằng 0 kể cả khi có gói đang hoạt động; không cho chọn ưu tiên lớn hơn 0 hoặc gán boost mới cho các tin này. Tin tạo mới sau chế tài không tự kế thừa cờ này.
- Từ lần 3: đặt tài khoản thành `BLOCKED` và tăng `auth_version`, khiến token cũ không còn sử dụng được. Tin của tài khoản bị khóa không còn xuất hiện qua truy vấn công khai; không cho tạo yêu cầu thuê mới. Hợp đồng và lịch sử thanh toán vẫn được giữ. Tài khoản có vai trò ADMIN được bảo vệ khỏi thao tác khóa này.

Quota boost đã dùng và lịch sử mua gói không được hoàn lại. Cờ giảm ưu tiên chưa có thời hạn tự hết hoặc API gỡ phạt. Việc mở khóa tài khoản bằng quản lý người dùng hiện có không đặt lại số lần đã xác nhận.

## Chọn tin liên quan khi báo cáo trực tiếp chủ trọ

`GET /api/admin/reports/{id}/sanction-options` yêu cầu cả `REPORT_READ` và `REPORT_HANDLE`, báo cáo đang `PROCESSING`, người gọi là người đã nhận xử lý. Response trong `ApiResponse.data`:

```json
{
  "nextWarningLevel": 2,
  "listings": [
    { "id": "listing-uuid", "title": "Phòng trọ", "status": "PUBLISHED" }
  ]
}
```

Danh sách chỉ gồm tin thuộc chủ trọ của báo cáo, bao gồm tin đã hết hạn hoặc đang ẩn. Mức xem trước có thể thay đổi khi có báo cáo khác được phê duyệt trước; backend luôn tính lại trong giao dịch lưu.

Với báo cáo `LANDLORD` đã có `originListingId`, mức 2 tự dùng đúng tin gốc đó; không cần gửi `relatedListingId` và không cho chọn tin khác. Nếu báo cáo cũ chưa có ngữ cảnh, admin phải chọn một tin trong danh sách và gửi:

```json
{ "resolutionNote": "Đã kiểm tra và xác nhận vi phạm", "relatedListingId": "listing-uuid" }
```

Gửi đến `POST /api/admin/reports/{id}/resolve`. Thiếu tin trả `REPORT_RELATED_LISTING_REQUIRED`; tin không thuộc chủ trọ trả `INVALID_REQUEST`. Lỗi không tăng bộ đếm và báo cáo vẫn đang xử lý. Khi chủ trọ chưa có tin phù hợp, báo cáo có thể tiếp tục chờ xác minh; không thể phê duyệt ở mức 2 mà bỏ qua chế tài.

Với báo cáo `LISTING`, hệ thống luôn dùng chính tin bị báo cáo; không được đổi sang tin khác. Không đổi đối tượng gốc của báo cáo `LANDLORD` thành `LISTING`. `sanctionedListingId` ghi nhận tin thực tế bị xử lý ở mức 2.

## Kết quả và thông báo

Report response có `warningLevel` (0–3), `sanctionAction` (`NONE`, `WARNING`, `HIDE_LISTING_AND_SUPPRESS_PRIORITY`, `BLOCK_ACCOUNT`) và `sanctionedListingId`. User response quản lý có `confirmedReportCount`.

Chế tài, bộ đếm, trạng thái báo cáo và việc ghi hàng đợi thông báo dùng chung giao dịch. Nếu giao dịch lỗi, tất cả cùng hoàn tác. Hàng đợi giao thông báo bất đồng bộ sau commit: tenant nhận kết quả, chủ trọ nhận thông báo chế tài. Thông báo chủ trọ tham chiếu tin hoặc hệ thống, không cấp quyền truy cập nội dung báo cáo riêng tư. Tài khoản đã khóa không thể đăng nhập đọc inbox cho đến khi được mở khóa; không có cam kết gửi email trong luồng này.

Migration `V9__report_sanctions.sql` thêm các cột và giá trị mặc định. Báo cáo đã giải quyết trước migration không bị tính phạt hồi tố. Flyway chạy migration khi khởi động backend; không chỉnh sửa dữ liệu sản xuất bằng bộ dữ liệu kiểm thử.

## Lưu và đọc tin đăng gốc

Migration mới `V10__report_origin_listing.sql` thêm `reports.origin_listing_id`, độc lập `listing_id` dùng để phân biệt loại báo cáo. Chỉ backfill báo cáo LISTING đã biết chính xác tin; không đoán tin gốc cho báo cáo LANDLORD cũ. Không sửa V1/V2 hoặc các migration đã chạy.

Khi tạo báo cáo chủ trọ từ hợp đồng, FE gửi `rentalAgreementId` cùng payload tạo báo cáo. BE kiểm tra chủ thể, trạng thái xác nhận và lấy tin từ `RentalAgreement → RentalRequest → Listing`. Nếu hợp đồng được tạo trực tiếp, không có yêu cầu thuê, BE không thể suy ra tin gốc. FE có thể gửi `originListingId` nếu biết rõ ngữ cảnh; nếu kèm hợp đồng, tin phải thuộc cùng cơ sở và khớp tin trong yêu cầu thuê nếu có.

Ví dụ payload `POST /api/reports`:

```json
{
  "targetType": "LANDLORD",
  "targetId": "landlord-uuid",
  "reasonCode": "SCAM",
  "description": "Nội dung phản ánh",
  "rentalAgreementId": "agreement-uuid"
}
```

`ReportResponse.originListingId` cho FE biết ngữ cảnh mà không thay đổi đối tượng LANDLORD. `GET /api/reports/{id}` trả thêm `originListing` và `sanctionedListing`, chứa chi tiết tin ngay cả khi ẩn/hết hạn. `GET /api/admin/reports/{id}/listings/{listingId}` yêu cầu `REPORT_READ`, cho admin xem tin gốc, tin đã xử lý hoặc tin đang thuộc chủ trọ trong hồ sơ. Không mở quyền xem công khai tin ẩn/hết hạn. Dữ liệu tin là thông tin hiện tại, không phải bản chụp lịch sử.
