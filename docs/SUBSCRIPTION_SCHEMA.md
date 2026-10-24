# Nền tảng dữ liệu gói dịch vụ và Boost

Phạm vi tài liệu này: migration V3–V5, entity và enum. API và service cho gói dịch vụ, VNPay, tin đăng/ưu tiên và Boost được mô tả tại mục 8–14 trong `api.md`. Có IPN và QueryDr kiểm tra lại giao dịch khi chạy localhost; kiểm thử tự động không thay thế nghiệm thu giao dịch trên hệ thống VNPay. Thứ tự công việc theo `SUBSCRIPTION_PAYMENT_PLAN.md`.

Chỉ thêm 3 bảng: `service_package_prices`, `service_subscription_orders`, `service_subscription_periods`. Thông tin suất ưu tiên lưu trực tiếp trên `listings`. V4 chỉ bổ sung cột/index vào bảng hiện có, không tạo bảng thứ tư. Schema được kiểm chứng trên MySQL 8.0.42, bao gồm JPA validate toàn bộ entity.

V5 thêm payments.vnp_create_date để giữ mốc gốc khi tạo URL checkout và gửi đúng vnp_TransactionDate cho QueryDr. Dữ liệu VNPAY cũ được backfill từ created_at, không thay đổi trạng thái/giá/khoản thu. Đây là mốc suy ra cho dữ liệu cũ; URL checkout từng được mở lại trước V5 có thể đã dùng mốc khác, cần đối chiếu nếu VNPay không tìm thấy giao dịch. Payment mới giữ cùng mốc khi checkout lại, không lấy thời gian tạo yêu cầu QueryDr làm ngày giao dịch gốc.

## Danh mục và giá

- `service_packages`: danh mục và quyền lợi hiện được admin cấu hình. `max_listings = NULL` biểu thị không giới hạn; `high_priority_slots`/`low_priority_slots` là suất đồng thời; `default_priority_level = 1` hỗ trợ Business.
- `service_package_prices`: mỗi gói có giá riêng cho 1/3/6/12 tháng, duy nhất theo gói và số tháng. Free được seed với tối đa 3 tin, không có lựa chọn giá trả phí.
- `boost_service_packages`: lựa chọn Boost ưu tiên cao, thời hạn 1/3/6 tháng. Chỉ bản ghi có thời hạn hợp lệ mới được bán.

## Đơn, subscription và kỳ quyền lợi

- `service_subscription_orders`: một lần mua/gia hạn/đổi gói, giữ số tháng, giá và cấu hình đã chốt lúc đặt mua. Có thời hạn chờ thanh toán, dấu vết admin xác nhận và thời điểm đã áp dụng để xử lý lặp an toàn.
- `service_package_subscriptions`: khoảng thời gian sử dụng tổng, có trạng thái `SCHEDULED`, `ACTIVE`, `EXPIRED`, `CANCELLED`. Database giới hạn một ACTIVE và một SCHEDULED trên mỗi chủ trọ.
- `service_subscription_periods`: kỳ quyền lợi gắn với một đơn duy nhất, có khoảng thời gian và snapshot riêng. Entity được đánh dấu Hibernate `@Immutable`; service không cập nhật kỳ đã mua.
- Gia hạn thêm kỳ nối tiếp, kéo dài hạn tổng và giữ nguyên snapshot kỳ cũ. Mỗi lần áp dụng đơn chỉ tạo một kỳ. Khóa và kiểm tra không chồng lấn giữa các kỳ là trách nhiệm transaction ở phần service, không được suy ra rằng unique index đã kiểm tra khoảng thời gian.
- Yêu cầu CHANGE đang chờ thanh toán, chờ admin hoặc SCHEDULED có khóa duy nhất theo chủ trọ. Các trạng thái đã kết thúc không chặn lịch sử yêu cầu mới.
- Đổi gói đã trả tiền phải chờ admin; mốc bắt đầu là `max(hạn tổng gói cũ, confirmed_at)`. Khi đã SCHEDULED, service phải chặn cả tạo và áp dụng gia hạn gói cũ. Database chưa thay thế được kiểm tra nghiệp vụ này.

## Tin đăng và ưu tiên

