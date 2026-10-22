-- =============================================================================
-- HỆ THỐNG CHO THUÊ PHÒNG TRỌ TRỰC TUYẾN - SCHEMA GỐC
-- MySQL 8.0+ / Flyway
--
-- Quy ước:
-- 1. Không sử dụng BaseEntity dùng chung.
-- 2. Không sử dụng created_by / updated_by / deleted.
-- 3. created_at / updated_at chỉ giữ ở những bảng cần thiết.
-- 4. Các bảng mapping không sử dụng timestamp.
-- 5. Các entity có trạng thái nghiệp vụ riêng không dùng soft delete.
--
-- LƯU Ý: File này là nguồn sự thật duy nhất cho schema.
-- Hibernate được cấu hình ddl-auto=validate, mọi lệch schema sẽ
-- làm ứng dụng không khởi động được.
-- =============================================================================

-- =====================================================================
-- I. NHÓM TÀI KHOẢN & PHÂN QUYỀN (RBAC)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. USERS
-- ---------------------------------------------------------------------

CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(20) UNIQUE,
    password VARCHAR(255) NOT NULL,

    full_name VARCHAR(100),

    avatar_url VARCHAR(255),
    avatar_public_id VARCHAR(255),

    citizen_id VARCHAR(20) UNIQUE,

    email_verified_at DATETIME,
    phone_verified_at DATETIME,

    status ENUM(
        'ACTIVE',
        'INACTIVE',
        'BLOCKED'
    ) NOT NULL DEFAULT 'ACTIVE',

    -- Tăng mỗi khi đổi mật khẩu, đổi email hoặc đổi trạng thái.
    -- Access token đã cấp sẽ bị vô hiệu hoá khi giá trị này thay đổi.
    auth_version INT NOT NULL DEFAULT 0,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 2. ROLES
-- ---------------------------------------------------------------------

