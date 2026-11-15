# Cộng đồng và kiểm duyệt — tình trạng triển khai

Tài liệu này mô tả mã nguồn hiện có, khác với toàn bộ phạm vi đề xuất trong [bản thiết kế](community-feed-design.md). Các mẫu HTML trong frontend là tài liệu tham khảo; giao diện chạy bằng Angular, không nhúng nguyên trang HTML/Tailwind/CDN của mẫu.

## Phần đã triển khai

- Bảng tin công khai, tab Mới nhất, Đang theo dõi, Quanh tôi; tìm từ khóa/hashtag trong nội dung bằng MySQL. Quanh tôi hiện dùng chuỗi khu vực người viết nhập, đối chiếu chính xác; chưa lấy địa bàn tự động từ hồ sơ nhu cầu.
- Phân trang cursor `(created_at,id)` cho bài/bình luận, phân trang riêng cho reply; nút Xem thêm.
- Bài của tôi, bài đã lưu; tạo/sửa có kiểm tra version, xóa mềm bài, nhãn đã chỉnh sửa.
- Upload JPEG/PNG qua ImageStorageService/Cloudinary, 5 MiB/ảnh, tối đa 10 ảnh/bài. Backend kiểm tra nội dung ảnh theo validator hiện có; chưa bật WebP.
- Tìm listing công khai theo tiêu đề, đính kèm tối đa 3 tin. Mỗi lần đọc kiểm tra lại điều kiện công khai theo ListingRepository; tin không còn hợp lệ hiển thị placeholder.
- Bình luận hai cấp, sửa/xóa, placeholder gốc đã xóa; cảm xúc LIKE/LOVE/WOW cho bài và bình luận. UI bình luận hiện cung cấp nút Thích.
- Lưu bài, theo dõi tác giả; các thao tác đặt trạng thái dùng PUT/DELETE để không tăng trùng.
- Tác giả xóa bình luận trong bài của mình; chủ trọ không được xóa đánh giá hợp lệ của khách thuê.
- Giới hạn tạo bài 5/giờ, bình luận 30/giờ theo dữ liệu MySQL và khóa user. Tài khoản mới dưới 24 giờ giảm một nửa, làm tròn xuống. Chặn bài cùng text trong 10 phút của cùng người viết.
- Report bài/bình luận; ngưỡng 3 người khác nhau ACTIVE, email đã xác minh, tài khoản từ 24 giờ tự ẩn nội dung. Unique bảo vệ report trùng trên cùng phiên bản; giới hạn 10 report/ngày.
- Từ khóa TOKEN/PHRASE/DOMAIN, chuẩn hóa Unicode/zero-width, tùy chọn khớp không dấu, phạm vi dùng chung, bật/tắt, version cấu hình.
- Hàng đợi admin xem snapshot bằng chứng, xử lý VISIBLE/RESTRICTED/HIDDEN/REMOVED, kiểm tra bản hiện tại trước khi duyệt; ghi audit quyết định và đổi rule.
- RESTRICTED thu gọn nội dung công khai; người đăng nhập có thể chủ động tải text thuần của bài/bình luận, không kèm ảnh hoặc thẻ listing. Toàn văn không có trong JSON public ban đầu.
- Tác giả gửi yêu cầu xét lại bài bị hạn chế/chờ duyệt/ẩn/gỡ, tối đa một yêu cầu mỗi phiên bản; hiện đưa vào hàng đợi với source APPEAL.
- Admin đánh dấu ghim bài; hiện có nhãn ghim, chưa có vùng ghim tách riêng đầu feed.
- Thông báo bình luận/trả lời, follow và quyết định duyệt qua notification_outbox hiện có; link thông báo cộng đồng mở đúng bài. Không đưa chữ bị ẩn vào thông báo tương tác.
- Chi tiết bài và bình luận được tải lại mỗi 5 giây khi tab đang hiển thị; đây là polling, chưa phải WebSocket cộng đồng.

## Đánh giá listing: yêu cầu bắt buộc được giữ nguyên

- Không có API hoặc khu vực bình luận tự do trên listing.
- Tạo đánh giá vẫn qua ReviewServiceImpl: đúng người thuê của hợp đồng, trạng thái ACTIVE/EXPIRED/TERMINATED, xác nhận hai bên, đã đến ngày bắt đầu, không tự đánh giá, một lần/hợp đồng/loại.
- Chỉ sau khi kiểm tra điều kiện thuê mới chạy bộ lọc phần chữ. Điểm 1–5 không bị đổi bởi bộ lọc từ khóa.
- room_reviews/landlord_reviews có text_status và reply_status độc lập; is_hidden cũ vẫn có nghĩa ẩn toàn bộ đánh giá.
- Public mapper che chữ vi phạm, giữ điểm sao; phản hồi chủ trọ bị kiểm duyệt không che lời đánh giá hợp lệ của người thuê. DTO thêm trạng thái và listing UI hiển thị nhãn tương ứng.
- replyRate chỉ tính phản hồi VISIBLE.
- Phần chữ review bị RESTRICTED hiện hiển thị thông báo thay thế; chưa có nút mở toàn văn hoặc yêu cầu xét lại riêng review trên listing. Admin vẫn xử lý được hồ sơ tự động của review.
- tenant_reviews không được thay đổi nghiệp vụ trong đợt này.