- `listings.submitted_at`/`pending_expires_at`: mốc gửi duyệt và hạn giữ chỗ 7 ngày. Quota PENDING dùng hạn này, độc lập với hạn xuất bản cũ.
- `listings.published_at`/`expires_at`: mốc admin duyệt xuất bản và hạn hiển thị 1 tháng lịch. `APPROVED` được đổi thành `PUBLISHED` trong migration và Java enum.
- `listings.service_subscription_period_id` và `package_priority_level`: lưu ngay trên tin kỳ cấp suất và mức ưu tiên theo gói. Mức 0 phải có kỳ null; mức 1/2 phải có FK kỳ hợp lệ. Khi rút suất, cập nhật đồng thời về null/0. Không tạo bảng phân bổ riêng hoặc lưu lịch sử chuyển suất trong MVP.
- Index theo kỳ/mức/trạng thái/hạn hỗ trợ đếm suất đang dùng. Service phải kiểm tra chủ sở hữu, kỳ còn quyền và tổng số suất trên các tin PUBLISHED còn hạn; database không tự giới hạn tổng suất.
- `listings.priority_level` là mức hiển thị lưu sẵn nếu được sử dụng, khác với `package_priority_level`. Boost độc lập không được ghi đè lựa chọn theo gói, để khi Boost hết hạn còn tính được mức đúng.
- `boost_service_package_subscriptions`: đăng ký Boost mua riêng, snapshot tên/mức/số tháng; `PENDING_PAYMENT` chưa có thời gian sử dụng. `ACTIVE`/`EXPIRED` phải có đủ khoảng thời gian hợp lệ; một tin chỉ có một Boost ACTIVE.
- Quota thực tế chỉ đếm PENDING/PUBLISHED còn hạn dưới khóa chung chủ trọ. Tin quá hạn không giữ quota hoặc xuất hiện trong truy vấn công khai dù tác vụ chưa chạy. Truy vấn ưu tiên lấy kỳ/Boost còn hiệu lực; không dựa riêng priority_level lưu sẵn.
- Không có bảng, cột nguồn hoặc chu kỳ Boost tặng trong MVP.

## Thanh toán và lịch sử

- `payments` trỏ đúng một đơn gói dịch vụ hoặc đăng ký Boost bằng FK, đồng thời khớp `type`; không dùng ID đa hình không có FK.
- `merchant_order_id` duy nhất cho lần thử thanh toán; mã giao dịch nhà cung cấp duy nhất theo phương thức; có hạn chờ và trạng thái EXPIRED.
- Payment SUCCESS phải có `paid_at`. Một đơn có thể có nhiều lần thử; chống áp dụng quyền nhiều lần còn cần transaction xử lý trạng thái đơn.
- V4 thêm `payments.requires_review` và `review_reason` cho từng khoản thu. Thu trùng/quá hạn/không còn đủ điều kiện vẫn giữ SUCCESS và hóa đơn; service không dùng khoản đang đối soát để cấp quyền hoặc chặn xử lý khoản hợp lệ ban đầu.
- V4 thêm `boost_service_package_subscriptions.payment_expires_at`: đăng ký Boost chờ thanh toán 15 phút, không tự giữ chỗ ưu tiên. Dữ liệu V3 được gán mốc này từ created_at + 15 phút, giữ nguyên ngày bắt đầu/kết thúc Boost đang hoạt động.
- Mỗi payment tối đa một hóa đơn. Các FK liên quan lịch sử thanh toán dùng RESTRICT để không xóa dây chuyền khi xóa tài khoản/tin.
- Không có thay đổi cấu hình VNPay hoặc secret trong phần 1.

## Quy tắc nâng cấp dữ liệu cũ

V3 giữ nguyên V1/V2 và không thực hiện reset database.

