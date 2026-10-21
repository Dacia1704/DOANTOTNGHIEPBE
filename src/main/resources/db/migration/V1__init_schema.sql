-- =====================================================================
-- V1__init_schema.sql  (MySQL 8.0.16+)  -  Rental Management System
-- ---------------------------------------------------------------------
-- Quy uoc:
--  * KHONG dung kieu ENUM(...) cua MySQL: dung VARCHAR + CHECK constraint
--    => JPA map enum de doc, va MySQL version khac van chay duoc.
--  * CHI XOA MEM: moi bang nghiep vu co is_deleted (BOOLEAN, mac dinh FALSE).
--  * MySQL KHONG ho tro filtered index (CREATE INDEX ... WHERE ...).
--    => Dung GENERATED COLUMN de lam UNIQUE co dieu kien:
--      - Cot `<ten>_uk` GENERATED ALWAYS AS (IF(is_deleted, NULL, <cot>)).
--      - MySQL cho phep NHIEU NULL trong UNIQUE index, nen nhieu ban ghi
--        da xoa mem van khong chan tao lai gia tri trung.
--  * Chuoi tieng Viet: utf8mb4 / utf8mb4_unicode_ci (mac dinh MySQL 8).
--  * Khoa chinh nghiep vu: CHAR(36) UUID. Bang trung gian: Composite PK.
--  * Thoi gian: DATETIME(6) + CURRENT_TIMESTAMP(6).
--  * CHECK constraint yeu cau MySQL >= 8.0.16 (da thuc thi).
-- =====================================================================

-- =====================================================================
-- I. NHOM TAI KHOAN & PHAN QUYEN (RBAC)
-- =====================================================================
-- =====================================================================
-- V1__init_schema.sql
-- MySQL 8.0+
-- ĐỒ ÁN: HỆ THỐNG CHO THUÊ PHÒNG TRỌ TRỰC TUYẾN
-- =====================================================================
--
-- Quy ước:
-- 1. Không sử dụng BaseEntity dùng chung.
-- 2. Không sử dụng created_by / updated_by / deleted.
-- 3. Không sử dụng soft delete.
-- 4. Các bảng mapping có id riêng.
-- 5. Dùng ENUM cho các trạng thái nghiệp vụ.
-- 6. created_at / updated_at chỉ xuất hiện ở những bảng cần thiết.
-- 7. UUID được sinh trực tiếp bởi MySQL.
-- =====================================================================


-- =====================================================================
-- DATABASE
-- =====================================================================

CREATE DATABASE IF NOT EXISTS DATN
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE DATN;


-- =====================================================================
-- I. NHÓM TÀI KHOẢN & PHÂN QUYỀN (RBAC)
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. USERS
-- ---------------------------------------------------------------------

CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(20),
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    avatar_url VARCHAR(255),
    avatar_public_id VARCHAR(255),
    citizen_id VARCHAR(20),

    email_verified_at DATETIME,
    phone_verified_at DATETIME,

    status ENUM(
        'ACTIVE',
        'INACTIVE',
        'BLOCKED'
    ) NOT NULL DEFAULT 'ACTIVE',

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


-- =====================================================================
-- II. NHÓM ĐỊA CHỈ HÀNH CHÍNH
-- =====================================================================


-- ---------------------------------------------------------------------
-- 6. PROVINCES
-- ---------------------------------------------------------------------

