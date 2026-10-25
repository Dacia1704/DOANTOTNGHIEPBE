# Kế hoạch triển khai gói dịch vụ, Boost và thanh toán

Trạng thái: đã có code backend MVP cho phần 1–7: danh mục/bảng giá, kỳ quyền lợi, đăng ký/gia hạn/đổi gói, VNPay checkout/Return/IPN, hóa đơn/đối soát, quota/duyệt tin, slot ưu tiên/Boost và tác vụ hết hạn. Vẫn chỉ thêm 3 bảng; V4 bổ sung cột đối soát thanh toán và hạn chờ thanh toán Boost vào bảng hiện có. Migration chỉ chạy trên schema thử nghiệm riêng, chưa áp dụng vào database ứng dụng. Chưa thực hiện giao dịch qua website VNPay hoặc xác nhận VNPay gọi được IPN công khai. Chi tiết API xem mục 8–14 trong `api.md`, cấu hình và kiểm tra sandbox xem `VNPAY_SANDBOX.md`.

Kiểm chứng ngày 24/10/2026: 54 test đã qua, không bỏ qua test nào trong lần chạy có MySQL thử nghiệm (45 API/nghiệp vụ, 7 migration/JPA trên MySQL 8.0.42 và 2 chữ ký). Có kiểm tra giao dịch đồng thời về quota, slot, gia hạn, đổi gói, IPN lặp/thu trùng và đọc snapshot cũ trước khi chờ khóa. Đây là nhóm kiểm thử theo phạm vi thay đổi, không phải kiểm tra toàn bộ ứng dụng/hạ tầng hoặc VNPay trực tiếp.

Bổ sung theo nhu cầu test localhost: POST `/api/payments/{id}/check` dùng QueryDr chủ động xác minh với VNPay, dùng chung transaction ghi nhận/cấp quyền/hóa đơn với IPN. Return không cấp quyền. Migration V5 giữ mốc checkout gốc, không thêm bảng. Sau bổ sung có 67 test qua, 0 lỗi/0 bỏ qua (53 API/nghiệp vụ, 8 schema/JPA, 2 chữ ký, 4 HTTP client QueryDr). Có thể kiểm tra lại payment EXPIRED đã trả tiền trong hạn mà không thanh toán lần nữa; quyền vẫn phải qua kiểm tra điều kiện hiện tại. Chưa xác nhận qua QueryDr thật giao dịch cụ thể của người dùng.

## 1. Nguyên tắc thực hiện

- Đọc và tuân thủ `docs/RULE.md`; nêu giả định, chọn giải pháp tối thiểu, chỉ sửa phần liên quan và kiểm chứng từng phần.
- Giữ module `subscription`, `payment`, `listing` và mẫu code hiện có: controller, DTO request/response, mapper, repository, service interface và service/impl khi cần.
- Đối chiếu `RoomTypeController`, `RoomTypeServiceImpl`, `RoomTypeMapper` và các module tương tự trước khi thêm lớp.
- Dùng lại `ApiResponse`, `PageResponse`, `AppException`, `ErrorCode`, principal, `@PreAuthorize`, MapStruct và cách quản lý transaction đang có.
- Không tạo lớp CRUD tổng quát, framework thanh toán hoặc refactor module ngoài phạm vi.
- Đồng bộ Flyway, entity và enum; giữ lịch sử migration đã áp dụng. Mỗi phần có tiêu chí hoàn thành và tài liệu API tương ứng.

## 2. Hiện trạng ban đầu trước triển khai

- `subscription` mới có bốn entity danh mục/đăng ký và `SubscriptionStatus`; chưa có repository, service hoặc controller.
- `payment` mới có `Payment`, `Invoice` và các enum; chưa có tích hợp thanh toán.
- `listing` mới có entity/enum; chưa có luồng gửi duyệt, duyệt và đăng lại để gắn kiểm tra quyền.
- `ListingStatus` hiện dùng `APPROVED`, trong khi nghiệp vụ mới thống nhất `PUBLISHED`; cần migration dữ liệu và cập nhật enum/references cùng lúc.
- `Listing` có `approvedAt`, `createdAt`, `expiresAt`, chưa có `publishedAt`.
- Database dùng MySQL và Flyway với `V1__baseline.sql`, `V2__seed_data.sql`; JPA đang đặt `ddl-auto: validate`.
- Enum phương thức có VNPay/MoMo/chuyển khoản, nhưng MVP đã chọn VNPay Sandbox. Giữ enum đang có, chưa triển khai hai phương thức còn lại.
- Test hiện có mới kiểm tra Spring context.
- `.env` và `.env.docker` đang được Git theo dõi. Khi cấu hình VNPay, dùng biến môi trường runtime hoặc file local riêng đã được ignore; không ghi khóa bí mật vào hai file này, source code, log hay tài liệu.

## 3. Quy tắc nghiệp vụ đã chốt

### 3.1. Danh mục và quyền theo gói

- Admin quản lý danh mục, giá theo thời hạn và trạng thái bán; chủ trọ chỉ đăng ký, thanh toán, gia hạn và dùng quyền của mình.
- Free: tối đa 3 tin, không có suất ưu tiên cao/thấp, mức mặc định thường, không có thống kê.
- Gói 1: tối đa 10 tin, tối đa 5 suất ưu tiên thấp, không có suất cao, mức mặc định thường, không có thống kê.
- Gói 2: tối đa 20 tin, tối đa 5 suất cao và 10 suất thấp, mức mặc định thường, có thống kê.
- Gói 3/Business: không giới hạn tin, tối đa 20 suất cao; tin còn lại tự động mức thấp, có thống kê.
- Quản lý phòng và yêu cầu thuê có ở mọi gói; không thêm cột khóa hai chức năng này theo gói.
- Lưu snapshot quyền lợi và giá giao dịch; thay đổi danh mục không sửa lịch sử đã mua.

### 3.2. Giới hạn số tin

