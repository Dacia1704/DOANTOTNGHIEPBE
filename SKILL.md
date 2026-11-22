---
name: backend-development
description: >-
  Comprehensive guide and runbook for developing, migrating, building, and running
  the Spring Boot 4.1.1 backend (RentalManagement) in Java 21.
  Activate when working on controllers, services, database migrations (Flyway),
  entities/repositories, JPA transactions, room concurrency locks, JasperReports PDF generation,
  ranking/discovery algorithms, Redis/RabbitMQ messaging, or VNPay payment flows.
---

# Backend Development Guide (RentalManagement)

Tài liệu hướng dẫn và quy chuẩn kỹ thuật cho việc phát triển, kiểm thử, quản lý database và vận hành dịch vụ Backend của Hệ thống Quản lý Thuê trọ (Rental Management).

---

## 1. Tổng quan Công nghệ & Hạ tầng

| Thành phần | Công nghệ / Phiên bản | Ghi chú |
| :--- | :--- | :--- |
| **Ngôn ngữ** | Java 21 (JDK 21) | Cần cả `java` và `javac` từ JDK 21 |
| **Framework** | Spring Boot 4.1.1 | Spring Security, Spring Data JPA, Spring WebSocket |
| **Database** | MySQL 8.0 (`rental_db`) | `ddl-auto: validate` — Flyway là nguồn sự thật duy nhất |
| **Migration** | Flyway 10+ | Quản lý schema từ V1 đến V14 trong `db/migration/` |
| **Cache & Sessions** | Redis 7+ (`localhost:6379`) | Cache kết quả xếp hạng, phiên tìm kiếm `RankingSessions` |
| **Message Broker** | RabbitMQ 3.13+ (`localhost:5672`)| Pub/Sub tin nhắn chat, outbox thông báo realtime |
| **PDF Generation** | JasperReports 7.0.8 | Sinh hợp đồng thuê PDF từ `rental-contract.jrxml` + NotoSans |
| **Bảo mật** | JWT HS512 (`app.jwt.*`) | Secret bắt buộc $\ge 64$ bytes, dùng chung với AIService |
| **Lưu trữ ngoài** | Cloudinary | Upload ảnh phòng trọ và hợp đồng PDF đã ký số |
| **Cổng thanh toán** | VNPay Sandbox | Mua gói dịch vụ & Boost hiển thị tin đăng |
| **Đơn vị hành chính**| API tinhthanhpho.com | Đồng bộ 34 tỉnh/thành và 3.321 phường/xã + tọa độ Centroid |

### Cổng & URL Truy cập Local

*   **REST API**: `http://localhost:8080/api`
*   **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`
*   **WebSocket STOMP**: `http://localhost:8080/ws`
*   **RabbitMQ Management**: `http://localhost:15672` (User/Pass mặc định: `admin` / `admin123`)
*   **MailHog Web UI**: `http://localhost:8025` (Xem email xác thực/reset pass local)

---

## 2. Cấu trúc Thư mục & Phân vùng Module

```text
RentalManagement/
├── src/main/java/com/tamdaptit/rentalmanagement/
│   ├── address/             # Tỉnh/phường, centroid tọa độ từ tinhthanhpho.com
│   ├── auth/                # Đăng ký, đăng nhập, JWT provider, filter chuỗi bảo mật
│   ├── chat/                # WebSocket chat giữa tenant và landlord, lưu hội thoại
│   ├── common/              # ApiResponse, PageResponse, AppExceptions, Base utils
│   ├── community/           # Bài viết, bình luận, tương tác, báo cáo cộng đồng
│   ├── dashboard/           # Thống kê doanh thu, số liệu cho Admin & Landlord
│   ├── discovery/           # Thuật toán xếp hạng landing (listing-v1) & feed (community-v1)
│   ├── identity/            # Quản lý User, UserProfile, Role, Permissions
│   ├── listing/             # Tin đăng phòng trọ, duyệt tin, hiển thị công khai
│   ├── moderation/          # Hàng đợi kiểm duyệt nội dung, xử lý vi phạm
│   ├── notification/        # Notification inbox JPA & Outbox relay qua RabbitMQ
│   ├── payment/             # Tích hợp VNPay sandbox, webhook IPN, xử lý thanh toán
│   ├── property/            # Quản lý cơ sở trọ, địa chỉ, tiện ích cơ sở
│   ├── rental/              # Yêu cầu thuê, hợp đồng, xác nhận điện tử, PDF generator
│   ├── report/              # Báo cáo vi phạm tin đăng / người dùng
│   ├── review/              # Đánh giá & nhận xét phòng trọ
│   ├── room/                # Phòng trọ (đơn vị cho thuê), trạng thái, tiện ích phòng
│   ├── storage/             # Cloudinary upload service cho ảnh và PDF
│   ├── subscription/        # Gói dịch vụ tháng, gói Boost tin đăng, phân bổ lượt
│   └── RentalManagementApplication.java
├── src/main/resources/
│   ├── application.yaml     # Cấu hình mặc định (đọc từ .env khi chạy)
│   ├── db/migration/        # File migration Flyway V1 -> V14
│   └── reports/             # JasperReports template (rental-contract.jrxml, font NotoSans)
├── scripts/                 # PowerShell/Python scripts khởi tạo DB, seed dữ liệu demo
├── pom.xml                  # Maven build configuration
└── .env                     # Biến môi trường local (sao chép từ .env.docker)
```