## Đường dẫn giao diện

| URL frontend | Chức năng |
| --- | --- |
| /community | Bảng tin; chọn Bài của tôi/Bài đã lưu ở cột trái |
| /community/create | Tạo bài, yêu cầu đăng nhập |
| /community/create?edit={postId} | Sửa bài của mình |
| /community/posts/{postId} | Chi tiết và bình luận |
| /admin/moderation | Hàng đợi và tab cấu hình từ khóa |
| /tro/{listingId} | Đánh giá listing đã xác thực thuê hiện có |

Mobile ẩn hai cột phụ và giữ nội dung chính. Chưa có kiểm thử bằng trình duyệt/screenshot với backend đang chạy trong phiên triển khai này.

## API hiện có

Tất cả nằm dưới `/api`. Response dùng ApiResponse hiện có. Xem chữ ký DTO trong `community/dto/CommunityDtos.java`.

| Endpoint | Mô tả |
| --- | --- |
| GET /community/feed?tab=LATEST\|FOLLOWING\|NEARBY\|SAVED\|MINE&cursor=&q=&area= | Slice gồm items, nextCursor, hasMore; 10 item/trang |
| GET /community/listings?q= | Tìm tối đa 20 tin công khai theo tiêu đề |
| GET/POST /community/posts/{id} hoặc /community/posts | Đọc chi tiết / tạo bài |
| PUT/DELETE /community/posts/{id} | Sửa với expectedVersion / xóa mềm |
| POST /community/media | Multipart part tên file, trả id và url |
| GET/POST /community/posts/{id}/comments | GET có parent/cursor tùy chọn; POST content,parentId,replyToId |
| PUT /community/posts/{postId}/comments/{id} | Sửa với expectedVersion |
| DELETE /community/comments/{id} | Xóa bình luận |
| PUT/DELETE /community/content/{POST\|POST_COMMENT}/{id}/reaction | Body PUT có type LIKE/LOVE/WOW |
| PUT/DELETE /community/posts/{id}/save | Lưu/bỏ lưu |
| PUT/DELETE /community/users/{id}/follow | Theo dõi/bỏ theo dõi |
| POST /community/content/{POST\|POST_COMMENT}/{id}/report | Body reason, server xác định content version |
| POST /community/content/{POST\|POST_COMMENT}/{id}/reveal | Trả text RESTRICTED, yêu cầu đăng nhập và ngữ cảnh hợp lệ |
| POST /community/posts/{id}/appeal | Body reason, chỉ tác giả |
| PUT /community/posts/{id}/pin?pinned=true | Chỉ admin, bài VISIBLE |
| GET/POST /admin/moderation/rules | Danh sách/tạo rule |
| PUT /admin/moderation/rules/{id} | Sửa rule kèm version |
| POST /admin/moderation/rules/test | Body scope,text; thử ruleset đã lưu |
| GET /admin/moderation/cases?resolved=false&page=0 | Tối đa 50 hồ sơ/trang; không trả review cho admin thiếu REVIEW_READ |
| POST /admin/moderation/cases/{id}/decisions | Body status,reason,expectedVersion (contentVersion của hồ sơ) |

Phản hồi giữ `parentId` là bình luận gốc của luồng, đồng thời lưu `replyToId` là đúng bình luận được chọn (có thể là một phản hồi). `replyToId` phải cùng bài và cùng luồng, đang hiển thị, chưa xóa, tác giả hoạt động; tham chiếu khác luồng/bài bị từ chối. Response trả `replyToId`, `replyToAuthorName` để FE gắn phản hồi dưới đúng bình luận được chọn. Nếu bình luận đích bị xóa/ẩn hoặc tác giả bị khóa, không trả tên; FE dùng nhãn không còn hiển thị. Sửa nội dung giữ nguyên đích trả lời đã lưu. Thông báo phản hồi mới gửi đến tác giả bài và người được trả lời, loại người gửi và trùng lặp. Migration `V15__community_reply_target.sql` thêm FK tự tham chiếu; dữ liệu cũ không được suy đoán/backfill người nhận.

Quyền admin: MODERATION_READ, MODERATION_HANDLE, MODERATION_RULE_MANAGE; duyệt review yêu cầu thêm REVIEW_MODERATE. Migration cấp quyền mặc định cho role ADMIN có sẵn. Quyền quản lý vẫn được backend tra DB, không chỉ dựa vào nút trên UI.