- Chỉ tính tin `PENDING` và `PUBLISHED` còn hiệu lực vào hạn mức đồng thời.
- Tin ẩn, bị từ chối hoặc hết hiệu lực không chiếm hạn mức.
- Kiểm tra khi gửi duyệt, duyệt tin và đăng lại; tất cả kiểm tra nằm ở backend.
- Khi chuyển một tin từ `PENDING` hợp lệ sang `PUBLISHED`, không cộng tin đó hai lần.
- Cách kiểm tra thống nhất: đếm các tin hợp lệ khác tin đang thao tác, cộng 1 nếu trạng thái sau thao tác chiếm chỗ, rồi so với giới hạn. Quota đúng bằng giới hạn vẫn cho duyệt một tin đã được tính vào quota nếu tổng sau thao tác không vượt giới hạn.
- Gửi duyệt hoặc đăng lại một tin chưa chiếm chỗ phải còn đủ chỗ sau thao tác.
- Business bỏ kiểm tra trần số lượng, vẫn kiểm tra quyền sở hữu, trạng thái và hiệu lực.
- PENDING giữ quota tối đa 7 ngày từ lần gửi duyệt: `pending_expires_at = submitted_at.plusDays(7)`. Chỉ tính khi `now < pending_expires_at`; không dùng expires_at của lần xuất bản cũ để xét hạn chờ duyệt.
- Quá hạn chờ duyệt thì giải phóng quota ngay theo thời gian thực tế và cho phép gửi duyệt lại sau khi kiểm tra gói/quota. Admin không được duyệt trực tiếp một yêu cầu đã quá 7 ngày; phải có lần gửi duyệt lại hợp lệ.
- PUBLISHED hiển thị 1 tháng lịch từ lúc admin duyệt: đặt `published_at` tại lúc xuất bản và `expires_at = published_at.plusMonths(1)`.
- Đăng lại tin hết hạn bắt buộc gửi PENDING, kiểm tra gói còn hiệu lực và quota còn đủ, giữ chỗ tối đa 7 ngày như gửi duyệt mới. Không xuất bản ngay; sau khi admin duyệt mới PUBLISHED và có thời hạn hiển thị mới 1 tháng.

### 3.3. Suất ưu tiên theo gói

- Chủ trọ tự chọn tin dùng suất cao/thấp trong giới hạn gói; có thể rút và chuyển suất sang tin khác.
- Hạn mức suất là giới hạn đồng thời, không reset mỗi tháng và không trừ như lượt tiêu hao.
- Chỉ cấp ưu tiên hiển thị cho tin `PUBLISHED` còn hiệu lực, thuộc chủ trọ và có gói còn quyền.
- Mỗi tin lưu trực tiếp một kỳ cấp suất và một mức ưu tiên theo gói trong listings. Rút ưu tiên hoặc tin mất điều kiện thì xóa liên kết kỳ, đặt mức về 0 để trả suất; MVP không lưu lịch sử chuyển suất.
- Business dùng mức thấp mặc định; chỉ cần lưu phân bổ cho tối đa 20 tin được chọn mức cao.
- Suất theo gói hết hiệu lực khi gói hết hạn, kể cả khi tác vụ đồng bộ chưa chạy.

### 3.4. Thời hạn, gia hạn và đổi gói

- Gói trả phí có lựa chọn 1, 3, 6 hoặc 12 tháng; dùng tháng lịch theo `Asia/Ho_Chi_Minh`, không coi một tháng là 30 ngày.
- Đăng ký mới được kích hoạt sau khi thanh toán hợp lệ; `end_at` được tính bằng cộng số tháng đã mua vào mốc bắt đầu.
- Gia hạn cùng gói trước hạn: sau khi thanh toán, cộng số tháng mua thêm vào `end_at` hiện tại của subscription; không tạo subscription hoạt động thứ hai.
- Mỗi lần gia hạn phải có bản ghi giao dịch mua riêng, giữ giá/số tháng đã mua và chỉ cộng hạn một lần dù IPN lặp.
- Một subscription có nhiều kỳ nối tiếp. Mỗi kỳ là phần thời gian đã mua theo một đơn (1/3/6/12 tháng), có start_at/end_at và snapshot quyền lợi riêng; không tự chia mọi gói nhiều tháng thành kỳ một tháng.
- Gia hạn trước hạn tạo kỳ mới bắt đầu tại end_at trước gia hạn, đồng thời kéo dài end_at tổng của subscription. Kỳ hiện tại giữ quyền lợi cũ đến đúng ngày kết thúc kỳ; kỳ tiếp theo mới dùng snapshot đã mua cho kỳ đó.
- Gia hạn sau hết hạn: tạo subscription mới sau khi thanh toán thành công, không sửa subscription đã hết hạn thành một lịch sử liên tục giả.
- Nâng/hạ gói: người dùng thanh toán trước; IPN hợp lệ chỉ ghi nhận đã thanh toán. Admin chỉ xác nhận đơn có payment thành công đã được hệ thống kiểm chứng; không tự thay kết quả thanh toán VNPay.
- Trong transaction xác nhận, đọc lại end_at tổng của subscription cũ và ghi confirmed_at một lần. Mốc bắt đầu gói mới là `max(old_subscription.end_at, confirmed_at)`, end_at mới bằng mốc bắt đầu cộng số tháng đã mua; dùng hạn tổng đã bao gồm các kỳ gia hạn trả tiền trước đó, không cắt kỳ đã mua.
- Nếu admin xác nhận trước hạn, tạo subscription SCHEDULED với kỳ/snapshot đã mua; chỉ chuyển ACTIVE khi subscription cũ kết thúc. Nếu xác nhận đúng hoặc sau hạn, kết thúc trạng thái cũ nếu cần và kích hoạt ACTIVE ngay từ confirmed_at, không ghi lùi ngày bắt đầu hoặc chờ tác vụ.
- Ví dụ: gói cũ hết hạn 30/11, admin xác nhận ngày 03/12 thì gói mới bắt đầu 03/12 và được đủ thời hạn đã mua. Trong khoảng chờ xác nhận không có quyền đăng tin theo gói đã hết hạn.
- Khi đã có subscription SCHEDULED, chặn gia hạn gói hiện tại ở cả bước tạo đơn và áp dụng thanh toán. Không tự dời lịch gói mới; người dùng chờ kích hoạt gói mới. Việc hủy yêu cầu trả trước chưa nằm trong phạm vi MVP đã xác nhận.
- Nếu gia hạn đã hoàn tất trước khi admin xác nhận đổi gói, dùng hạn tổng mới nhất để tính mốc bắt đầu lần đầu cho gói mới. Sau khi đã SCHEDULED, mốc đó được giữ nguyên và gia hạn gói cũ bị chặn.
- MVP chỉ cho một yêu cầu đổi gói đang chờ xử lý/kích hoạt trên mỗi chủ trọ; bao gồm cả đơn chờ thanh toán, đã trả tiền chờ admin và gói SCHEDULED. Một yêu cầu hết hiệu lực phải được xử lý xong trước khi tạo yêu cầu khác.
- Giá bán cho từng thời hạn do admin cấu hình; không mặc định giá 3/6/12 tháng bằng giá một tháng nhân số tháng.
- Admin sửa danh mục không ghi đè snapshot của kỳ đã mua, kể cả kỳ tương lai đã thanh toán. Đơn mua/gia hạn sau thay đổi lấy cấu hình mới và lưu snapshot; quyền lợi mới chỉ có hiệu lực khi kỳ mới bắt đầu.
- Đọc quyền phải tìm kỳ đang hiệu lực trong subscription ACTIVE, không chỉ nhìn end_at tổng hoặc cấu hình danh mục hiện tại.

