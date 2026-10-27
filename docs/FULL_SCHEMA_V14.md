# V14 — bản SQL đầy đủ cho database mới

File thực thi: `sql/V14__fullsql.sql`.

Đây là bản build độc lập theo yêu cầu, không phải migration nâng cấp. V12/V13 được giữ nguyên. V14 nằm ngoài `src/main/resources/db/migration` để Flyway không tự chạy các lệnh CREATE TABLE trên database đã có schema.

## Kết quả đối chiếu

Đếm chính xác trong mã Java có 44 entity JPA. Số 46 trước đó bao gồm hai đoạn `@Entity` trong file hướng dẫn Markdown, không phải entity được biên dịch.

V12 chạy trên schema trống và sau đó V13 chạy thành công trên MySQL 8.0.46. Kết quả có đủ 44 bảng, 398 cột và 85 quan hệ FK. So sánh với schema Hibernate sinh từ toàn bộ entity cho thấy:

- Không thiếu bảng/cột entity, không sai nullability, độ dài VARCHAR hoặc precision/scale DECIMAL, không thiếu/sai đích FK hoặc khóa chính.
- Thiếu UNIQUE trên `listings.slug`, trong khi `Listing.slug` có `@Column(unique = true)`. Hibernate validate không tự kiểm tra đầy đủ unique/FK/index; test bổ sung so sánh metadata MySQL để phát hiện lỗi này.
- Bốn cột lịch sử không còn được entity dùng: `service_packages.legacy_price`, `service_packages.legacy_billing_cycle_days`, `boost_service_packages.legacy_priority_level`, `boost_service_packages.legacy_duration_days`. Bản dành cho database mới không cần chúng.
- Ba cột GENERATED được giữ: `service_package_subscriptions.active_landlord_id`, `scheduled_landlord_id`, `service_subscription_orders.open_change_landlord_id`. Chúng bảo vệ tính duy nhất theo trạng thái, không phải field bị thiếu của entity.
- Các unique nghiệp vụ có trong SQL nhưng chưa khai báo trong JPA được giữ: tên Amenity/RoomType; một SavedListing cho tenant/listing; một ListingDailyStat mỗi ngày; kỳ hạn giá gói; thứ tự kỳ thuê bao; cặp payment_method/provider_txn_id; phone/citizen_id; các cột GENERATED nêu trên. Không xóa chúng chỉ để làm giống annotation.

So sánh kiểu dữ liệu coi BIT/TINYINT boolean và ENUM/VARCHAR là các biểu diễn tương thích khi Hibernate chấp nhận; với hai cột ENUM, so sánh tập giá trị không phụ thuộc thứ tự. Giữ DATETIME và các default/ON UPDATE hiện có của SQL; không tự đổi toàn bộ timestamp sang DATETIME(6). Các CHECK nghiệp vụ và các quy tắc ON DELETE/ON UPDATE của V12 + V13 được giữ nguyên, không suy diễn chúng từ cascade của JPA.

## Nội dung V14

- Tạo trực tiếp schema cuối cùng: không lặp lại những bước ALTER/UPDATE chuyển đổi lịch sử của V12/V13.
- Thêm unique `uk_listings_slug`; cho phép nhiều slug NULL theo MySQL, nhưng chặn hai slug không NULL trùng nhau.
- Bỏ bốn cột legacy không dùng; kết quả 394 cột, gồm 391 cột mapped và ba cột GENERATED bảo vệ nghiệp vụ.
- Tạo đủ 44 bảng trước, sau đó thêm toàn bộ 85 FK. Không tắt FOREIGN_KEY_CHECKS, không DROP/TRUNCATE, không tự tạo/chọn database.
- Giữ toàn bộ CHECK, quy tắc xóa/cập nhật FK và index hiện có; đảm bảo các index khai báo trong entity có index tương đương bao phủ.
- Seed tối thiểu gói hệ thống FREE như V12. Không seed dữ liệu mẫu, user, phòng hoặc tài chính; không cần loại cơ sở tạm dành cho chuyển đổi dữ liệu cũ.