## Database, khởi chạy và kiểm thử

- Migration schema: `V11__community_and_content_moderation.sql`; seed cộng đồng tự chạy ở `V12__seed_community_demo_data.sql` sau V2/V11. `scripts/setup-demo.ps1 -CommunityPosts 600` tích hợp việc sinh và nạp dữ liệu khi dựng database mới. Không sửa migration đã áp dụng. Xem [hướng dẫn seed](../scripts/seed-community.README.md).
- Cần MySQL hoạt động để kiểm chứng migration/API thực tế; Cloudinary cho upload và RabbitMQ/relay hiện có để giao thông báo đầy đủ.
- Chạy backend và frontend theo cấu hình sẵn có của hai repo, rồi đăng nhập lại admin/làm mới quyền để thấy menu Kiểm duyệt cộng đồng.
- Bộ từ khởi tạo có vcl/đm ở mức hạn chế; cụm xúc phạm rõ nghĩa bị ẩn; “việc nhẹ lương cao” chờ duyệt; domain `.invalid` chỉ là dữ liệu demo.
- Bảng community/moderation dùng JDBC, chủ quản được ghi tại các service triển khai. Các bộ kiểm thử tự động đã được tạm gỡ ngày 15/11/2026 theo yêu cầu, sẽ được bổ sung lại sau; hiện kiểm chứng bằng build và chạy chức năng thủ công.

Lưu ý về ảnh: storage hiện có cấp URL Cloudinary công khai. Ẩn bài sẽ ngừng trả URL ở API public; một URL đã phát trước đó vẫn có thể truy cập trực tiếp. Cần delivery private/signed và chính sách thu hồi nếu yêu cầu kiểm duyệt bao gồm thu hồi quyền xem ảnh tại CDN.

## Phần chưa triển khai của bản thiết kế lớn

- Feed “Dành cho bạn” có xếp hạng/snapshot Redis, cuộn tự động thay cho nút Xem thêm.
- Ưu tiên gói/boost, giới hạn 2 slot cộng đồng, vòng đời quảng bá và thống kê impression/click.
- Đồng bộ/tìm kiếm Elasticsearch và chèn thẻ gợi ý từ AIService.
- WebSocket cho cộng đồng; hiện dùng polling ở chi tiết.
- Hồ sơ cộng đồng riêng, danh sách follow riêng, nhóm thông báo cảm xúc.
- Ngoại lệ theo ngữ cảnh cho rule, quét lại hàng loạt, giao hồ sơ cho từng reviewer, màn hình audit và appeal cho mọi loại nội dung.
- Report phần chữ review từ listing và nút mở text review RESTRICTED; đánh giá không có sửa điểm sao mới.
- Rate limit sửa bài/phản hồi, idempotency key bền vững, dọn asset mồ côi và bằng chứng media có thời hạn lưu.

Các phần này không được mô phỏng bằng số liệu giả hoặc gắn nhãn là đã hoạt động trong UI.

### Comment tree and request lifecycle

GET comments with `parent` now accepts any visible comment in the post and returns only its direct children, using `COALESCE(reply_to_id,parent_id)`. New replies retain the root thread in `parentId` and the exact immediate target in `replyToId`. Legacy replies with no target remain direct children of their stored root; no target is inferred. Each level has its own cursor and expand/collapse state. Deleted intermediate comments can remain as placeholders when they have descendants; hidden/inactive root threads are not exposed through nested queries.

The frontend renders recursive branches and fetches a branch only when first expanded or when its next page is requested. Reopening a loaded branch uses the cached data. Both the comment component and post-detail page no longer poll every five seconds. Create/edit/delete/reaction responses update the affected local node without resetting loaded pages, expanded branches, the composer draft, or focus. Comment lists are deduplicated by ID when a next page arrives. The response to a branch fetch that was already in flight is merged with locally posted comments so it cannot erase a new reply.

Each comment response includes `replyCount`: the total accessible direct children, including retained deleted placeholders with descendants, computed in the same SQL response rather than separate HTTP requests. Counts are not limited to the loaded page. The toggle displays “N phản hồi” or “Thu gọn · N phản hồi”; empty collapsed branches have no toggle. Local create/delete updates the affected parent's count without polling or reloading the thread.

Community feed loads 10 posts per cursor page for LATEST/FOLLOWING/SAVED/MINE. The UI observes a persistent end marker with a 600px prefetch margin and appends the next page automatically. There is no load-more button. Only one page request is active at a time; filter resets cancel the old request. Search text is applied only on submit/reset, so typing does not mix a new query with an old cursor. Fetch errors preserve the loaded list and stop automatic retries; the retry action resumes the failed page. Observer/frame callbacks are cleaned up on navigation.