Ví dụ: kỳ hiện tại từ 23/10/2026 đến 23/11/2026 có giới hạn 10 tin. Admin sửa gói còn 8 tin, chủ trọ gia hạn thêm 3 tháng trước hạn: end_at tổng thành 23/02/2027, vẫn được 10 tin đến 23/11/2026; kỳ mới từ 23/11/2026 đến 23/02/2027 dùng snapshot 8 tin. Tin cũ vượt quota mới vẫn giữ đến hạn riêng.

### 3.5. Khi hết hạn hoặc hạ gói

- Tin đã đăng tiếp tục hiển thị đến `listings.expires_at`; không tự xóa/ẩn vì gói dịch vụ hết hạn hoặc quota mới thấp hơn số tin hiện có.
- Gói trả phí hết hạn thì chặn gửi duyệt tin mới, duyệt để xuất bản và đăng lại cho đến khi có gói hợp lệ; tin đã tồn tại không được dùng để bỏ qua kiểm tra quyền.
- Free là quyền mặc định cho chủ trọ chưa từng kích hoạt gói trả phí. Một lần đặt mua chưa thanh toán không làm mất Free; không tự quay về Free để đăng tiếp sau khi gói trả phí hết hạn.
- Hết hạn gói thì quyền ưu tiên cao/thấp của gói và mức thấp mặc định của Business mất hiệu lực. Nếu không có Boost độc lập còn hạn, tin về mức thường.
- Boost độc lập vẫn tiếp tục nếu bản thân Boost và tin còn hiệu lực.
- Khi hạ gói mà đang vượt quota, giữ tin cũ nhưng chặn thao tác tạo thêm chỗ chiếm quota; kiểm tra duyệt vẫn theo tổng sau thao tác.
- Khi gói mới bắt đầu, không tự chọn hoặc ẩn tin để ép về hạn mức, không tự chuyển toàn bộ phân bổ cao/thấp cũ sang gói mới khi chưa có chính sách được xác nhận.

### 3.6. Boost mua riêng và thứ tự hiển thị

- Boost mua riêng là ưu tiên cao cho một tin cụ thể, có lựa chọn 1, 3 hoặc 6 tháng lịch.
- Độc lập với quyền theo gói; Free, Gói 1, Gói 2 và Business đều có thể mua nếu tin hợp lệ.
- Mỗi tin tối đa một Boost độc lập đang hoạt động. Không tích trữ, xếp hàng hoặc chồng nhiều Boost trong MVP.
- Kích hoạt sau thanh toán thành công; kiểm tra lại tin tại lúc kích hoạt, không chỉ lúc tạo đơn.
- Boost không tăng số tin được đăng, không gia hạn tin, không gia hạn gói và không chiếm suất cao của gói.
- Thời gian Boost không bị cắt theo hạn gói dịch vụ. Nếu tin hết hạn trước Boost, Boost không làm tin tiếp tục hiển thị và không được dùng trên tin khác.
- Mức hiển thị lấy giá trị cao nhất còn hiệu lực từ mức mặc định của gói, phân bổ theo gói và Boost độc lập.
- Boost hết hạn thì tính lại quyền còn lại; nếu gói vẫn cấp mức thấp/cao thì trở về mức đó, nếu không thì mức thường.
- Sau bộ lọc và kiểm tra tin còn hiệu lực, sắp xếp: cao (2) → thấp (1) → thường (0); cùng mức dùng `published_at DESC`, sau đó ID để ổn định phân trang.
- Không dùng `created_at` hoặc `updated_at` thay thời điểm xuất bản; cập nhật nội dung hoặc mua Boost không làm mới thời gian đăng.

### 3.7. Phạm vi MVP và gói nhiều tháng

- Quota tin và suất cao/thấp là giới hạn đồng thời cho toàn thời gian hiệu lực; không cấp lại hoặc tăng gấp theo số tháng đã mua.
- Không triển khai Boost tặng trong MVP. Chỉ có suất cao/thấp theo gói và Boost mua riêng 1/3/6 tháng.
- Không tạo bảng chu kỳ tặng, cột nguồn GIFT, bộ đếm lượt tặng, tác vụ cấp lại theo tháng hoặc API dùng lượt tặng.
- Kỳ gia hạn có thể thay đổi quota/slot khi bắt đầu hiệu lực; không đồng nghĩa với reset quota mỗi tháng trong một kỳ đã mua.

### 3.8. Thanh toán VNPay Sandbox