CREATE TABLE roles (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    code ENUM(
        'TENANT',
        'LANDLORD',
        'ADMIN'
    ) UNIQUE NOT NULL,

    name VARCHAR(100) NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 3. PERMISSIONS
-- ---------------------------------------------------------------------

CREATE TABLE permissions (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    code VARCHAR(100) UNIQUE NOT NULL,

    name VARCHAR(255) NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 4. USER_ROLES
-- ---------------------------------------------------------------------

CREATE TABLE user_roles (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    user_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL,

    CONSTRAINT uk_user_roles
        UNIQUE (user_id, role_id),

    CONSTRAINT fk_ur_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ur_role
        FOREIGN KEY (role_id)
        REFERENCES roles(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 5. ROLE_PERMISSIONS
-- ---------------------------------------------------------------------

CREATE TABLE role_permissions (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    role_id VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,

    CONSTRAINT uk_role_permissions
        UNIQUE (role_id, permission_id),

    CONSTRAINT fk_rp_role
        FOREIGN KEY (role_id)
        REFERENCES roles(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rp_permission
        FOREIGN KEY (permission_id)
        REFERENCES permissions(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 6. REFRESH_TOKENS
-- ---------------------------------------------------------------------
-- Lưu SHA-256 hex của refresh token (64 ký tự), KHÔNG lưu token gốc.
-- Token gốc chỉ trả về cho client 1 lần; sau đó không thể khôi phục từ DB.
-- Token là base64url nên đây là chuỗi ASCII — khai báo CHARACTER SET ascii
-- để index đủ nhỏ và so khớp nhanh.
-- Dùng FOR UPDATE khi xoay vòng token để chống race condition.

CREATE TABLE refresh_tokens (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    user_id VARCHAR(36) NOT NULL,

    token VARCHAR(64) CHARACTER SET ascii NOT NULL,

    expires_at DATETIME NOT NULL,

    revoked BOOLEAN NOT NULL DEFAULT FALSE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rt_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    INDEX IX_refresh_tokens_token (token),
    INDEX IX_refresh_tokens_user_id (user_id),
    INDEX IX_refresh_tokens_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =====================================================================
-- II. NHÓM ĐỊA CHỈ HÀNH CHÍNH
-- =====================================================================

-- ---------------------------------------------------------------------
-- 7. PROVINCES
-- ---------------------------------------------------------------------

CREATE TABLE provinces (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 8. WARDS
-- ---------------------------------------------------------------------

CREATE TABLE wards (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    province_id VARCHAR(36) NOT NULL,

    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,

    CONSTRAINT fk_wards_province
        FOREIGN KEY (province_id)
        REFERENCES provinces(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =====================================================================
-- III. NHÓM HỒ SƠ NGƯỜI THUÊ & DANH MỤC
-- =====================================================================

-- ---------------------------------------------------------------------
-- 9. ROOM_TYPES
-- ---------------------------------------------------------------------
-- parent_id cho phép nhóm loại phòng theo cây (ví dụ: "Phòng trọ" >
-- "Phòng trọ có điều hoà"). Có FK tự tham chiếu.

CREATE TABLE room_types (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,

    parent_id VARCHAR(36),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_rt_parent
        FOREIGN KEY (parent_id)
        REFERENCES room_types(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 10. AMENITIES
-- ---------------------------------------------------------------------

CREATE TABLE amenities (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    name VARCHAR(100) UNIQUE NOT NULL,
    icon VARCHAR(255),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 11. TENANT_PROFILES
-- ---------------------------------------------------------------------

CREATE TABLE tenant_profiles (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    user_id VARCHAR(36) UNIQUE NOT NULL,

    desired_province_id VARCHAR(36),
    desired_ward_id VARCHAR(36),

    price_min DECIMAL(12, 2),
    price_max DECIMAL(12, 2),

    area_min DECIMAL(8, 2),
    area_max DECIMAL(8, 2),

    desired_room_type_id VARCHAR(36),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_tp_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_tp_province
        FOREIGN KEY (desired_province_id)
        REFERENCES provinces(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_tp_ward
        FOREIGN KEY (desired_ward_id)
        REFERENCES wards(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_tp_room_type
        FOREIGN KEY (desired_room_type_id)
        REFERENCES room_types(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- IV. NHÓM PHÒNG & TIN ĐĂNG
-- =====================================================================

-- ---------------------------------------------------------------------
-- 12. ROOMS
-- ---------------------------------------------------------------------

CREATE TABLE rooms (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    landlord_id VARCHAR(36) NOT NULL,
    room_type_id VARCHAR(36) NOT NULL,

    price DECIMAL(12, 2) NOT NULL,

    electricity_price DECIMAL(10, 2),
    water_price DECIMAL(10, 2),
    service_fee DECIMAL(10, 2),
    default_deposit DECIMAL(12, 2),

    area_m2 DECIMAL(8, 2) NOT NULL,
    max_occupants INT DEFAULT 1,

    province_id VARCHAR(36) NOT NULL,
    ward_id VARCHAR(36) NOT NULL,

    address_line VARCHAR(255) NOT NULL,

    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),

    rental_status ENUM(
        'AVAILABLE',
        'RENTED',
        'MAINTENANCE'
    ) NOT NULL DEFAULT 'AVAILABLE',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_rooms_landlord
        FOREIGN KEY (landlord_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rooms_room_type
        FOREIGN KEY (room_type_id)
        REFERENCES room_types(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_rooms_province
        FOREIGN KEY (province_id)
        REFERENCES provinces(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_rooms_ward
        FOREIGN KEY (ward_id)
        REFERENCES wards(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 13. ROOM_AMENITIES
--
-- Đặt sau `rooms` vì có FK trỏ tới bảng đó; MySQL không cho tạo FK tới
-- bảng chưa tồn tại.
-- ---------------------------------------------------------------------

CREATE TABLE room_amenities (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    room_id VARCHAR(36) NOT NULL,
    amenity_id VARCHAR(36) NOT NULL,

    CONSTRAINT uk_room_amenities
        UNIQUE (room_id, amenity_id),

    CONSTRAINT fk_ra_room
        FOREIGN KEY (room_id)
        REFERENCES rooms(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ra_amenity
        FOREIGN KEY (amenity_id)
        REFERENCES amenities(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 14. ROOM_IMAGES
-- ---------------------------------------------------------------------

CREATE TABLE room_images (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    room_id VARCHAR(36) NOT NULL,

    url VARCHAR(255) NOT NULL,
    public_id VARCHAR(255),

    sort_order INT DEFAULT 0,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ri_room
        FOREIGN KEY (room_id)
        REFERENCES rooms(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 15. LISTINGS
-- ---------------------------------------------------------------------

CREATE TABLE listings (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    room_id VARCHAR(36) NOT NULL,

    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255),
    description TEXT,

    status ENUM(
        'PENDING',
        'APPROVED',
        'REJECTED',
        'HIDDEN',
        'EXPIRED'
    ) NOT NULL DEFAULT 'PENDING',

    rejection_reason TEXT,

    approved_by VARCHAR(36),
    approved_at DATETIME,

    priority_level INT DEFAULT 0,

    view_count INT DEFAULT 0,
    contact_count INT DEFAULT 0,

    expires_at DATETIME,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_listings_room
        FOREIGN KEY (room_id)
        REFERENCES rooms(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_listings_approved_by
        FOREIGN KEY (approved_by)
        REFERENCES users(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =====================================================================
-- V. NHÓM TƯƠNG TÁC & THỐNG KÊ
-- =====================================================================

-- ---------------------------------------------------------------------
-- 16. SAVED_LISTINGS
-- ---------------------------------------------------------------------

CREATE TABLE saved_listings (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    tenant_id VARCHAR(36) NOT NULL,
    listing_id VARCHAR(36) NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_saved_listings
        UNIQUE (tenant_id, listing_id),

    CONSTRAINT fk_sl_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_sl_listing
        FOREIGN KEY (listing_id)
        REFERENCES listings(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 17. LISTING_VIEW_HISTORY
-- ---------------------------------------------------------------------

CREATE TABLE listing_view_history (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    tenant_id VARCHAR(36) NOT NULL,
    listing_id VARCHAR(36) NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_lvh_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_lvh_listing
        FOREIGN KEY (listing_id)
        REFERENCES listings(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 18. LISTING_CONTACT_EVENTS
-- ---------------------------------------------------------------------

CREATE TABLE listing_contact_events (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    listing_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_lce_listing
        FOREIGN KEY (listing_id)
        REFERENCES listings(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_lce_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 19. LISTING_DAILY_STATS
-- ---------------------------------------------------------------------

CREATE TABLE listing_daily_stats (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    listing_id VARCHAR(36) NOT NULL,

    stat_date DATE NOT NULL,

    view_count INT DEFAULT 0,
    contact_count INT DEFAULT 0,

    CONSTRAINT uk_listing_daily_stats
        UNIQUE (listing_id, stat_date),

    CONSTRAINT fk_lds_listing
        FOREIGN KEY (listing_id)
        REFERENCES listings(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =====================================================================
-- VI. NHÓM YÊU CẦU THUÊ & HỢP ĐỒNG
-- =====================================================================

-- ---------------------------------------------------------------------
-- 20. RENTAL_REQUESTS
-- ---------------------------------------------------------------------

CREATE TABLE rental_requests (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    listing_id VARCHAR(36) NOT NULL,

    tenant_id VARCHAR(36) NOT NULL,
    landlord_id VARCHAR(36) NOT NULL,

    message TEXT,

    proposed_move_in_date DATE,

    status ENUM(
        'PENDING',
        'ACCEPTED',
        'REJECTED',
        'EXPIRED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'PENDING',

    responded_at DATETIME,
    expires_at DATETIME,

    -- Khoá chống gửi trùng yêu cầu khi người dùng bấm nút hai lần.
    pending_key VARCHAR(255),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_rr_listing
        FOREIGN KEY (listing_id)
        REFERENCES listings(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rr_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rr_landlord
        FOREIGN KEY (landlord_id)
        REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 21. RENTAL_AGREEMENTS
-- ---------------------------------------------------------------------

CREATE TABLE rental_agreements (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    agreement_code VARCHAR(50) UNIQUE NOT NULL,

    rental_request_id VARCHAR(36),

    room_id VARCHAR(36) NOT NULL,

    tenant_id VARCHAR(36) NOT NULL,
    landlord_id VARCHAR(36) NOT NULL,

    price DECIMAL(12, 2) NOT NULL,
    deposit DECIMAL(12, 2) DEFAULT 0,

    electricity_price DECIMAL(10, 2),
    water_price DECIMAL(10, 2),
    service_fee DECIMAL(10, 2),

    payment_cycle_months INT DEFAULT 1,

    start_date DATE NOT NULL,
    end_date DATE NOT NULL,

    terms_text TEXT,

    pdf_url VARCHAR(255),
    pdf_public_id VARCHAR(255),

    status ENUM(
        'DRAFT',
        'PENDING_CONFIRMATION',
        'ACTIVE',
        'EXPIRED',
        'TERMINATED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'DRAFT',

    created_by_role ENUM(
        'LANDLORD',
        'TENANT'
    ) NOT NULL,

    landlord_confirmed_at DATETIME,
    tenant_confirmed_at DATETIME,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_rag_request
        FOREIGN KEY (rental_request_id)
        REFERENCES rental_requests(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_rag_room
        FOREIGN KEY (room_id)
        REFERENCES rooms(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_rag_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_rag_landlord
        FOREIGN KEY (landlord_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =====================================================================
-- VII. NHÓM GÓI DỊCH VỤ & THANH TOÁN
-- =====================================================================

-- ---------------------------------------------------------------------
-- 22. SERVICE_PACKAGES
-- ---------------------------------------------------------------------

CREATE TABLE service_packages (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    code VARCHAR(50) UNIQUE NOT NULL,

    name VARCHAR(100) NOT NULL,

    max_listings INT DEFAULT 0,

    high_priority_quota INT DEFAULT 0,
    low_priority_quota INT DEFAULT 0,

    has_stats BOOLEAN DEFAULT FALSE,

    price DECIMAL(12, 2) NOT NULL,

    billing_cycle_days INT DEFAULT 30,

    is_active BOOLEAN DEFAULT TRUE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 23. SERVICE_PACKAGE_SUBSCRIPTIONS
-- ---------------------------------------------------------------------

CREATE TABLE service_package_subscriptions (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    landlord_id VARCHAR(36) NOT NULL,
    package_id VARCHAR(36) NOT NULL,

    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,

    status ENUM(
        'ACTIVE',
        'EXPIRED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'ACTIVE',

    active_flag BOOLEAN DEFAULT TRUE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_sps_landlord
        FOREIGN KEY (landlord_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_sps_package
        FOREIGN KEY (package_id)
        REFERENCES service_packages(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 24. BOOST_SERVICE_PACKAGES
-- ---------------------------------------------------------------------

CREATE TABLE boost_service_packages (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,

    priority_level INT DEFAULT 1,

    duration_days INT DEFAULT 1,

    price DECIMAL(12, 2) NOT NULL,

    is_active BOOLEAN DEFAULT TRUE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 25. BOOST_SERVICE_PACKAGE_SUBSCRIPTIONS
-- ---------------------------------------------------------------------

CREATE TABLE boost_service_package_subscriptions (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    landlord_id VARCHAR(36) NOT NULL,
    listing_id VARCHAR(36) NOT NULL,
    boost_package_id VARCHAR(36) NOT NULL,

    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,

    status ENUM(
        'ACTIVE',
        'EXPIRED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'ACTIVE',

    price DECIMAL(12, 2) NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_bsps_landlord
        FOREIGN KEY (landlord_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_bsps_listing
        FOREIGN KEY (listing_id)
        REFERENCES listings(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_bsps_package
        FOREIGN KEY (boost_package_id)
        REFERENCES boost_service_packages(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 26. PAYMENTS
-- ---------------------------------------------------------------------

CREATE TABLE payments (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    landlord_id VARCHAR(36) NOT NULL,

    type ENUM(
        'SERVICE_PACKAGE',
        'BOOST_PACKAGE'
    ) NOT NULL,

    reference_id VARCHAR(36) NOT NULL,

    amount DECIMAL(12, 2) NOT NULL,

    payment_method ENUM(
        'VNPAY',
        'MOMO',
        'BANK_TRANSFER'
    ) NOT NULL,

    status ENUM(
        'PENDING',
        'SUCCESS',
        'FAILED',
        'REFUNDED'
    ) NOT NULL DEFAULT 'PENDING',

    provider_txn_id VARCHAR(255),

    paid_at DATETIME,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_payments_landlord
        FOREIGN KEY (landlord_id)
        REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 27. INVOICES
-- ---------------------------------------------------------------------

CREATE TABLE invoices (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    payment_id VARCHAR(36) NOT NULL,

    type ENUM(
        'SERVICE_PACKAGE',
        'BOOST_PACKAGE'
    ) NOT NULL,

    service_package_id VARCHAR(36),
    boost_service_package_id VARCHAR(36),

    invoice_no VARCHAR(100) UNIQUE NOT NULL,

    tax_amount DECIMAL(12, 2) DEFAULT 0,
    total_amount DECIMAL(12, 2) NOT NULL,

    buyer_name VARCHAR(255),
    buyer_tax_code VARCHAR(50),

    issued_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_invoices_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_invoices_sp
        FOREIGN KEY (service_package_id)
        REFERENCES service_packages(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_invoices_bsp
        FOREIGN KEY (boost_service_package_id)
        REFERENCES boost_service_packages(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =====================================================================
-- VIII. NHÓM ĐÁNH GIÁ
-- =====================================================================

-- ---------------------------------------------------------------------
-- 28. ROOM_REVIEWS
-- ---------------------------------------------------------------------

CREATE TABLE room_reviews (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    rental_agreement_id VARCHAR(36) UNIQUE NOT NULL,

    tenant_id VARCHAR(36) NOT NULL,
    room_id VARCHAR(36) NOT NULL,

    rating INT NOT NULL
        CHECK (rating >= 1 AND rating <= 5),

    comment TEXT,

    landlord_reply TEXT,

    is_hidden BOOLEAN DEFAULT FALSE,

    hidden_by VARCHAR(36),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_rrev_agreement
        FOREIGN KEY (rental_agreement_id)
        REFERENCES rental_agreements(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rrev_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rrev_room
        FOREIGN KEY (room_id)
        REFERENCES rooms(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rrev_hidden_by
        FOREIGN KEY (hidden_by)
        REFERENCES users(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 29. LANDLORD_REVIEWS
-- ---------------------------------------------------------------------

CREATE TABLE landlord_reviews (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    rental_agreement_id VARCHAR(36) UNIQUE NOT NULL,

    tenant_id VARCHAR(36) NOT NULL,
    landlord_id VARCHAR(36) NOT NULL,

    rating INT NOT NULL
        CHECK (rating >= 1 AND rating <= 5),

    comment TEXT,

    landlord_reply TEXT,

    is_hidden BOOLEAN DEFAULT FALSE,

    hidden_by VARCHAR(36),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_lrev_agreement
        FOREIGN KEY (rental_agreement_id)
        REFERENCES rental_agreements(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_lrev_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_lrev_landlord
        FOREIGN KEY (landlord_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_lrev_hidden_by
        FOREIGN KEY (hidden_by)
        REFERENCES users(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 30. TENANT_REVIEWS
-- ---------------------------------------------------------------------

CREATE TABLE tenant_reviews (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    rental_agreement_id VARCHAR(36) UNIQUE NOT NULL,

    landlord_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,

    rating INT NOT NULL
        CHECK (rating >= 1 AND rating <= 5),

    comment TEXT,

    is_hidden BOOLEAN DEFAULT FALSE,

    hidden_by VARCHAR(36),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_trev_agreement
        FOREIGN KEY (rental_agreement_id)
        REFERENCES rental_agreements(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_trev_landlord
        FOREIGN KEY (landlord_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_trev_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_trev_hidden_by
        FOREIGN KEY (hidden_by)
        REFERENCES users(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =====================================================================
-- IX. NHÓM CHAT & THÔNG BÁO
-- =====================================================================

-- ---------------------------------------------------------------------
-- 31. CHAT_CONVERSATIONS
-- ---------------------------------------------------------------------

CREATE TABLE chat_conversations (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    listing_id VARCHAR(36),
    rental_request_id VARCHAR(36),

    tenant_id VARCHAR(36) NOT NULL,
    landlord_id VARCHAR(36) NOT NULL,

    last_message_at DATETIME,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_cc_listing
        FOREIGN KEY (listing_id)
        REFERENCES listings(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_cc_request
        FOREIGN KEY (rental_request_id)
        REFERENCES rental_requests(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_cc_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_cc_landlord
        FOREIGN KEY (landlord_id)
        REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 32. CHAT_MESSAGES
-- ---------------------------------------------------------------------

CREATE TABLE chat_messages (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    conversation_id VARCHAR(36) NOT NULL,
    sender_id VARCHAR(36) NOT NULL,

    content TEXT NOT NULL,

    message_type ENUM(
        'TEXT',
        'IMAGE',
        'SYSTEM'
    ) NOT NULL DEFAULT 'TEXT',

    is_read BOOLEAN DEFAULT FALSE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cm_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES chat_conversations(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_cm_sender
        FOREIGN KEY (sender_id)
        REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 33. NOTIFICATIONS
-- ---------------------------------------------------------------------

CREATE TABLE notifications (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    user_id VARCHAR(36) NOT NULL,

    type ENUM(
        'SYSTEM',
        'RENTAL_REQUEST',
        'RENTAL_AGREEMENT',
        'REPORT',
        'CHAT'
    ) NOT NULL,

    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,

    reference_type VARCHAR(50),
    reference_id VARCHAR(36),

    is_read BOOLEAN DEFAULT FALSE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notif_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =====================================================================
-- X. NHÓM BÁO CÁO & KHIẾU NẠI
-- =====================================================================

-- ---------------------------------------------------------------------
-- 34. REPORTS
-- ---------------------------------------------------------------------

CREATE TABLE reports (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    tenant_id VARCHAR(36) NOT NULL,

    landlord_id VARCHAR(36),
    listing_id VARCHAR(36),

    reason_code VARCHAR(100) NOT NULL,
    description TEXT,

    status ENUM(
        'PENDING',
        'PROCESSING',
        'RESOLVED',
        'REJECTED'
    ) NOT NULL DEFAULT 'PENDING',

    handled_by VARCHAR(36),
    resolution_note TEXT,

    resolved_at DATETIME,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_rep_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rep_landlord
        FOREIGN KEY (landlord_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rep_listing
        FOREIGN KEY (listing_id)
        REFERENCES listings(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rep_handled_by
        FOREIGN KEY (handled_by)
        REFERENCES users(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 35. REPORT_EVIDENCES
-- ---------------------------------------------------------------------

CREATE TABLE report_evidences (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    report_id VARCHAR(36) NOT NULL,

    image_url VARCHAR(255) NOT NULL,
    image_public_id VARCHAR(255),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_re_report
        FOREIGN KEY (report_id)
        REFERENCES reports(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =============================================================================
-- HOÀN TẤT
-- =============================================================================