- Danh mục gói cũ giữ `legacy_price`/`legacy_billing_cycle_days`; không tự tạo giá tháng từ giá/ngày. Admin cần cấu hình bảng giá mới trước khi bán.
- Danh mục Boost cũ giữ `legacy_duration_days`/`legacy_priority_level`, tạm ngừng bán và để thời hạn tháng null. Admin cần đặt thời hạn 1/3/6 tháng trước khi bật bán; không tự chuyển một tuần thành một tháng.
- Tin APPROVED có `approved_at` được đổi thành PUBLISHED; giữ `expires_at` nếu đã có, chỉ tính một tháng từ `approved_at` khi thiếu hạn. Không dùng ngày tạo làm ngày xuất bản.
- Schema cũ không có thời điểm gửi duyệt riêng: tin PENDING dùng `created_at` làm mốc gửi duyệt ban đầu và cộng 7 ngày. Đây là fallback được ghi nhận, không đặt lại thời gian giữ chỗ từ lúc chạy migration.
- Nếu tin APPROVED thiếu mốc duyệt hoặc có khoảng thời gian không hợp lệ, V3 dừng trước thay đổi schema để đối soát.
- Nếu đã có subscription, đăng ký Boost, payment hoặc hóa đơn cũ, V3 dừng trước thay đổi schema bằng guard `v3_requires_legacy_financial_reconciliation`. Schema cũ không lưu snapshot lúc mua, nên không thể tự khôi phục quyền lợi/giá/tháng đúng lịch sử. Cần phương án chuyển dữ liệu đã đối soát cho database đó; không xóa lịch sử hoặc bỏ guard để ép chạy.
- Kiểm tra các guard xảy ra trước DDL nghiệp vụ. MySQL không rollback trọn migration có DDL nếu phát sinh lỗi sau khi đã bắt đầu thay đổi schema; cần kiểm tra trên bản sao dữ liệu trước khi triển khai thực tế.

## Kiểm chứng

`SubscriptionSchemaMigrationTest` dùng MySQL thật, tự tạo schema ngẫu nhiên có tiền tố `subscription_test_` và chỉ dọn schema đó. Không dùng database ứng dụng làm schema thử nghiệm.

Chạy với một MySQL thử nghiệm riêng, Java 21 và Maven wrapper:

```powershell
$env:SUBSCRIPTION_TEST_MYSQL_URL = 'jdbc:mysql://127.0.0.1:33317/'
$env:SUBSCRIPTION_TEST_MYSQL_USER = 'root'
# SUBSCRIPTION_TEST_MYSQL_PASSWORD được cấp qua môi trường nếu server yêu cầu.
.\mvnw.cmd "-Dtest=SubscriptionSchemaMigrationTest,PackageCatalogApiTest,VnPaySignerTest,VnPayQueryClientTest" test
```

Các test MySQL bỏ qua khi chưa đặt URL thử nghiệm; test chữ ký không cần database. Bài kiểm tra schema gồm migration không tạo bảng phân bổ riêng, JPA validate toàn bộ entity, nâng cấp tin/danh mục cũ, guard lịch sử không xác minh được, FK/check cho ưu tiên trên tin, unique cho ACTIVE/SCHEDULED/CHANGE/Boost/mã giao dịch và nâng V3 lên V4 khi đã có dữ liệu thanh toán.

API test dùng MockMvc, controller/service/repository thật, SecurityConfig của ứng dụng và người dùng giả lập Spring Security; kiểm tra danh mục, tháng lịch/năm nhuận, snapshot, quyền sở hữu, gia hạn/đổi gói, callback VNPay sai/lặp/thu trùng, hóa đơn, quota/slot/Boost và các giao dịch đồng thời. Callback được ký bằng khóa giả của test trong schema riêng, chưa phải kết quả thanh toán qua website VNPay. Không thay thế nghiệm thu IPN công khai hoặc kiểm tra toàn bộ hạ tầng/JWT thật; xem `VNPAY_SANDBOX.md`.

Lần chạy sau bổ sung QueryDr ngày 24/10/2026: 67 test qua, 0 lỗi/0 bỏ qua (53 API/nghiệp vụ, 8 schema/JPA, 2 chữ ký, 4 HTTP client QueryDr). Migration V1–V5 và JPA validate được kiểm tra bằng MySQL 8.0.42 riêng; không dùng database ứng dụng làm database thử nghiệm. Các ca QueryDr kiểm tra hồi phục EXPIRED, không giữ transaction khi gọi mạng và IPN đến trong lúc chờ không nhân quyền/hóa đơn.