- MVP tích hợp VNPay Sandbox cho mua gói, gia hạn và Boost mua riêng.
- Mã website sandbox do người dùng cung cấp: `NQC9OREC`.
- URL thanh toán: `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`.
- Secret được cung cấp trong hội thoại chỉ được đưa vào cấu hình runtime/local không được Git theo dõi khi triển khai. Không chép giá trị secret vào kế hoạch này.
- Cấu hình dự kiến: `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_PAYMENT_URL`, `VNPAY_RETURN_URL`; URL IPN sẽ được cấu hình với VNPay.
- Return URL phục vụ hiển thị/chuyển hướng kết quả; IPN đã xác minh dùng để ghi nhận giao dịch và cấp quyền. Quy trình ký/xác minh và phản hồi IPN theo [tài liệu chính thức VNPay](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
- Chưa cấu hình Return URL, IPN HTTPS truy cập được từ VNPay và URL frontend nhận kết quả; cần xác định khi chuẩn bị chạy thử tích hợp.

## 4. Cấu hình triển khai và phạm vi chưa mở rộng

- Ba tình huống đã có quyết định: chặn gia hạn khi có SCHEDULED; xác nhận muộn thì tính từ confirmed_at; đăng lại bắt buộc PENDING và duyệt lại. Không còn câu hỏi nghiệp vụ chặn việc thiết kế phần 1 theo phạm vi hiện tại.
- Trước khi kiểm thử VNPay thực tế cần cấu hình Return URL, IPN và URL frontend; trước khi bán cần admin cấu hình giá từng thời hạn. Có thể cung cấp các giá trị này ở giai đoạn tương ứng, không cần trì hoãn migration/entity vì chưa có URL chạy thử.
- Người dùng có nhắc hủy yêu cầu đổi gói "nếu được phép", chưa xác nhận chính sách hủy/hoàn tiền cho đơn đã trả. Kế hoạch MVP hiện không thêm API hủy/hoàn tiền hoặc tự hủy SCHEDULED để mở khóa gia hạn. Chỉ mở rộng khi có quy tắc cụ thể.
- Nếu payment thành công nhưng đơn không thể áp dụng đúng quyền hoặc lịch đã xác nhận, lưu khoản thu và trạng thái cần đối soát; không giả định được tự hoàn tiền, chuyển gói hoặc dời lịch.

## 5. Thứ tự triển khai

### Phần 1 — Database, entity và enum

Trạng thái: đã điều chỉnh nền tảng dữ liệu còn 3 bảng mới theo yêu cầu: giá gói, đơn mua/gia hạn và kỳ quyền lợi. Ưu tiên theo gói lưu trực tiếp trong listings, không tạo bảng phân bổ riêng. Test bao gồm schema mới, nâng cấp tin/danh mục cũ, JPA validate, FK/check/unique và guard đối soát lịch sử tài chính chưa có snapshot. Không tự suy diễn dữ liệu tài chính cũ; xem `SUBSCRIPTION_SCHEMA.md` trước khi nâng cấp database có lịch sử đó.

Thiết kế dưới đây dựa trên các quy tắc đã chốt; trước khi viết migration chỉ cần kiểm tra lại phiên bản, dữ liệu đang có và các ràng buộc thực tế của schema.

- Dùng migration kế tiếp, hiện dự kiến `V3__subscription_payment_updates.sql`; kiểm tra lại phiên bản khi bắt đầu. Không sửa `V1`/`V2` đã áp dụng và không reset database để nâng cấp.
- `service_packages` giữ danh mục Free/Gói 1/Gói 2/Business và quyền lợi. Đổi quota thành `high_priority_slots`/`low_priority_slots`; thêm `default_priority_level`; `max_listings = NULL` biểu thị Business không giới hạn.
- Thêm `service_package_prices`: FK gói, `duration_months` thuộc 1/3/6/12, giá và trạng thái bán; unique gói/số tháng. Giá/thời lượng gói trả phí được đưa về bảng này để một gói có nhiều lựa chọn thời hạn mà không nhân bản quyền lợi.
- Free có giá 0 và không cần lựa chọn thời hạn trả phí. Boost bán lẻ vẫn có thể dùng mỗi bản ghi `boost_service_packages` làm một lựa chọn thời hạn/giá; đổi `duration_days` thành `duration_months` thuộc 1/3/6 và mức ưu tiên cao (2).
- `service_package_subscriptions` lưu gói, chủ trọ và khoảng hiệu lực tổng. Bỏ `active_flag`, bổ sung SCHEDULED bên cạnh ACTIVE/EXPIRED/CANCELLED; chỉ có một subscription ACTIVE và tối đa một SCHEDULED trên mỗi chủ trọ. Tổng hạn sau gia hạn không được ghi đè thời lượng/giá của từng lần mua.
- Thêm `service_subscription_orders` lưu từng lần mua/gia hạn/đổi gói: chủ trọ, gói/lựa chọn giá, loại thao tác, subscription liên quan, số tháng/giá/quyền lợi snapshot cần thiết, trạng thái thanh toán/áp dụng, mốc hiệu lực và thời điểm đã áp dụng. Đơn chờ thanh toán không cấp quyền.
- Bản ghi đơn là điểm chống cộng hạn hai lần: một đơn chỉ được áp dụng một lần dù có nhiều lần thử thanh toán. Lưu vết hạn trước/sau gia hạn để đối soát.
- Đơn đổi gói có bước AWAITING_ADMIN_CONFIRMATION sau khi ghi nhận thanh toán; lưu confirmed_by/confirmed_at. Admin chỉ xác nhận đơn đã thanh toán hợp lệ và không được tự đổi payment thất bại/chưa thanh toán thành SUCCESS.
- Lưu mốc bắt đầu/kết thúc gói mới đã tính khi admin xác nhận, dùng lại cho lần xử lý lặp; không ghi lại confirmed_at hoặc tính ngày bắt đầu mới khi admin bấm xác nhận lần hai.
- Ràng buộc một yêu cầu CHANGE đang mở trên mỗi chủ trọ xuyên suốt chờ thanh toán, chờ admin và SCHEDULED; không chỉ chặn tại thời điểm tạo subscription. Dùng transaction và ràng buộc database phù hợp để hai yêu cầu đồng thời không vượt giới hạn.
- Thêm `service_subscription_periods`: FK subscription, FK đơn tạo kỳ duy nhất, thứ tự kỳ, start_at/end_at và snapshot tên gói, giới hạn tin, quyền thống kê, suất cao/thấp, mức mặc định. Giá và số tháng từng lần mua giữ trong đơn, có thể truy ngược từ kỳ.
- Các kỳ của một subscription nối tiếp và không chồng lấn; kiểm tra trong transaction khóa chủ trọ/subscription. Khi gia hạn, thêm kỳ với snapshot mới và kéo dài end_at tổng; không sửa snapshot hoặc ngày kết thúc của kỳ đang chạy.
- Snapshot theo kỳ là nguồn quyền duy nhất cho gói trả phí; không duy trì một bản quyền lợi tổng ở subscription rồi dùng lẫn với snapshot kỳ. Chọn kỳ đang chạy bằng start_at <= now < end_at.
- Không thêm bảng/entity phân bổ suất. Thêm trực tiếp vào listings: FK `service_subscription_period_id` nullable và `package_priority_level` mặc định 0. Mức 1/2 bắt buộc có kỳ; mức 0 bắt buộc kỳ null. Một dòng tin chỉ chứa một lựa chọn ưu tiên theo gói, không cần unique riêng hoặc lịch sử cấp/giải phóng.
- Quyền ưu tiên theo gói chỉ có hiệu lực khi kỳ được tham chiếu còn hiệu lực. Service đếm các tin PUBLISHED còn hạn có cùng kỳ/mức để xét số suất và kiểm tra quyền sở hữu; FK/check không thay thế kiểm tra tổng quota. Rút suất cập nhật đồng thời mức 0 và kỳ null.
- `boost_service_package_subscriptions` giữ lịch sử Boost mua riêng, thêm snapshot tên/mức/số tháng, trạng thái chờ thanh toán và thời gian null trước kích hoạt. FK gói Boost vẫn bắt buộc khi chỉ triển khai mua riêng.
- Không thêm schema Boost tặng vào MVP; bảng đăng ký Boost chỉ lưu mua riêng và không cần cột nguồn hoặc FK chu kỳ tặng.
- `payments` thay `reference_id` bằng FK tới `service_subscription_orders` hoặc đăng ký Boost, bắt buộc đúng một đích và khớp type. Không tiếp tục chỉ trỏ payment trực tiếp tới subscription vì một subscription có nhiều lần gia hạn.
- Thêm mã tham chiếu thanh toán duy nhất, hạn chờ, mã giao dịch VNPay và unique theo nhà cung cấp/mã giao dịch khi có giá trị. Một đơn có thể có nhiều lần thử, nhưng chỉ một lần được cấp quyền.
- Ràng buộc một Boost độc lập `ACTIVE` trên mỗi tin; ràng buộc giá/số suất không âm, số tháng đúng lựa chọn, thời gian và liên kết hợp lệ. Mức ưu tiên gói và Boost độc lập được kiểm soát riêng.
- Chuyển dữ liệu `APPROVED` sang `PUBLISHED` an toàn trước khi thu hẹp enum SQL; cập nhật Java enum và các tham chiếu liên quan.
- Thêm `listings.submitted_at`, `pending_expires_at`, `published_at`; expires_at chỉ là hạn hiển thị của lần xuất bản. Giữ approved_at làm dấu vết duyệt. PENDING hợp lệ cần submitted_at/pending_expires_at, PUBLISHED hợp lệ cần published_at/expires_at; không dùng một deadline cho hai trạng thái.
- Dữ liệu cũ chỉ backfill thời điểm từ nguồn có căn cứ, ghi rõ giá trị suy ra nếu cần; không coi ngày tạo luôn là ngày xuất bản hoặc tự kéo dài tin lịch sử mà không có căn cứ.
- Index cho quota theo chủ trọ qua phòng, trạng thái/pending_expires_at hoặc expires_at; kỳ theo subscription/start_at/end_at; listings theo kỳ/mức ưu tiên/trạng thái/hạn; gói SCHEDULED/Boost đến hạn; đơn/payment và lịch sử chủ trọ.
- Với quota Free không có subscription để khóa, dùng bản ghi chủ trọ ổn định làm điểm khóa chung. Thống nhất thứ tự khóa trong các luồng đếm quota, gia hạn, đổi gói và cấp suất.
- Seed Free; gói trả phí được admin cấu hình cùng giá/thời hạn hợp lệ. Giữ ngày bắt đầu/kết thúc lịch sử; không chuyển ngày sang tháng bằng phép chia cho 30 và không bịa snapshot lịch sử thiếu dữ liệu.

Hoàn thành khi: migration chạy trên MySQL thử nghiệm mới và dữ liệu trước thay đổi; JPA validate khớp; kiểm tra ràng buộc và bảo toàn lịch sử; schema thể hiện được kỳ quyền lợi, hạn chờ duyệt và SCHEDULED theo quy tắc đã chốt.

### Phần 2 — Admin quản lý danh mục và bảng giá

Trạng thái: hoàn thành. Đã có 2 controller, 2 service/interface/impl, 3 repository, mapper và DTO theo cấu trúc hiện có; không thêm bảng hoặc dependency. Admin tạo/sửa/bật/ngừng bán, chủ trọ chỉ đọc danh mục khả dụng. MockMvc chạy cùng MySQL thật đã kiểm tra phân quyền, validation, Free, giá đúng gói và bảo toàn snapshot; tổng 13 test cùng phần 1 đều qua. Chi tiết endpoint xem mục 8–9 trong `api.md`.

- Thêm controller, DTO, mapper, repository và service/interface/impl cần thiết trong `subscription`, theo mẫu đang có.
- API danh sách phân trang, chi tiết, tạo/sửa và ngừng bán gói; quản lý giá cho thời hạn 1/3/6/12 tháng của từng gói dịch vụ và 1/3/6 tháng của Boost.
- Chỉ admin được thay đổi cấu hình; chủ trọ chỉ đọc gói/lựa chọn giá đang bán.
- Validate mã duy nhất, hạn mức/suất, mức mặc định, giá và danh sách thời hạn; cấu hình Business không dùng số rất lớn thay cho không giới hạn.
- Giữ Free làm gói mặc định; không xóa gói hoặc lựa chọn đã được giao dịch tham chiếu. Ngừng bán chỉ chặn đơn mới, không sửa snapshot đã mua.
- DTO/config MVP không có Boost tặng. Giá/quyền lợi admin sửa chỉ dùng cho đơn mới; không cập nhật các kỳ đã mua hoặc SCHEDULED.

Hoàn thành khi: admin quản lý được quyền lợi và từng mức giá; chủ trọ/tenant không chỉnh được; thay danh mục không làm thay đổi quyền đã mua.

### Phần 3 — Đăng ký, gia hạn và đổi gói

Đã có controller, DTO, mapper, repository và service theo module hiện tại. Đơn chờ thanh toán 15 phút; service không cung cấp endpoint tự đánh dấu đã trả tiền. Luồng nội bộ kiểm tra Payment SUCCESS trước khi cấp quyền; VNPay/IPN sẽ kết nối ở phần 4. Các API nghiệp vụ đồng bộ chuyển kỳ/SCHEDULED đến hạn dưới khóa chủ trọ; tác vụ quét nền toàn hệ thống vẫn thuộc phần 7.

- Chủ trọ chọn gói và số tháng, server lấy giá/quyền lợi rồi tạo đơn `PENDING_PAYMENT`; không nhận giá, chủ sở hữu, hạn gói hoặc trạng thái kích hoạt do client tự đặt.
- API xem quyền hiện tại, hạn gói, lịch sử subscription, đơn mua/gia hạn và thay đổi đã đặt cho kỳ tới.
- Đăng ký mới/gia hạn sau hết hạn tạo subscription mới sau khi trả tiền hợp lệ.
- Gia hạn trước hạn giữ subscription hiện tại, áp dụng đúng một lần sau thanh toán: khóa và đọc lại end_at, thêm kỳ mới bắt đầu tại mốc đó với snapshot đã mua, rồi kéo dài end_at tổng. Hai gia hạn hợp lệ không được chồng kỳ hoặc làm mất thời gian của nhau.
- Chặn tạo đơn gia hạn gói hiện tại khi có SCHEDULED; kiểm tra lại trong transaction áp dụng đơn để chặn cả payment của đơn gia hạn đã tạo trước khi SCHEDULED xuất hiện. Khoản thu không áp dụng được đưa về đối soát, không kéo dài gói cũ hoặc dời lịch gói mới.
- Nâng/hạ: tạo đơn, nhận thanh toán VNPay, chờ admin xác nhận; chỉ cho một yêu cầu đổi gói mở. Khi admin xác nhận, khóa cùng bản ghi chủ trọ với luồng gia hạn và đọc hạn tổng mới nhất; nếu còn hạn thì tạo SCHEDULED, nếu đã hết hạn thì kích hoạt ngay từ confirmed_at. Không chuyển quyền ngay khi IPN về.
- Bổ sung API admin xác nhận đơn đổi gói và API chủ trọ xem trạng thái đã thanh toán/chờ xác nhận/SCHEDULED; kiểm tra quyền trên backend.
- Khi đến hạn, kết thúc subscription cũ rồi kích hoạt SCHEDULED và kỳ đầu của gói mới đúng một lần. Xác nhận muộn không làm mất thời gian mua và không tự gia hạn bù giai đoạn gói cũ đã hết hạn.
- API quyền hiện tại trả riêng end_at tổng của subscription, kỳ đang hưởng quyền và ngày bắt đầu kỳ kế tiếp; không hiển thị snapshot tương lai như quyền đang có.
- Khi đọc quyền, kiểm tra cả subscription ACTIVE lẫn kỳ chứa thời điểm hiện tại; người có gói trả phí hết hạn không được dùng Free để đăng tiếp.
- Không tự thay snapshot kỳ đã mua khi admin sửa gói. Phần gia hạn nhận cấu hình mới từ kỳ mới, kể cả subscription vẫn giữ cùng ID.
- Không tự động trừ tiền/gia hạn; đây là các thao tác chủ trọ chủ động thực hiện.

Hoàn thành khi: đơn chưa trả tiền không nâng quyền; gia hạn cộng hạn một lần và thêm kỳ không chồng lấn; quyền cũ giữ đến cuối kỳ; chỉ một SCHEDULED và gia hạn gói cũ bị chặn; xác nhận trước/đúng/sau hạn cho đúng ngày kích hoạt; lịch sử giá/quyền lợi từng kỳ không bị ghi đè.

### Phần 4 — VNPay Sandbox và cấp quyền sau thanh toán

Đã có code và kiểm thử callback có chữ ký trên schema riêng, hóa đơn một lần, khóa chung chủ trọ và đánh dấu khoản thu cần đối soát. Chỉ mở công khai GET Return/IPN; Return không cấp quyền. Chưa nghiệm thu đường truyền VNPay thực tế do chưa có URL IPN công khai/cấu hình merchant để chạy giao dịch trực tiếp. Các tiêu chí cần VNPay gọi thật bên dưới vẫn chờ bước cấu hình đó.

- Bổ sung config/service/controller/DTO/repository trong module `payment` theo cách tổ chức hiện có; không thêm triển khai MoMo hoặc chuyển khoản.
- Khi triển khai, cấu hình terminal, secret, URL thanh toán, Return URL và IPN qua runtime/local an toàn; không sao chép secret vào file đang được Git theo dõi.
- Tạo URL thanh toán từ đơn phía server và mã tham chiếu duy nhất; số tiền lấy từ snapshot và chuyển sang định dạng VNPay bằng số học chính xác.
- Theo tài liệu VNPay: ký/xác minh dữ liệu bằng thuật toán phù hợp phiên bản 2.1.0, mã hóa/sắp xếp tham số đúng quy tắc; số tiền gửi là VND nhân 100 và thời gian theo GMT+7.
- Tách Return URL khỏi IPN: Return không tự đánh dấu thành công hoặc cấp quyền. IPN kiểm tra chữ ký, terminal, tham chiếu, số tiền và mã kết quả rồi cập nhật; phản hồi đúng giao thức VNPay, không bọc response IPN bằng ApiResponse.
- Chỉ mở đúng endpoint Return/IPN không cần JWT; các API tạo đơn/xem lịch sử vẫn kiểm tra xác thực và chủ sở hữu.
- Trong transaction ngắn, khóa các bản ghi cần thiết, ghi nhận payment thành công và áp dụng mua mới/gia hạn/Boost một lần. Riêng đổi gói chỉ chuyển sang chờ admin xác nhận; IPN không tự tạo ACTIVE hoặc bỏ qua bước xác nhận. Không gọi mạng ngoài khi đang giữ khóa database.
- IPN lặp hoặc thứ tự Return/IPN đảo không làm cộng hạn, tạo kỳ, tạo yêu cầu chờ xác nhận hoặc hóa đơn hai lần. Admin xác nhận lặp cũng không tạo nhiều SCHEDULED.
- Nếu có nhiều lần thử đều trả tiền thành công, hoặc tiền về khi tin/đơn không còn đủ điều kiện, lưu đúng khoản thanh toán và trạng thái cần đối soát; không cấp chồng quyền hoặc giả định chưa thu tiền.
- Tạo hóa đơn một lần cho payment thành công theo mô hình Invoice hiện có; unique payment nếu một payment chỉ có một hóa đơn.
- API theo dõi kết quả cho chủ giao dịch/admin; không lưu/log secret hoặc chuỗi ký chứa secret. Đối chiếu chi tiết với tài liệu VNPay được liên kết ở mục 3.8.

Hoàn thành khi: tạo URL sandbox đúng, VNPay gọi được IPN, trả tiền hợp lệ cấp đúng quyền; sai chữ ký/số tiền không cấp quyền; thông báo lặp không nhân quyền; Return không thay thế IPN; giao dịch lỗi/hết hạn có trạng thái rõ.

### Phần 5 — Luồng tin đăng và kiểm tra quota

Đã có API tạo/gửi lại/ẩn/sửa tin không còn giữ quota, admin duyệt/từ chối và danh sách tin công khai. Quota đọc dữ liệu mới nhất dưới khóa chủ trọ, chỉ tính PENDING/PUBLISHED còn hạn và loại chính tin đang thao tác trước khi cộng 1. Đã kiểm tra quota Free đồng thời, PENDING 7 ngày, tháng hiển thị mới và gói trả phí hết hạn.

- Bổ sung luồng tối thiểu gửi duyệt, duyệt, hiển thị và đăng lại theo cấu trúc module `listing`; không coi đây là chỉnh một service đã có.
- Dùng `PENDING` và `PUBLISHED` nhất quán; gắn quyền sở hữu qua phòng của chủ trọ.
- Trước gửi duyệt/duyệt/đăng lại: khóa bản ghi chủ trọ, đọc quyền còn hiệu lực, đếm tin theo quy tắc ở mục 3.2 và áp dụng thay đổi trong cùng transaction.
- Khi gửi duyệt/giữ chỗ lại, đặt submitted_at = now và pending_expires_at = now.plusDays(7). Quota PENDING dựa vào pending_expires_at; không dựa vào expires_at đang null hoặc còn lưu từ lần xuất bản trước.
- PENDING hết 7 ngày mất chỗ ngay theo truy vấn thời gian, đồng bộ EXPIRED bằng tác vụ và cho phép gửi duyệt lại khi đủ điều kiện. Không tự kéo dài thời gian giữ chỗ vì admin chưa xử lý.
- Duyệt một tin đã giữ chỗ không cộng hai lần; nếu gói hết hạn trước khi duyệt, không được xuất bản bằng quyền cũ.
- Duyệt PENDING còn hiệu lực: đặt approved_at/published_at tại lúc duyệt và expires_at = published_at.plusMonths(1). Không trừ 7 ngày chờ duyệt vào tháng hiển thị.
- Đăng lại tin hết hạn luôn chuyển PENDING sau khi kiểm tra quyền/quota và đặt lại hạn chờ 7 ngày; không có endpoint bỏ qua duyệt để xuất bản ngay. Admin kiểm tra nội dung, giá thuê và tình trạng phòng rồi duyệt; khi đó mới đặt published_at mới và expires_at mới sau 1 tháng. Không đổi ngày đăng chỉ vì sửa nội dung hoặc mua Boost.
- Giữ tin PUBLISHED đến expires_at dù gói hết hạn/hạ gói; không tự xóa hoặc ẩn khi vượt hạn mức mới.
- Nếu dùng quyền thống kê trong phạm vi triển khai, kiểm tra snapshot kỳ đang hiệu lực; hết hạn gói không xóa dữ liệu thống kê cũ.

Hoàn thành khi: hạn mức từng gói đúng; PENDING mất chỗ chính xác sau 7 ngày và gửi lại được nếu đủ quyền; PUBLISHED được đủ 1 tháng từ duyệt; đồng thời gửi/duyệt/đăng lại không vượt quota; duyệt ở quota đầy không đếm trùng; hạ gói/chuyển kỳ không làm mất tin cũ.

### Phần 6 — Suất ưu tiên và Boost độc lập

Đã có API cấp/rút/đổi slot cho PUBLISHED còn hạn, Business mặc định thấp, mua Boost 1/3/6 tháng và cấp quyền sau IPN. Chỉ một Boost ACTIVE trên tin; khoản thu không thể cấp quyền được giữ SUCCESS để đối soát. Danh sách tin sắp xếp bằng quyền còn hiệu lực trong truy vấn, response tính cùng điều kiện; không dùng priority_level lưu sẵn làm nguồn quyền. Cột này chỉ giữ tương thích, cập nhật khi thao tác tin/Boost và đồng bộ nền. Không có Boost tặng.

- API cấp/đổi/rút suất cao/thấp cho tin của chủ trọ; cập nhật hai trường ưu tiên trong listings, đếm theo hạn mức snapshot của kỳ và transaction khóa thống nhất. Không tạo bản ghi lịch sử phân bổ.
- Business tự áp dụng thấp cho phần còn lại; không tạo bản ghi mức thấp cho mọi tin.
- API mua Boost cao 1/3/6 tháng cho một tin PUBLISHED còn hạn; kiểm tra lại khi IPN thành công và chống hai đơn cấp chồng Boost.
- Mỗi tin một Boost độc lập còn hiệu lực; lịch sử quá hạn không cản mua mới. Không xếp lịch Boost tương lai hoặc chuyển Boost giữa các tin.
- Trước kích hoạt Boost mới, chuyển bản ghi ACTIVE đã quá end_at sang EXPIRED trong cùng transaction để unique trạng thái không chặn nhầm khi tác vụ hết hạn chưa chạy.
- Không cắt hạn Boost theo hạn gói; tin phải còn hiệu lực mới được hiển thị ưu tiên. Boost không gia hạn expires_at.
- Tính lại mức từ mọi quyền còn hiệu lực khi gói/Boost/phân bổ đổi; Boost hết hạn không luôn đặt tin về 0.
- Danh sách tin lọc trạng thái/hạn/bộ lọc trước, rồi priority DESC, published_at DESC, ID; truy vấn không được ưu tiên quá hạn khi tác vụ cập nhật chạy chậm.
- Nếu giữ Listing.priorityLevel làm giá trị ưu tiên hiển thị lưu sẵn, phải đồng bộ ở mọi luồng ảnh hưởng; không coi cột này là nguồn xác định quyền duy nhất. Trường này khác packagePriorityLevel: Boost cao không ghi đè hoặc làm mất lựa chọn mức thấp theo gói.
- Không triển khai Boost tặng hoặc chu kỳ cấp lượt trong MVP. Quota tin/slot không có tác vụ reset hằng tháng.
- Khi sang kỳ quyền lợi mới, phân bổ thuộc kỳ cũ mất hiệu lực; không dùng snapshot hoặc slot cũ để vượt quyền kỳ mới. Giữ phương án hiện tại là chủ trọ chọn suất trong kỳ mới, không tự chọn tin để giữ ưu tiên cao khi quota giảm.

Hoàn thành khi: đủ giới hạn slot theo snapshot kỳ; đổi tin trả lại suất; ưu tiên đúng và mới nhất; chuyển kỳ không tiếp tục dùng suất cũ vượt hạn mức; Boost không tăng quota tin và còn chạy khi gói hết hạn; chỉ một Boost mua riêng hoạt động trên mỗi tin.

### Phần 7 — Hết hạn và ranh giới chu kỳ

Đã có tác vụ chạy mỗi phút, transaction riêng theo chủ trọ để đồng bộ gói/đơn/payment/tin/Boost và ưu tiên lưu sẵn. Truy vấn quota và hiển thị không phụ thuộc việc tác vụ đã chạy. Đã kiểm tra tác vụ lặp không nhân kỳ/hóa đơn, hạn mức mới không ẩn tin cũ, slot kỳ cũ mất hiệu lực và IPN hợp lệ đến sau khi đơn/payment đã được đánh dấu hết hạn.

- Tác vụ định kỳ cập nhật gói/Boost/tin hết hạn, đơn chờ quá hạn, giải phóng suất không còn hợp lệ và tính lại mức ưu tiên.
- Gia hạn trước hạn thay end_at tổng và thêm kỳ sau giao dịch hợp lệ; tác vụ không kết thúc subscription ở mốc hết hạn cũ nhưng vẫn chuyển quyền giữa các kỳ tại mốc đó.
- Chuyển snapshot kỳ đúng ranh giới [start_at, end_at); không cập nhật quyền lợi sớm khi thanh toán hoặc khi admin sửa danh mục.
- Với đổi gói, chỉ chuyển SCHEDULED đã trả tiền và được admin xác nhận sang ACTIVE; đơn chỉ mới thanh toán chưa xác nhận không đủ điều kiện. Subscription cũ kết thúc trước khi chuyển trạng thái để không vi phạm một ACTIVE.
- Ngày kích hoạt/kết thúc được giữ theo mốc đã xác nhận; tác vụ chạy trễ không đặt lại start_at theo giờ chạy hoặc tự bù/dời lịch. Xác nhận admin muộn đã kích hoạt ngay theo confirmed_at trong luồng xác nhận, không cần chờ tác vụ.
- Truy vấn/ghi nghiệp vụ phải nhận diện kỳ/gói đã đến hạn ngay cả khi tác vụ chạy chậm; xử lý chuyển trạng thái trong transaction khi cần để không dùng quyền cũ hoặc chặn quyền mới hợp lệ.
- Mỗi lần cấp quyền kiểm tra thời gian thực tế; gói cũ hết hạn mất quyền ngay, tin cũ và Boost độc lập vẫn theo hạn của chúng.
- Hạ gói không xóa/ẩn tin; gói mới không kế thừa slot vượt giới hạn. Phân bổ theo gói cũ được kết thúc hoặc chuyển theo chính sách đã chốt, không đổi Boost độc lập.
- PENDING đủ 7 ngày được giải phóng chỗ và đồng bộ EXPIRED; không chờ tác vụ mới loại khỏi quota. PUBLISHED hết 1 tháng không còn hiển thị dù tác vụ chưa cập nhật trạng thái.
- Không xóa lịch sử subscription, đơn, payment, hóa đơn hoặc Boost.

Hoàn thành khi: chạy lại tác vụ không phát sinh tác dụng phụ; ranh giới hết hạn/gia hạn/đổi gói đúng; tác vụ chạy chậm không cho vượt quyền hoặc tạo khoảng trống quyền sai chính sách.

### Phần 8 — Kiểm chứng và tài liệu

- Kiểm thử cùng từng phần, dùng JUnit/Spring test hiện có; chỉ thêm dependency khi cần kiểm chứng thực tế.
- Migration, enum, unique và khóa đồng thời phải được kiểm tra trên MySQL thử nghiệm; mock repository không thay thế kiểm chứng database.
- Quota: Free có/không có subscription, đúng trước/tại/sau hạn PENDING 7 ngày, gửi lại sau hết hạn, PUBLISHED 1 tháng từ duyệt, ẩn/từ chối, duyệt ở quota đầy, đồng thời gửi/duyệt/đăng lại.
- Slot: cấp cao/thấp, đổi tin, hết suất, Business không giới hạn và mức thấp mặc định, hết hạn gói nhưng tin vẫn hiển thị.
- Thời gian: gói 1/3/6/12, Boost 1/3/6, ngày cuối tháng/năm nhuận, đúng tại end_at, gia hạn trước/sau hạn và mua hai lần đồng thời.
- Gia hạn/đổi gói: thêm kỳ và cộng hạn đúng một lần; admin sửa quyền lợi không đổi kỳ đã mua; trước/tại/sau ranh giới kỳ trả đúng snapshot; hai gia hạn không chồng kỳ; không tự mất tin khi hạ quota.
- Đổi gói trả trước: IPN chỉ ghi nhận đã thanh toán, admin xác nhận mới tạo SCHEDULED hoặc ACTIVE tùy mốc hết hạn; tối đa một yêu cầu đang mở kể cả thao tác đồng thời; xác nhận/tác vụ lặp không kích hoạt hai lần; admin không xác nhận được đơn chưa trả tiền.
- Xác nhận đổi gói trước/đúng/sau hạn; ví dụ hết hạn 30/11, xác nhận 03/12 phải bắt đầu 03/12; xác nhận lặp giữ nguyên confirmed_at/start_at và không cộng thêm hạn.
- Đã SCHEDULED thì chặn tạo lẫn áp dụng gia hạn; race giữa admin xác nhận, IPN gia hạn và tác vụ kích hoạt được tuần tự hóa; không tự dời SCHEDULED hoặc cấp thêm quyền khi tiền về muộn.
- Đăng lại không được chuyển thẳng PUBLISHED; luôn giữ quota PENDING tối đa 7 ngày, kiểm tra lại khi duyệt và chỉ bắt đầu tháng hiển thị mới tại lúc duyệt.
- VNPay: ký/mã hóa tham số, số tiền, callback sai chữ ký/terminal/tham chiếu, IPN lặp, Return đến trước IPN, thanh toán thành công nhưng điều kiện cấp quyền đã đổi và khoản thu trùng.
- Boost: mua cho tin người khác/tin hết hạn bị chặn; không có hai Boost hoạt động; Boost chạy qua hạn gói; hết Boost trả về đúng ưu tiên còn lại.
- MVP không có schema, API hoặc tác vụ Boost tặng; không có việc reset quota tin/slot theo tháng.
- Cập nhật docs/api.md sau mỗi phần: endpoint, quyền, request/response, lỗi, PUBLISHED, hạn chờ duyệt/hiển thị, kỳ quyền lợi, trạng thái chờ admin/SCHEDULED và hướng dẫn sandbox không chứa secret.
- Chỉ đánh dấu hoàn thành phần đã có bằng chứng; báo rõ thiếu môi trường/callback nếu chưa kiểm thử VNPay thực tế.

Hoàn thành khi: luồng Free → mua gói → thanh toán → gửi/duyệt tin → cấp suất/mua Boost → gia hạn/đổi gói → hết hạn được kiểm chứng, tài liệu khớp và các câu hỏi ảnh hưởng phần đã làm được chốt.

## 6. Bước tiếp theo

- Các quyết định nghiệp vụ đã được cập nhật; không hỏi lại các lựa chọn đã chốt. Chưa mở rộng sang hủy/hoàn tiền SCHEDULED hoặc Boost tặng.
- Code backend phần 1–7 và QueryDr đã có. Khi test local, sau Return gọi POST `/api/payments/{id}/check` để xác nhận qua VNPay mà không cần IPN công khai. Khi triển khai xác nhận tự động, cấu hình URL IPN phía merchant và kiểm tra VNPay gọi về thực tế.
- Giá bán cần admin cấu hình; secret chỉ cấp qua môi trường, không ghi vào source/tài liệu hoặc file Git đang theo dõi.
- Chưa chạy migration vào database ứng dụng. Trước áp dụng, kiểm tra guard dữ liệu cũ trong `SUBSCRIPTION_SCHEMA.md`; không xóa lịch sử hoặc bỏ guard để ép migration.
- Thực hiện các bước kiểm tra trực tiếp trong `VNPAY_SANDBOX.md` sau khi backend được cấu hình và nhận được IPN từ VNPay. Chưa mở rộng MVP sang refund, hủy SCHEDULED hoặc Boost tặng.