Các nhóm bảng đã đối chiếu:

- Identity/auth: users, roles, permissions, user_roles, role_permissions, tenant_profiles, refresh_tokens.
- Address: provinces, wards.
- Property: property_types, properties, property_amenities, property_images.
- Room: room_types, amenities, rooms, room_amenities, room_images.
- Listing: listings, saved_listings, listing_view_history, listing_contact_events, listing_daily_stats.
- Rental: rental_requests, rental_agreements.
- Subscription/Boost: service_packages, service_package_prices, service_package_subscriptions, service_subscription_orders, service_subscription_periods, boost_service_packages, boost_service_package_subscriptions, boost_allocations.
- Payment: payments, invoices.
- Review: room_reviews, landlord_reviews, tenant_reviews.
- Chat: chat_conversations, chat_conversation_participants, chat_messages.
- Notification/report: notifications, reports, report_evidences.

Các quan hệ mới đã có trong schema cuối: Property liên kết landlord/type/province/ward; Room liên kết Property và RoomType; Listing liên kết Property, người duyệt và kỳ dịch vụ. `listings.room_id` không còn. Hợp đồng và đánh giá vẫn có quan hệ Room theo entity; không chuyển những quan hệ đó sang Property. Cây RoomType có FK tự tham chiếu parent_id; kiểm tra vòng lặp nhiều cấp vẫn do service đảm nhiệm.

## Chạy cho database mới

Tạo/chọn một database MySQL trống, sau đó dùng `SOURCE D:/HOCTAP/DOANTOTNGHIEP/DOANTOTNGHIEPBE/sql/V14__fullsql.sql` trong MySQL client, hoặc import file bằng công cụ quản lý database.

Không chạy V1–V13 trước hoặc sau bản full này. Không chạy lại V13 vì V14 đã dùng `listings.property_id`.

Ứng dụng hiện bật Flyway và đọc `classpath:db/migration`. Nếu dùng cách import V14 thủ công, khi chạy ứng dụng với database đó cần đặt `SPRING_FLYWAY_ENABLED=false`, giữ `ddl-auto=validate`. Muốn dùng Flyway quản lý database này về sau thì cần một quy trình baseline và lịch sử migration thống nhất riêng; file full không tự giả lập lịch sử Flyway.

V12 hiện nằm trong thư mục migration nhưng chứa CREATE TABLE của cả schema: chạy toàn chuỗi V1 → V12 sẽ lỗi bảng đã tồn tại trước khi tới V13/V14. Một migration về sau không sửa được lỗi dừng ở V12. Theo lựa chọn tạo bản full cho database mới, lần này không tự chuyển/xóa V12 hoặc sửa lịch sử migration. Những test cũ chạy nguyên chuỗi Flyway vì vậy không còn đại diện cho cách dùng bản full; đây là vấn đề đóng gói migration cần xử lý riêng trước khi triển khai theo chuỗi.

## Kiểm chứng

`FullSchemaAuditTest` tạo và xóa ba schema ngẫu nhiên trên MySQL riêng:

1. Dựng V12 + V13; xác nhận lỗi thiếu UNIQUE slug trong bản nguồn.
2. Sinh schema từ toàn bộ entity hiện tại làm mẫu đối chiếu cột, PK, unique, FK và index.
3. Dựng chỉ V14 trên schema trống; so sánh metadata, toàn bộ quy tắc FK và CHECK; chạy Hibernate validate với 44 entity.
4. Ghi dữ liệu kiểm tra để xác nhận FK không tồn tại, slug trùng, mã phòng trùng và hai subscription ACTIVE bị chặn; nhiều slug NULL vẫn hợp lệ; rental_mode ngoài enum bị chặn.

Kết quả: test qua trên MySQL 8.0.46, 44 bảng/394 cột/85 FK. Không chạy trên database ứng dụng. Test chỉ chạy khi `SUBSCRIPTION_TEST_MYSQL_URL` là JDBC URL localhost có port riêng. Từ thư mục BE, đặt biến này và chạy `./mvnw.cmd test -Dtest=FullSchemaAuditTest`.