CREATE TABLE provinces (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ---------------------------------------------------------------------
-- 7. WARDS
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
-- III. NHÓM HỒ SƠ NGƯỜI THUÊ & TÌM KIẾM
-- =====================================================================


-- ---------------------------------------------------------------------
-- 8. ROOM_TYPES
-- ---------------------------------------------------------------------

CREATE TABLE room_types (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    name VARCHAR(100) NOT NULL,
    description TEXT,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ---------------------------------------------------------------------
-- 9. TENANT_PROFILES
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


-- ---------------------------------------------------------------------
-- 10. AMENITIES
-- ---------------------------------------------------------------------

CREATE TABLE amenities (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    name VARCHAR(100) NOT NULL,
    icon VARCHAR(255),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =====================================================================
-- IV. NHÓM PHÒNG & TIN ĐĂNG
-- =====================================================================


-- ---------------------------------------------------------------------
-- 11. ROOMS
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
    max_occupants TINYINT DEFAULT 1,

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
-- 12. ROOM_AMENITIES
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
-- 13. ROOM_IMAGES
-- ---------------------------------------------------------------------

CREATE TABLE room_images (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    room_id VARCHAR(36) NOT NULL,

    url VARCHAR(255) NOT NULL,
    public_id VARCHAR(255),

    sort_order TINYINT DEFAULT 0,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ri_room
        FOREIGN KEY (room_id)
        REFERENCES rooms(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ---------------------------------------------------------------------
-- 14. LISTINGS
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
-- 15. SAVED_LISTINGS
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
-- 16. LISTING_VIEW_HISTORY
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
-- 17. LISTING_CONTACT_EVENTS
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
-- 18. LISTING_DAILY_STATS
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
-- 19. RENTAL_REQUESTS
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
-- 20. RENTAL_AGREEMENTS
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

    payment_cycle_months TINYINT DEFAULT 1,

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
-- 21. SERVICE_PACKAGES
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
-- 22. SERVICE_PACKAGE_SUBSCRIPTIONS
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
-- 23. BOOST_SERVICE_PACKAGES
-- ---------------------------------------------------------------------

CREATE TABLE boost_service_packages (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    code VARCHAR(50) UNIQUE NOT NULL,

    name VARCHAR(100) NOT NULL,

    priority_level TINYINT DEFAULT 1,

    duration_days INT DEFAULT 1,

    price DECIMAL(12, 2) NOT NULL,

    is_active BOOLEAN DEFAULT TRUE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ---------------------------------------------------------------------
-- 24. BOOST_SERVICE_PACKAGE_SUBSCRIPTIONS
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
-- 25. PAYMENTS
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
-- 26. INVOICES
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
-- 27. ROOM_REVIEWS
-- ---------------------------------------------------------------------

CREATE TABLE room_reviews (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    rental_agreement_id VARCHAR(36) UNIQUE NOT NULL,

    tenant_id VARCHAR(36) NOT NULL,

    room_id VARCHAR(36) NOT NULL,

    rating TINYINT NOT NULL
        CHECK (rating >= 1 AND rating <= 5),

    comment TEXT,

    landlord_reply TEXT,

    is_hidden BOOLEAN DEFAULT FALSE,

    hidden_by VARCHAR(36),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_rr_agreement
        FOREIGN KEY (rental_agreement_id)
        REFERENCES rental_agreements(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rr_tenant_rev
        FOREIGN KEY (tenant_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rr_room
        FOREIGN KEY (room_id)
        REFERENCES rooms(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rr_hidden_by
        FOREIGN KEY (hidden_by)
        REFERENCES users(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ---------------------------------------------------------------------
-- 28. LANDLORD_REVIEWS
-- ---------------------------------------------------------------------

CREATE TABLE landlord_reviews (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    rental_agreement_id VARCHAR(36) UNIQUE NOT NULL,

    tenant_id VARCHAR(36) NOT NULL,

    landlord_id VARCHAR(36) NOT NULL,

    rating TINYINT NOT NULL
        CHECK (rating >= 1 AND rating <= 5),

    comment TEXT,

    landlord_reply TEXT,

    is_hidden BOOLEAN DEFAULT FALSE,

    hidden_by VARCHAR(36),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_lr_agreement
        FOREIGN KEY (rental_agreement_id)
        REFERENCES rental_agreements(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_lr_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_lr_landlord
        FOREIGN KEY (landlord_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_lr_hidden_by
        FOREIGN KEY (hidden_by)
        REFERENCES users(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ---------------------------------------------------------------------
-- 29. TENANT_REVIEWS
-- ---------------------------------------------------------------------

CREATE TABLE tenant_reviews (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    rental_agreement_id VARCHAR(36) UNIQUE NOT NULL,

    landlord_id VARCHAR(36) NOT NULL,

    tenant_id VARCHAR(36) NOT NULL,

    rating TINYINT NOT NULL
        CHECK (rating >= 1 AND rating <= 5),

    comment TEXT,

    is_hidden BOOLEAN DEFAULT FALSE,

    hidden_by VARCHAR(36),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_tr_agreement
        FOREIGN KEY (rental_agreement_id)
        REFERENCES rental_agreements(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_tr_landlord
        FOREIGN KEY (landlord_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_tr_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_tr_hidden_by
        FOREIGN KEY (hidden_by)
        REFERENCES users(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =====================================================================
-- IX. NHÓM CHAT & THÔNG BÁO
-- =====================================================================


-- ---------------------------------------------------------------------
-- 30. CHAT_CONVERSATIONS
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
-- 31. CHAT_MESSAGES
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
-- 32. NOTIFICATIONS
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
-- 33. REPORTS
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
-- 34. REPORT_EVIDENCES
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


-- =====================================================================
-- XI. SEED DATA
-- =====================================================================


-- =====================================================================
-- ROLES
-- =====================================================================

INSERT INTO roles (id, code, name) VALUES
    (UUID(), 'TENANT', 'Người thuê'),
    (UUID(), 'LANDLORD', 'Chủ trọ'),
    (UUID(), 'ADMIN', 'Quản trị viên');


-- =====================================================================
-- PERMISSIONS
-- =====================================================================

INSERT INTO permissions (id, code, name) VALUES

    -- Tài khoản
    (UUID(), 'USER_READ',
        'Xem tài khoản người dùng'),

    (UUID(), 'USER_WRITE',
        'Cập nhật tài khoản người dùng'),

    (UUID(), 'USER_BLOCK',
        'Khóa tài khoản người dùng'),

    -- Role / Permission
    (UUID(), 'ROLE_READ',
        'Xem vai trò'),

    (UUID(), 'ROLE_WRITE',
        'Cập nhật vai trò'),

    (UUID(), 'PERMISSION_READ',
        'Xem quyền'),

    (UUID(), 'PERMISSION_WRITE',
        'Cập nhật quyền'),

    -- Địa chỉ
    (UUID(), 'PROVINCE_READ',
        'Xem tỉnh/thành'),

    (UUID(), 'WARD_READ',
        'Xem phường/xã'),

    -- Phòng
    (UUID(), 'ROOM_READ',
        'Xem phòng'),

    (UUID(), 'ROOM_WRITE',
        'Quản lý phòng'),

    (UUID(), 'ROOM_TYPE_READ',
        'Xem loại phòng'),

    (UUID(), 'AMENITY_READ',
        'Xem tiện ích'),

    -- Tin đăng
    (UUID(), 'LISTING_READ',
        'Xem tin đăng'),

    (UUID(), 'LISTING_WRITE',
        'Quản lý tin đăng'),

    (UUID(), 'LISTING_APPROVE',
        'Duyệt tin đăng'),

    -- Yêu cầu thuê
    (UUID(), 'RENTAL_REQUEST_READ',
        'Xem yêu cầu thuê'),

    (UUID(), 'RENTAL_REQUEST_WRITE',
        'Xử lý yêu cầu thuê'),

    -- Hợp đồng
    (UUID(), 'RENTAL_AGREEMENT_READ',
        'Xem hợp đồng thuê'),

    (UUID(), 'RENTAL_AGREEMENT_WRITE',
        'Quản lý hợp đồng thuê'),

    -- Thanh toán
    (UUID(), 'PAYMENT_READ',
        'Xem thanh toán'),

    (UUID(), 'PAYMENT_WRITE',
        'Quản lý thanh toán'),

    (UUID(), 'INVOICE_READ',
        'Xem hóa đơn'),

    -- Gói dịch vụ
    (UUID(), 'SERVICE_PACKAGE_READ',
        'Xem gói dịch vụ'),

    (UUID(), 'SERVICE_PACKAGE_WRITE',
        'Quản lý gói dịch vụ'),

    (UUID(), 'BOOST_PACKAGE_READ',
        'Xem gói đẩy tin'),

    -- Đánh giá
    (UUID(), 'REVIEW_READ',
        'Xem đánh giá'),

    (UUID(), 'REVIEW_WRITE',
        'Quản lý đánh giá'),

    -- Chat
    (UUID(), 'CHAT_READ',
        'Xem hội thoại'),

    -- Notification
    (UUID(), 'NOTIFICATION_READ',
        'Xem thông báo'),

    (UUID(), 'NOTIFICATION_WRITE',
        'Quản lý thông báo'),

    -- Report
    (UUID(), 'REPORT_READ',
        'Xem báo cáo'),

    (UUID(), 'REPORT_HANDLE',
        'Xử lý báo cáo'),

    -- Statistics
    (UUID(), 'STATISTICS_READ',
        'Xem thống kê');


-- =====================================================================
-- ROLE PERMISSIONS - ADMIN
-- =====================================================================

INSERT INTO role_permissions (id, role_id, permission_id)
SELECT
    UUID(),
    r.id,
    p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'ADMIN';


-- =====================================================================
-- ROLE PERMISSIONS - LANDLORD
-- =====================================================================

INSERT INTO role_permissions (id, role_id, permission_id)
SELECT
    UUID(),
    r.id,
    p.id
FROM roles r
JOIN permissions p
    ON p.code IN (
        'USER_READ',

        'PROVINCE_READ',
        'WARD_READ',

        'ROOM_READ',
        'ROOM_WRITE',
        'ROOM_TYPE_READ',
        'AMENITY_READ',

        'LISTING_READ',
        'LISTING_WRITE',

        'RENTAL_REQUEST_READ',
        'RENTAL_REQUEST_WRITE',

        'RENTAL_AGREEMENT_READ',
        'RENTAL_AGREEMENT_WRITE',

        'PAYMENT_READ',
        'INVOICE_READ',

        'SERVICE_PACKAGE_READ',
        'BOOST_PACKAGE_READ',

        'REVIEW_READ',
        'REVIEW_WRITE',

        'CHAT_READ',

        'NOTIFICATION_READ',
        'NOTIFICATION_WRITE',

        'REPORT_READ',

        'STATISTICS_READ'
    )
WHERE r.code = 'LANDLORD';


-- =====================================================================
-- ROLE PERMISSIONS - TENANT
-- =====================================================================

INSERT INTO role_permissions (id, role_id, permission_id)
SELECT
    UUID(),
    r.id,
    p.id
FROM roles r
JOIN permissions p
    ON p.code IN (
        'USER_READ',
        'USER_WRITE',

        'PROVINCE_READ',
        'WARD_READ',

        'ROOM_READ',
        'ROOM_TYPE_READ',
        'AMENITY_READ',

        'LISTING_READ',

        'RENTAL_REQUEST_READ',
        'RENTAL_REQUEST_WRITE',

        'RENTAL_AGREEMENT_READ',

        'PAYMENT_READ',

        'REVIEW_READ',
        'REVIEW_WRITE',

        'CHAT_READ',

        'NOTIFICATION_READ',
        'NOTIFICATION_WRITE',

        'REPORT_READ',

        'STATISTICS_READ'
    )
WHERE r.code = 'TENANT';


-- =====================================================================
-- USERS DEMO
-- =====================================================================
--
-- Password:
-- Rental@123
--
-- BCrypt hash:
-- $2a$10$rESLuXqKgAf6GT2oG7.IkeXQlsAxhNJvhYe9C1SNgDhGvnqplgJEO
--
-- =====================================================================

INSERT INTO users (
    id,
    email,
    phone,
    password,
    full_name,
    status
) VALUES

(
    UUID(),
    'admin@rental.local',
    '0900000001',
    '$2a$10$rESLuXqKgAf6GT2oG7.IkeXQlsAxhNJvhYe9C1SNgDhGvnqplgJEO',
    'Quản trị viên',
    'ACTIVE'
),

(
    UUID(),
    'landlord@rental.local',
    '0900000002',
    '$2a$10$rESLuXqKgAf6GT2oG7.IkeXQlsAxhNJvhYe9C1SNgDhGvnqplgJEO',
    'Chủ trọ demo',
    'ACTIVE'
),

(
    UUID(),
    'tenant@rental.local',
    '0900000003',
    '$2a$10$rESLuXqKgAf6GT2oG7.IkeXQlsAxhNJvhYe9C1SNgDhGvnqplgJEO',
    'Người thuê demo',
    'ACTIVE'
);


-- =====================================================================
-- USER ROLES
-- =====================================================================

INSERT INTO user_roles (
    id,
    user_id,
    role_id
)
SELECT
    UUID(),
    u.id,
    r.id
FROM users u
JOIN roles r
    ON r.code = 'ADMIN'
WHERE u.email = 'admin@rental.local';


INSERT INTO user_roles (
    id,
    user_id,
    role_id
)
SELECT
    UUID(),
    u.id,
    r.id
FROM users u
JOIN roles r
    ON r.code = 'LANDLORD'
WHERE u.email = 'landlord@rental.local';


INSERT INTO user_roles (
    id,
    user_id,
    role_id
)
SELECT
    UUID(),
    u.id,
    r.id
FROM users u
JOIN roles r
    ON r.code = 'TENANT'
WHERE u.email = 'tenant@rental.local';


-- =====================================================================
-- ROOM TYPES DEMO
-- =====================================================================

INSERT INTO room_types (
    id,
    name,
    description
) VALUES

(
    UUID(),
    'Phòng trọ',
    'Phòng trọ đơn giản, đầy đủ tiện ích cơ bản'
),

(
    UUID(),
    'Studio',
    'Căn hộ studio cho thuê'
),

(
    UUID(),
    'Căn hộ mini',
    'Căn hộ mini 1-2 phòng ngủ'
),

(
    UUID(),
    'Phòng chung',
    'Phòng trọ chung, nhiều người cùng thuê'
);


-- =====================================================================
-- AMENITIES DEMO
-- =====================================================================

INSERT INTO amenities (
    id,
    name,
    icon
) VALUES

(
    UUID(),
    'Wifi',
    'wifi'
),

(
    UUID(),
    'Điều hòa',
    'air-conditioner'
),

(
    UUID(),
    'Máy giặt',
    'washing-machine'
),

(
    UUID(),
    'Nóng lạnh',
    'water-heater'
),

(
    UUID(),
    'Bếp',
    'kitchen'
),

(
    UUID(),
    'Tủ lạnh',
    'refrigerator'
),

(
    UUID(),
    'Ban công',
    'balcony'
),

(
    UUID(),
    'Gửi xe',
    'parking'
);


-- =====================================================================
-- HOÀN TẤT V1
-- =====================================================================