---

## 3. Quy tắc Kiến trúc & Phát triển Code (RULE.md)

Tuân thủ nghiêm ngặt [RULE.md](file:///e:/SpringBoot/DoAn/RentalManagement/docs/RULE.md):

### 3.1. Tư duy Thiết kế (Core Philosophy)
1.  **Think Before Coding**: Xác định giả định rõ ràng trước khi code. Nếu có phương án đơn giản hơn, hãy chọn phương án đơn giản. Không ngầm đoán yêu cầu mơ hồ.
2.  **Simplicity First**: Giải pháp tối thiểu giải quyết triệt để vấn đề. Không tự ý thêm abstraction thừa thãi cho mã nguồn chỉ dùng một lần. Không viết trước các cấu hình "tiềm năng" chưa ai yêu cầu.
3.  **Surgical Changes**: Chỉ chỉnh sửa đúng những file và dòng code liên quan trực tiếp đến nhiệm vụ. Không tự tiện format lại code hoặc refactor những phần đang chạy ổn định xung quanh.
4.  **Goal-Driven Execution**: Mọi thay đổi phải có tiêu chí kiểm chứng (biên dịch pass, test pass, API phản hồi đúng chuẩn).

### 3.2. Chuẩn Controller / Service Pattern
*   **Controller**:
    *   Chỉ tiêm (`@Autowired` / constructor injection) **Interface Service** từ package `service/`.
    *   Nhiệm vụ: Trích xuất danh tính từ Security Context (`Authentication`), validate input DTO (`@Valid`), gọi Service và bọc kết quả trong `ApiResponse<T>` hoặc `PageResponse<T>`.
    *   **Tuyệt đối không trả JPA Entity trực tiếp ra Controller**. Luôn sử dụng DTO / Response Object.
*   **Service & ServiceImpl**:
    *   Interface đặt tại `com.tamdaptit.rentalmanagement.{domain}.service`.
    *   Implementation đặt tại `com.tamdaptit.rentalmanagement.{domain}.service.impl`, đánh dấu `@Service`, `@Transactional`, `@Override`.
    *   Mọi logic nghiệp vụ, chuyển đổi trạng thái và giao dịch database nằm tại Implementation.

### 3.3. Ranh giới Database & Toàn vẹn Dữ liệu
*   **Flyway là nguồn sự thật duy nhất**:
    *   `spring.jpa.hibernate.ddl-auto=validate`. Hibernate chỉ kiểm tra đối chiếu schema, tuyệt đối không tự sửa bảng.
    *   Không bao giờ sửa đổi các file migration đã được áp dụng vào database (Flyway kiểm tra checksum). Muốn đổi schema, tạo file `V{next}__mo_ta.sql` mới.
*   **Đơn vị cho thuê & Concurrency Locking**:
    *   `Room` là đơn vị thuê lõi. Một cơ sở thuê nguyên căn (`WHOLE`) có đúng 1 Room đại diện.
    *   Bắt buộc thực hiện **Khóa bi quan (Pessimistic Lock)** trên bản ghi `Room` trước khi tạo yêu cầu thuê hoặc xác nhận hợp đồng để chống thuê trùng phòng đồng thời.
    *   *Quy tắc vàng:* **Không bao giờ gọi dịch vụ ngoài mạng (Cloudinary, VNPay, gửi mail) hoặc render PDF khi đang giữ DB Transaction có khóa Room**. Hãy kết thúc transaction trước hoặc tách transaction độc lập.
*   **Bảng sự kiện & Giao tiếp tin cậy**:
    *   `rental_events`: Ghi audit log lịch sử thuê phòng qua `JdbcTemplate`.
    *   `rental_pdf_assets`: Theo dõi trạng thái sinh và đối soát hợp đồng PDF.
    *   `notification_outbox`: Áp dụng mẫu Transactional Outbox ghi nhận sự kiện thông báo trong cùng transaction nghiệp vụ, sau đó worker nền bắn qua RabbitMQ.

---

## 4. Thuật toán Xếp hạng Landing & Bảng tin Cộng đồng

Dự án đã triển khai thuật toán xếp hạng chính thức (ngày 15/11/2026):

### 4.1. Landing: Xếp hạng Tin Đăng (`listing-v1`)
*   File triển khai: `com.tamdaptit.rentalmanagement.discovery.ListingDiscoveryService` và `FeedRanking`.
*   **Bước 1 - Lọc ứng viên**: Chỉ lấy tin `PUBLISHED`, còn hạn, chủ trọ `ACTIVE`, cơ sở có phòng trống `AVAILABLE`.
*   **Bước 2 - Phân bổ vị trí 5/3/2**: Mỗi chu kỳ 10 vị trí theo khuôn mẫu `[2,1,2,0,2,1,2,0,2,1]` (5 tin VIP gói cao, 3 tin Boost thấp, 2 tin thường). Vị trí thứ 10 dành cho khám phá tin mới.
*   **Bước 3 - Công thức chấm điểm**:
    $$S_{\text{landing}} = 0.35 \cdot \text{relevance} + 0.25 \cdot \text{quality} + 0.25 \cdot \text{engagement} + 0.15 \cdot \text{freshness}$$
    $$S_{\text{serving}} = \frac{S_{\text{landing}}}{1 + 0.15 \cdot \text{seen\_by\_user\_7d}} - 0.025 \cdot \log(1 + \text{impressions\_30d})$$
*   **Đa dạng hóa**: Giới hạn tối đa 2 tin từ cùng một chủ trọ trong mỗi 10 vị trí hiển thị.
*   **RankingSessions**: Lưu thứ tự danh sách trong Redis TTL 30 phút. Phân trang bằng session token để kết quả không bị xáo trộn khi người dùng lướt trang tiếp.

### 4.2. Bảng tin Cộng đồng (`community-v1`)
*   File triển khai: `com.tamdaptit.rentalmanagement.discovery.CommunityDiscoveryService`.
*   Chấm điểm bài viết dựa trên: $30\%$ mức độ quan tâm (khu vực mong muốn & hashtag), $25\%$ tương tác độc lập ($U_{\text{reactions}} + 3 \cdot U_{\text{commenters}} + 4 \cdot U_{\text{saves}}$), $20\%$ tác giả đang theo dõi, $15\%$ độ mới thời gian và $10\%$ điểm uy tín (không bị báo cáo/vi phạm).
*   Cursor phân trang: Định dạng `r:<session>:<offset>`.

---

## 5. Hướng dẫn Vận hành & Runbooks

Tất cả các lệnh thực thi từ thư mục `E:\SpringBoot\DoAn\RentalManagement`:

### 5.1. Khởi động lần đầu (Dựng mới DB & Seed dữ liệu demo)
> [!WARNING]
> Chỉ chạy script này khi database mới hoàn toàn chưa có bảng hoặc khi chủ động reset toàn bộ database.

```powershell
Set-Location E:\SpringBoot\DoAn\RentalManagement
# Bật hạ tầng Docker -> Flyway V1 -> Đồng bộ địa chỉ -> Sinh V2/V12 -> Migrate hoàn tất
.\scripts\setup-demo.ps1
```

### 5.2. Khởi động thường ngày (Không seed lại)
```powershell
Set-Location E:\SpringBoot\DoAn\RentalManagement

# 1. Bật toàn bộ container hạ tầng (MySQL, Redis, RabbitMQ, MailHog)
docker compose up -d
docker compose ps

# 2. Khởi động Spring Boot Backend bằng Maven Wrapper
.\mvnw.cmd spring-boot:run
```

### 5.3. Kiểm tra Biên dịch & Đóng gói
```powershell
Set-Location E:\SpringBoot\DoAn\RentalManagement

# Biên dịch toàn bộ source và test code
.\mvnw.cmd compile

# Đóng gói JAR bỏ qua chạy test (khi kiểm tra build nhanh)
.\mvnw.cmd -DskipTests package
```

### 5.4. Tài khoản Demo Mặc định
*   **Admin**: `admin@gmail.com` / `123456`
*   **Landlord**: `landlord@gmail.com` / `123456`
*   **Tenant**: `tenant@gmail.com` / `123456`
*   Các tài khoản sinh thêm: domain `@trotot.example.test` / mật khẩu `123456`.

---

## 6. Các lỗi thường gặp & Cách xử lý

1.  **Lỗi Flyway Checksum Mismatch**:
    *   Nguyên nhân: Một file migration cũ (ví dụ V1, V2) bị chỉnh sửa nội dung sau khi đã chạy vào database.
    *   Khắc phục: Giữ nguyên file migration cũ theo lịch sử VCS; viết thêm migration mới để cập nhật thay đổi. Không tùy tiện chạy Flyway repair nếu chưa đối soát kỹ.
2.  **Lỗi JWT Secret quá ngắn (`WeakKeyException`)**:
    *   Spring Boot cấu hình thuật toán `HS512` yêu cầu secret tối thiểu 64 bytes (512 bits).
    *   Đảm bảo `JWT_SECRET` trong `.env` của Backend và AIService đồng nhất và đủ độ dài.
3.  **Lỗi không kết nối được RabbitMQ / Redis**:
    *   Kiểm tra `docker compose ps` xem container `rental-rabbitmq` và `rental-redis` có trạng thái `healthy` không.
    *   Cổng RabbitMQ ứng dụng kết nối là `5672` (cổng giao diện web quản lý là `15672`).
4.  **Lỗi thanh toán VNPay 503**:
    *   Thiếu `VNPAY_HASH_SECRET` hoặc `VNPAY_TMN_CODE` trong `.env`. Cần điền thông số sandbox để module thanh toán hoạt động.
