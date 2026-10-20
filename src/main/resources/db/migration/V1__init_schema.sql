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

-- ---------- ROLES ----------
CREATE TABLE roles (
    id          CHAR(36)     NOT NULL,
    code        VARCHAR(20)  NOT NULL COMMENT 'TENANT / LANDLORD / ADMIN',
    name        VARCHAR(50)  NOT NULL COMMENT 'Ten hien thi',
    description VARCHAR(255) NULL,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    code_uk     VARCHAR(20)  GENERATED ALWAYS AS (IF(is_deleted, NULL, code)) STORED,
    CONSTRAINT PK_roles PRIMARY KEY (id),
    CONSTRAINT CK_roles_code CHECK (code IN ('TENANT','LANDLORD','ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_roles_code_uk ON roles(code_uk);

-- ---------- PERMISSIONS ----------
CREATE TABLE permissions (
    id          CHAR(36)     NOT NULL,
    code        VARCHAR(50)  NOT NULL COMMENT 'VD: LISTING_APPROVE, USER_BLOCK, REPORT_HANDLE',
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    code_uk     VARCHAR(50)  GENERATED ALWAYS AS (IF(is_deleted, NULL, code)) STORED,
    CONSTRAINT PK_permissions PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_permissions_code_uk ON permissions(code_uk);

-- ---------- ROLE_PERMISSIONS (N-N) ----------
CREATE TABLE role_permissions (
    role_id       CHAR(36) NOT NULL,
    permission_id CHAR(36) NOT NULL,
    CONSTRAINT PK_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT FK_rp_role       FOREIGN KEY (role_id)       REFERENCES roles(id),
    CONSTRAINT FK_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_rp_permission_id ON role_permissions(permission_id);

-- ---------- USERS ----------
CREATE TABLE users (
    id                CHAR(36)      NOT NULL,
    email             VARCHAR(100)  NOT NULL COMMENT 'Email dung de dang nhap / lien he',
    phone             VARCHAR(20)   NULL,
    password          VARCHAR(255)  NOT NULL COMMENT 'BCrypt hash',
    full_name         VARCHAR(100)  NOT NULL,
    avatar_url        VARCHAR(500)  NULL COMMENT 'URL anh dai dien tren Cloudinary',
    avatar_public_id  VARCHAR(255)  NULL COMMENT 'public_id cua avatar tren Cloudinary',
    citizen_id        VARCHAR(20)   NULL COMMENT 'So CCCD/CMND',
    email_verified_at DATETIME(6)   NULL,
    phone_verified_at DATETIME(6)   NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / INACTIVE / BLOCKED',
    is_deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    email_uk          VARCHAR(100)  GENERATED ALWAYS AS (IF(is_deleted, NULL, email)) STORED,
    CONSTRAINT PK_users PRIMARY KEY (id),
    CONSTRAINT CK_users_status CHECK (status IN ('ACTIVE','INACTIVE','BLOCKED')),
    CONSTRAINT CK_users_email CHECK (email <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_users_email_uk ON users(email_uk);
CREATE INDEX IX_users_phone ON users(phone);
CREATE INDEX IX_users_citizen_id ON users(citizen_id);
CREATE INDEX IX_users_status ON users(status);

-- ---------- USER_ROLES (N-N) ----------
-- Mot user co the vua la TENANT vua la LANDLORD dong thoi.
CREATE TABLE user_roles (
    user_id CHAR(36) NOT NULL,
    role_id CHAR(36) NOT NULL,
    CONSTRAINT PK_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT FK_ur_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT FK_ur_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_ur_role_id ON user_roles(role_id);

-- ---------- REFRESH_TOKENS ----------
CREATE TABLE refresh_tokens (
    id         CHAR(36)     NOT NULL,
    user_id    CHAR(36)     NOT NULL,
    token      VARCHAR(2000) NOT NULL COMMENT 'JWT refresh token (raw)',
    expires_at DATETIME(6)  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT FK_rt_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Token la chuoi dai, dung index thuong tren 191 ky tu de tranh loi DUP KEY.
CREATE INDEX UX_refresh_tokens_token ON refresh_tokens(token(191));
CREATE INDEX IX_rt_user_id ON refresh_tokens(user_id);
CREATE INDEX IX_rt_expires_at ON refresh_tokens(expires_at);

-- =====================================================================
-- II. NHOM DIA CHI HANH CHINH
-- =====================================================================

CREATE TABLE provinces (
    id         CHAR(36)     NOT NULL,
    code       VARCHAR(20)  NOT NULL COMMENT 'Ma tinh/thanh pho',
    name       VARCHAR(100) NOT NULL,
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    code_uk    VARCHAR(20)  GENERATED ALWAYS AS (IF(is_deleted, NULL, code)) STORED,
    name_uk    VARCHAR(100) GENERATED ALWAYS AS (IF(is_deleted, NULL, name)) STORED,
    CONSTRAINT PK_provinces PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_provinces_code_uk ON provinces(code_uk);
CREATE UNIQUE INDEX UX_provinces_name_uk ON provinces(name_uk);

CREATE TABLE districts (
    id          CHAR(36)     NOT NULL,
    province_id CHAR(36)     NOT NULL,
    code        VARCHAR(20)  NOT NULL COMMENT 'Ma quan/huyen',
    name        VARCHAR(100) NOT NULL,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    code_uk     VARCHAR(20)  GENERATED ALWAYS AS (IF(is_deleted, NULL, code)) STORED,
    CONSTRAINT PK_districts PRIMARY KEY (id),
    CONSTRAINT FK_districts_province FOREIGN KEY (province_id) REFERENCES provinces(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_districts_code_uk ON districts(code_uk);
CREATE INDEX IX_districts_province_id ON districts(province_id);

CREATE TABLE wards (
    id          CHAR(36)     NOT NULL,
    district_id CHAR(36)     NOT NULL,
    code        VARCHAR(20)  NOT NULL COMMENT 'Ma phuong/xa',
    name        VARCHAR(100) NOT NULL,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    code_uk     VARCHAR(20)  GENERATED ALWAYS AS (IF(is_deleted, NULL, code)) STORED,
    CONSTRAINT PK_wards PRIMARY KEY (id),
    CONSTRAINT FK_wards_district FOREIGN KEY (district_id) REFERENCES districts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_wards_code_uk ON wards(code_uk);
CREATE INDEX IX_wards_district_id ON wards(district_id);

-- =====================================================================
-- III. NHOM HO SO NGUOI THUE & TIM KIEM
-- =====================================================================

-- ---------- ROOM_TYPES ----------
CREATE TABLE room_types (
    id          CHAR(36)     NOT NULL,
    name        VARCHAR(100) NOT NULL COMMENT 'VD: phong tro, studio, can ho mini',
    description VARCHAR(500) NULL,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    name_uk     VARCHAR(100) GENERATED ALWAYS AS (IF(is_deleted, NULL, name)) STORED,
    CONSTRAINT PK_room_types PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_room_types_name_uk ON room_types(name_uk);

-- ---------- AMENITIES ----------
CREATE TABLE amenities (
    id         CHAR(36)     NOT NULL,
    name       VARCHAR(100) NOT NULL COMMENT 'VD: Wifi, dieu hoa, may giat',
    icon       VARCHAR(255) NULL COMMENT 'Icon dai dien',
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    name_uk    VARCHAR(100) GENERATED ALWAYS AS (IF(is_deleted, NULL, name)) STORED,
    CONSTRAINT PK_amenities PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_amenities_name_uk ON amenities(name_uk);

-- ---------- TENANT_PROFILES (1-1 voi users) ----------
-- user_id vua la khoa chinh vua la khoa ngoai.
CREATE TABLE tenant_profiles (
    user_id              CHAR(36)      NOT NULL,
    desired_province_id  CHAR(36)      NULL,
    desired_district_id  CHAR(36)      NULL,
    desired_ward_id      CHAR(36)      NULL,
    price_min            DECIMAL(12,2) NULL,
    price_max            DECIMAL(12,2) NULL,
    area_min             DECIMAL(6,2)  NULL,
    area_max             DECIMAL(6,2)  NULL,
    desired_room_type_id CHAR(36)      NULL,
    created_at           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_tenant_profiles PRIMARY KEY (user_id),
    CONSTRAINT FK_tp_user      FOREIGN KEY (user_id)              REFERENCES users(id),
    CONSTRAINT FK_tp_province  FOREIGN KEY (desired_province_id)  REFERENCES provinces(id),
    CONSTRAINT FK_tp_district  FOREIGN KEY (desired_district_id)  REFERENCES districts(id),
    CONSTRAINT FK_tp_ward      FOREIGN KEY (desired_ward_id)      REFERENCES wards(id),
    CONSTRAINT FK_tp_room_type FOREIGN KEY (desired_room_type_id) REFERENCES room_types(id),
    CONSTRAINT CK_tp_price CHECK (price_min IS NULL OR price_max IS NULL OR price_max >= price_min),
    CONSTRAINT CK_tp_area  CHECK (area_min  IS NULL OR area_max  IS NULL OR area_max  >= area_min)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_tp_province  ON tenant_profiles(desired_province_id);
CREATE INDEX IX_tp_district  ON tenant_profiles(desired_district_id);
CREATE INDEX IX_tp_ward      ON tenant_profiles(desired_ward_id);
CREATE INDEX IX_tp_room_type ON tenant_profiles(desired_room_type_id);

-- =====================================================================
-- IV. NHOM PHONG & TIN DANG
-- =====================================================================

-- ---------- ROOMS ----------
CREATE TABLE rooms (
    id                CHAR(36)      NOT NULL,
    landlord_id       CHAR(36)      NOT NULL COMMENT 'Chu tro so huu phong',
    room_type_id      CHAR(36)      NOT NULL,
    price             DECIMAL(12,2) NOT NULL COMMENT 'Gia thue hien tai (VND)',
    electricity_price DECIMAL(10,2) NOT NULL DEFAULT 0,
    water_price       DECIMAL(10,2) NOT NULL DEFAULT 0,
    service_fee       DECIMAL(10,2) NOT NULL DEFAULT 0,
    default_deposit   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT 'Tien coc mac dinh',
    area_m2           DECIMAL(6,2)  NULL COMMENT 'Dien tich phong (m2)',
    max_occupants     INT           NULL COMMENT 'So nguoi toi da',
    province_id       CHAR(36)      NOT NULL,
    district_id       CHAR(36)      NOT NULL,
    ward_id           CHAR(36)      NOT NULL,
    address_line      VARCHAR(500)  NOT NULL COMMENT 'Dia chi chi tiet',
    latitude          DECIMAL(10,7) NULL COMMENT 'Vi do',
    longitude         DECIMAL(10,7) NULL COMMENT 'Kinh do',
    rental_status     VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',
    is_deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_rooms PRIMARY KEY (id),
    CONSTRAINT FK_rooms_landlord  FOREIGN KEY (landlord_id)  REFERENCES users(id),
    CONSTRAINT FK_rooms_room_type FOREIGN KEY (room_type_id) REFERENCES room_types(id),
    CONSTRAINT FK_rooms_province  FOREIGN KEY (province_id)  REFERENCES provinces(id),
    CONSTRAINT FK_rooms_district  FOREIGN KEY (district_id)  REFERENCES districts(id),
    CONSTRAINT FK_rooms_ward      FOREIGN KEY (ward_id)      REFERENCES wards(id),
    CONSTRAINT CK_rooms_price CHECK (price > 0),
    CONSTRAINT CK_rooms_rental_status CHECK (rental_status IN ('AVAILABLE','RENTED','MAINTENANCE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_rooms_landlord    ON rooms(landlord_id);
CREATE INDEX IX_rooms_room_type   ON rooms(room_type_id);
CREATE INDEX IX_rooms_location    ON rooms(province_id, district_id, ward_id);
CREATE INDEX IX_rooms_rental_status ON rooms(rental_status);
CREATE INDEX IX_rooms_price       ON rooms(price);

-- ---------- ROOM_AMENITIES (N-N) ----------
CREATE TABLE room_amenities (
    room_id    CHAR(36) NOT NULL,
    amenity_id CHAR(36) NOT NULL,
    CONSTRAINT PK_room_amenities PRIMARY KEY (room_id, amenity_id),
    CONSTRAINT FK_ramn_room    FOREIGN KEY (room_id)    REFERENCES rooms(id),
    CONSTRAINT FK_ramn_amenity FOREIGN KEY (amenity_id) REFERENCES amenities(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_ra_amenity_id ON room_amenities(amenity_id);

-- ---------- ROOM_IMAGES ----------
CREATE TABLE room_images (
    id         CHAR(36)     NOT NULL,
    room_id    CHAR(36)     NOT NULL,
    url        VARCHAR(500) NOT NULL COMMENT 'URL anh tren Cloudinary',
    public_id  VARCHAR(255) NOT NULL COMMENT 'public_id cua anh tren Cloudinary, dung de xoa',
    sort_order INT          NOT NULL DEFAULT 0 COMMENT 'Thu tu hien thi anh',
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_room_images PRIMARY KEY (id),
    CONSTRAINT FK_ri_room FOREIGN KEY (room_id) REFERENCES rooms(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_ri_room_id ON room_images(room_id, sort_order);

-- ---------- LISTINGS ----------
-- KHONG luu landlord_id: lay listing -> room -> landlord de tranh duplicate du lieu.
CREATE TABLE listings (
    id               CHAR(36)     NOT NULL,
    room_id          CHAR(36)     NOT NULL,
    title            VARCHAR(255) NOT NULL,
    slug             VARCHAR(255) NOT NULL COMMENT 'URL than thien',
    description      TEXT         NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(500) NULL COMMENT 'Ly do admin tu choi',
    approved_by      CHAR(36)     NULL COMMENT 'ID admin duyet tin',
    approved_at      DATETIME(6)  NULL,
    priority_level   INT          NOT NULL DEFAULT 0 COMMENT 'Muc uu tien hien thi',
    view_count       BIGINT       NOT NULL DEFAULT 0,
    contact_count    BIGINT       NOT NULL DEFAULT 0,
    expires_at       DATETIME(6)  NULL COMMENT 'Thoi diem tin het han',
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    slug_uk          VARCHAR(255) GENERATED ALWAYS AS (IF(is_deleted, NULL, slug)) STORED,
    CONSTRAINT PK_listings PRIMARY KEY (id),
    CONSTRAINT FK_listings_room     FOREIGN KEY (room_id)    REFERENCES rooms(id),
    CONSTRAINT FK_listings_approver FOREIGN KEY (approved_by) REFERENCES users(id),
    CONSTRAINT CK_listings_status   CHECK (status IN ('PENDING','APPROVED','REJECTED','HIDDEN','EXPIRED')),
    CONSTRAINT CK_listings_priority CHECK (priority_level >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_listings_slug_uk ON listings(slug_uk);
CREATE INDEX IX_listings_room     ON listings(room_id);
CREATE INDEX IX_listings_status   ON listings(status);
CREATE INDEX IX_listings_priority ON listings(priority_level DESC, created_at DESC);

-- =====================================================================
-- V. NHOM TUONG TAC & THONG KE
-- =====================================================================

-- ---------- SAVED_LISTINGS (N-N) ----------
-- PK (tenant_id, listing_id): cung mot nguoi khong luu cung mot tin nhieu lan.
CREATE TABLE saved_listings (
    tenant_id  CHAR(36)   NOT NULL,
    listing_id CHAR(36)   NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_saved_listings PRIMARY KEY (tenant_id, listing_id),
    CONSTRAINT FK_sl_tenant  FOREIGN KEY (tenant_id)  REFERENCES users(id),
    CONSTRAINT FK_sl_listing FOREIGN KEY (listing_id) REFERENCES listings(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_sl_listing_id ON saved_listings(listing_id);

-- ---------- LISTING_VIEW_HISTORY ----------
-- KHONG unique (tenant_id, listing_id): moi lan xem la mot su kien hop le.
CREATE TABLE listing_view_history (
    id         CHAR(36)   NOT NULL,
    tenant_id  CHAR(36)   NOT NULL,
    listing_id CHAR(36)   NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_listing_view_history PRIMARY KEY (id),
    CONSTRAINT FK_lvh_tenant  FOREIGN KEY (tenant_id)  REFERENCES users(id),
    CONSTRAINT FK_lvh_listing FOREIGN KEY (listing_id) REFERENCES listings(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_lvh_tenant        ON listing_view_history(tenant_id);
CREATE INDEX IX_lvh_listing       ON listing_view_history(listing_id);
CREATE INDEX IX_lvh_listing_created ON listing_view_history(listing_id, created_at);

-- ---------- LISTING_CONTACT_EVENTS ----------
CREATE TABLE listing_contact_events (
    id         CHAR(36)   NOT NULL,
    listing_id CHAR(36)   NOT NULL,
    tenant_id  CHAR(36)   NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_listing_contact_events PRIMARY KEY (id),
    CONSTRAINT FK_lce_listing FOREIGN KEY (listing_id) REFERENCES listings(id),
    CONSTRAINT FK_lce_tenant  FOREIGN KEY (tenant_id)  REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_lce_listing ON listing_contact_events(listing_id);
CREATE INDEX IX_lce_tenant  ON listing_contact_events(tenant_id);

-- ---------- LISTING_DAILY_STATS ----------
-- stat_date = ngay du lieu thuoc ve; created_at = thoi diem ghi nhan.
-- VD: stat_date = 2026-10-19, created_at = 2026-10-20 00:05
CREATE TABLE listing_daily_stats (
    id            CHAR(36)   NOT NULL,
    listing_id    CHAR(36)   NOT NULL,
    stat_date     DATE       NOT NULL COMMENT 'Ngay ma so lieu thuoc ve',
    view_count    BIGINT     NOT NULL DEFAULT 0,
    contact_count BIGINT     NOT NULL DEFAULT 0,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_listing_daily_stats PRIMARY KEY (id),
    CONSTRAINT FK_lds_listing      FOREIGN KEY (listing_id) REFERENCES listings(id),
    CONSTRAINT UQ_lds_listing_date UNIQUE (listing_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_lds_stat_date ON listing_daily_stats(stat_date);

-- =====================================================================
-- VI. NHOM YEU CAU THUE & HOP DONG
-- =====================================================================

-- ---------- RENTAL_REQUESTS ----------
-- landlord_id la SNAPSHOT cua chu tro tai thoi diem tao request.
CREATE TABLE rental_requests (
    id                    CHAR(36)    NOT NULL,
    listing_id            CHAR(36)    NOT NULL,
    tenant_id             CHAR(36)    NOT NULL,
    landlord_id           CHAR(36)    NOT NULL COMMENT 'Snapshot chu tro tai thoi diem tao',
    message               TEXT        NULL,
    proposed_move_in_date DATE        NULL COMMENT 'Ngay du kien chuyen vao',
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    responded_at          DATETIME(6) NULL COMMENT 'Thoi diem chu tro phan hoi',
    expires_at            DATETIME(6) NULL COMMENT 'Thoi diem yeu cau het han',
    pending_key           VARCHAR(100) NULL COMMENT 'Khoa ho tro kiem soat yeu cau pending',
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    -- Chi cho phep MOT yeu cau PENDING cho moi (listing, tenant).
    pending_uk            CHAR(73) GENERATED ALWAYS AS (
        IF(status = 'PENDING', CONCAT(listing_id, ':', tenant_id), NULL)
    ) STORED,
    CONSTRAINT PK_rental_requests PRIMARY KEY (id),
    CONSTRAINT FK_rr_listing  FOREIGN KEY (listing_id)  REFERENCES listings(id),
    CONSTRAINT FK_rr_tenant   FOREIGN KEY (tenant_id)   REFERENCES users(id),
    CONSTRAINT FK_rr_landlord FOREIGN KEY (landlord_id) REFERENCES users(id),
    CONSTRAINT CK_rr_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED','EXPIRED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_rr_pending_uk ON rental_requests(pending_uk);
CREATE INDEX IX_rr_listing  ON rental_requests(listing_id);
CREATE INDEX IX_rr_tenant   ON rental_requests(tenant_id);
CREATE INDEX IX_rr_landlord ON rental_requests(landlord_id);
CREATE INDEX IX_rr_status   ON rental_requests(status);

-- ---------- RENTAL_AGREEMENTS ----------
-- Luu SNAPSHOT gia va dieu khan tai thoi diem ky hop dong.
CREATE TABLE rental_agreements (
    id                    CHAR(36)      NOT NULL,
    agreement_code        VARCHAR(30)   NOT NULL COMMENT 'Ma hop dong',
    rental_request_id     CHAR(36)      NULL,
    room_id               CHAR(36)      NOT NULL,
    tenant_id             CHAR(36)      NOT NULL,
    landlord_id           CHAR(36)      NOT NULL,
    price                 DECIMAL(12,2) NOT NULL COMMENT 'Snapshot gia thue',
    deposit               DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT 'Snapshot tien coc',
    electricity_price     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT 'Snapshot gia dien',
    water_price           DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT 'Snapshot gia nuoc',
    service_fee           DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT 'Snapshot phi dich vu',
    payment_cycle_months  INT           NOT NULL DEFAULT 1 COMMENT 'Chu ky thanh toan (thang)',
    start_date            DATE          NOT NULL,
    end_date              DATE          NOT NULL,
    terms_text            TEXT          NULL COMMENT 'Dieu khan',
    pdf_url               VARCHAR(500)  NULL COMMENT 'URL PDF tren Cloudinary',
    pdf_public_id         VARCHAR(255)  NULL COMMENT 'public_id cua PDF tren Cloudinary',
    status                VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    created_by_role       VARCHAR(20)   NULL COMMENT 'LANDLORD / TENANT',
    landlord_confirmed_at DATETIME(6)   NULL,
    tenant_confirmed_at   DATETIME(6)   NULL,
    created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_rental_agreements PRIMARY KEY (id),
    CONSTRAINT FK_ra_request   FOREIGN KEY (rental_request_id) REFERENCES rental_requests(id),
    CONSTRAINT FK_ra_room      FOREIGN KEY (room_id)           REFERENCES rooms(id),
    CONSTRAINT FK_ra_tenant    FOREIGN KEY (tenant_id)         REFERENCES users(id),
    CONSTRAINT FK_ra_landlord  FOREIGN KEY (landlord_id)       REFERENCES users(id),
    CONSTRAINT CK_ra_status CHECK (status IN ('DRAFT','PENDING_CONFIRMATION','ACTIVE','EXPIRED','TERMINATED','CANCELLED')),
    CONSTRAINT CK_ra_created_by_role CHECK (created_by_role IS NULL OR created_by_role IN ('LANDLORD','TENANT')),
    CONSTRAINT CK_ra_dates CHECK (end_date >= start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_ra_agreement_code ON rental_agreements(agreement_code);
CREATE INDEX IX_ra_room     ON rental_agreements(room_id);
CREATE INDEX IX_ra_tenant   ON rental_agreements(tenant_id);
CREATE INDEX IX_ra_landlord ON rental_agreements(landlord_id);
CREATE INDEX IX_ra_status   ON rental_agreements(status);
CREATE INDEX IX_ra_dates    ON rental_agreements(start_date, end_date);

-- =====================================================================
-- VII. NHOM GOI DICH VU & THANH TOAN
-- =====================================================================

-- ---------- SERVICE_PACKAGES ----------
CREATE TABLE service_packages (
    id                  CHAR(36)      NOT NULL,
    code                VARCHAR(30)   NOT NULL,
    name                VARCHAR(100)  NOT NULL,
    max_listings        INT           NOT NULL DEFAULT 0 COMMENT 'So tin toi da',
    high_priority_quota INT           NOT NULL DEFAULT 0 COMMENT 'So luong tin uu tien cao',
    low_priority_quota  INT           NOT NULL DEFAULT 0 COMMENT 'So luong tin uu tien thap',
    has_stats           BOOLEAN       NOT NULL DEFAULT FALSE COMMENT 'Co quyen xem thong ke hay khong',
    price               DECIMAL(12,2) NOT NULL COMMENT 'Gia goi',
    billing_cycle_days  INT           NOT NULL DEFAULT 30 COMMENT 'So ngay su dung',
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE COMMENT 'Goi dang duoc ban hay khong',
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_service_packages PRIMARY KEY (id),
    CONSTRAINT CK_sp_price CHECK (price >= 0),
    CONSTRAINT CK_sp_cycle CHECK (billing_cycle_days > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_sp_code ON service_packages(code);

-- ---------- SERVICE_PACKAGE_SUBSCRIPTIONS ----------
CREATE TABLE service_package_subscriptions (
    id          CHAR(36)    NOT NULL,
    landlord_id CHAR(36)    NOT NULL,
    package_id  CHAR(36)    NOT NULL,
    start_at    DATETIME(6) NOT NULL,
    end_at      DATETIME(6) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active_flag BOOLEAN     NOT NULL DEFAULT TRUE COMMENT 'Danh dau subscription dang hoat dong',
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_service_package_subscriptions PRIMARY KEY (id),
    CONSTRAINT FK_sps_landlord FOREIGN KEY (landlord_id) REFERENCES users(id),
    CONSTRAINT FK_sps_package  FOREIGN KEY (package_id)  REFERENCES service_packages(id),
    CONSTRAINT CK_sps_status CHECK (status IN ('ACTIVE','EXPIRED','CANCELLED')),
    CONSTRAINT CK_sps_dates CHECK (end_at > start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_sps_landlord ON service_package_subscriptions(landlord_id);
CREATE INDEX IX_sps_package  ON service_package_subscriptions(package_id);
CREATE INDEX IX_sps_active   ON service_package_subscriptions(landlord_id, active_flag);

-- ---------- BOOST_SERVICE_PACKAGES ----------
CREATE TABLE boost_service_packages (
    id             CHAR(36)      NOT NULL,
    code           VARCHAR(30)   NOT NULL,
    name           VARCHAR(100)  NOT NULL,
    priority_level INT           NOT NULL DEFAULT 0 COMMENT 'Muc do uu tien',
    duration_days  INT           NOT NULL COMMENT 'So ngay hieu luc',
    price          DECIMAL(12,2) NOT NULL,
    is_active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_boost_service_packages PRIMARY KEY (id),
    CONSTRAINT CK_bsp_price    CHECK (price >= 0),
    CONSTRAINT CK_bsp_duration CHECK (duration_days > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_bsp_code ON boost_service_packages(code);

-- ---------- BOOST_SERVICE_PACKAGE_SUBSCRIPTIONS ----------
CREATE TABLE boost_service_package_subscriptions (
    id               CHAR(36)      NOT NULL,
    landlord_id      CHAR(36)      NOT NULL,
    listing_id       CHAR(36)      NOT NULL COMMENT 'Tin dang duoc boost',
    boost_package_id CHAR(36)      NOT NULL,
    start_at         DATETIME(6)   NOT NULL,
    end_at           DATETIME(6)   NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    price            DECIMAL(12,2) NOT NULL COMMENT 'Gia thuc te tai thoi diem mua',
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_boost_service_package_subscriptions PRIMARY KEY (id),
    CONSTRAINT FK_bsps_landlord FOREIGN KEY (landlord_id)      REFERENCES users(id),
    CONSTRAINT FK_bsps_listing  FOREIGN KEY (listing_id)       REFERENCES listings(id),
    CONSTRAINT FK_bsps_package  FOREIGN KEY (boost_package_id) REFERENCES boost_service_packages(id),
    CONSTRAINT CK_bsps_status CHECK (status IN ('ACTIVE','EXPIRED','CANCELLED')),
    CONSTRAINT CK_bsps_dates CHECK (end_at > start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_bsps_landlord ON boost_service_package_subscriptions(landlord_id);
CREATE INDEX IX_bsps_listing  ON boost_service_package_subscriptions(listing_id);

-- ---------- PAYMENTS ----------
-- reference_id la POLYMORPHIC reference: khong tao FK vat ly.
-- VD: type = SERVICE_PACKAGE, reference_id = ID cua service_package_subscription.
-- => phai validation o Service.
CREATE TABLE payments (
    id              CHAR(36)      NOT NULL,
    landlord_id     CHAR(36)      NOT NULL COMMENT 'Nguoi thuc hien thanh toan',
    type            VARCHAR(30)   NOT NULL COMMENT 'SERVICE_PACKAGE / BOOST_PACKAGE',
    reference_id    CHAR(36)      NOT NULL COMMENT 'ID doi tuong duoc thanh toan (polymorphic)',
    amount          DECIMAL(12,2) NOT NULL,
    payment_method  VARCHAR(20)   NOT NULL COMMENT 'VNPAY / MOMO / BANK_TRANSFER',
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    provider_txn_id VARCHAR(255)  NULL COMMENT 'Ma giao dich cua cong thanh toan',
    paid_at         DATETIME(6)   NULL COMMENT 'Thoi diem thanh toan thanh cong',
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_payments PRIMARY KEY (id),
    CONSTRAINT FK_pay_landlord FOREIGN KEY (landlord_id) REFERENCES users(id),
    CONSTRAINT CK_pay_type   CHECK (type IN ('SERVICE_PACKAGE','BOOST_PACKAGE')),
    CONSTRAINT CK_pay_method CHECK (payment_method IN ('VNPAY','MOMO','BANK_TRANSFER')),
    CONSTRAINT CK_pay_status CHECK (status IN ('PENDING','SUCCESS','FAILED','REFUNDED')),
    CONSTRAINT CK_pay_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_pay_landlord ON payments(landlord_id);
CREATE INDEX IX_pay_type_ref ON payments(type, reference_id);
CREATE INDEX IX_pay_status    ON payments(status);

-- ---------- INVOICES ----------
CREATE TABLE invoices (
    id                       CHAR(36)      NOT NULL,
    payment_id               CHAR(36)      NOT NULL,
    type                     VARCHAR(30)   NOT NULL COMMENT 'SERVICE_PACKAGE / BOOST_PACKAGE',
    service_package_id       CHAR(36)      NULL,
    boost_service_package_id CHAR(36)      NULL,
    invoice_no               VARCHAR(50)   NOT NULL COMMENT 'So hoa don',
    tax_amount               DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount             DECIMAL(12,2) NOT NULL,
    buyer_name               VARCHAR(100)  NOT NULL COMMENT 'Ten nguoi mua tai thoi diem xuat hoa don',
    buyer_tax_code           VARCHAR(20)   NULL COMMENT 'Ma so thue',
    issued_at                DATETIME(6)   NOT NULL,
    created_at               DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_invoices PRIMARY KEY (id),
    CONSTRAINT FK_inv_payment FOREIGN KEY (payment_id)               REFERENCES payments(id),
    CONSTRAINT FK_inv_sp      FOREIGN KEY (service_package_id)       REFERENCES service_packages(id),
    CONSTRAINT FK_inv_bsp     FOREIGN KEY (boost_service_package_id) REFERENCES boost_service_packages(id),
    CONSTRAINT CK_inv_type  CHECK (type IN ('SERVICE_PACKAGE','BOOST_PACKAGE')),
    CONSTRAINT CK_inv_total CHECK (total_amount >= 0),
    CONSTRAINT CK_inv_tax   CHECK (tax_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_inv_no      ON invoices(invoice_no);
CREATE INDEX IX_inv_payment      ON invoices(payment_id);
CREATE INDEX IX_inv_sp           ON invoices(service_package_id);
CREATE INDEX IX_inv_bsp          ON invoices(boost_service_package_id);

-- =====================================================================
-- VIII. NHOM DANH GIA
-- =====================================================================

-- ---------- ROOM_REVIEWS ----------
-- Mot hop dong chi duoc danh gia phong MOT LAN => rental_agreement_id UNIQUE.
CREATE TABLE room_reviews (
    id                  CHAR(36)    NOT NULL,
    rental_agreement_id CHAR(36)    NOT NULL COMMENT 'Hop dong lam can cu danh gia',
    tenant_id           CHAR(36)    NOT NULL COMMENT 'Nguoi danh gia',
    room_id             CHAR(36)    NOT NULL COMMENT 'Phong duoc danh gia',
    rating              INT         NOT NULL COMMENT 'Diem tu 1 den 5',
    comment             TEXT        NULL,
    landlord_reply      TEXT        NULL COMMENT 'Phan hoi cua chu tro',
    is_hidden           BOOLEAN     NOT NULL DEFAULT FALSE,
    hidden_by           CHAR(36)    NULL COMMENT 'Nguoi thuc hien an',
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_room_reviews PRIMARY KEY (id),
    CONSTRAINT FK_rrev_agreement FOREIGN KEY (rental_agreement_id) REFERENCES rental_agreements(id),
    CONSTRAINT FK_rrev_tenant    FOREIGN KEY (tenant_id)           REFERENCES users(id),
    CONSTRAINT FK_rrev_room      FOREIGN KEY (room_id)             REFERENCES rooms(id),
    CONSTRAINT FK_rrev_hider     FOREIGN KEY (hidden_by)           REFERENCES users(id),
    CONSTRAINT CK_rr_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_rr_agreement ON room_reviews(rental_agreement_id);
CREATE INDEX IX_rr_room   ON room_reviews(room_id);
CREATE INDEX IX_rr_tenant ON room_reviews(tenant_id);

-- ---------- LANDLORD_REVIEWS ----------
-- Tenant danh gia chu tro. 1 hop dong = 1 lan danh gia chu tro.
CREATE TABLE landlord_reviews (
    id                  CHAR(36)    NOT NULL,
    rental_agreement_id CHAR(36)    NOT NULL,
    tenant_id           CHAR(36)    NOT NULL COMMENT 'Nguoi danh gia',
    landlord_id         CHAR(36)    NOT NULL COMMENT 'Chu tro duoc danh gia',
    rating              INT         NOT NULL,
    comment             TEXT        NULL,
    landlord_reply      TEXT        NULL,
    is_hidden           BOOLEAN     NOT NULL DEFAULT FALSE,
    hidden_by           CHAR(36)    NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_landlord_reviews PRIMARY KEY (id),
    CONSTRAINT FK_lr_agreement FOREIGN KEY (rental_agreement_id) REFERENCES rental_agreements(id),
    CONSTRAINT FK_lr_tenant    FOREIGN KEY (tenant_id)           REFERENCES users(id),
    CONSTRAINT FK_lr_landlord  FOREIGN KEY (landlord_id)         REFERENCES users(id),
    CONSTRAINT FK_lr_hider     FOREIGN KEY (hidden_by)           REFERENCES users(id),
    CONSTRAINT CK_lr_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_lr_agreement ON landlord_reviews(rental_agreement_id);
CREATE INDEX IX_lr_landlord ON landlord_reviews(landlord_id);
CREATE INDEX IX_lr_tenant  ON landlord_reviews(tenant_id);

-- ---------- TENANT_REVIEWS ----------
-- Landlord danh gia nguoi thue. 1 hop dong = 1 lan danh gia nguoi thue.
CREATE TABLE tenant_reviews (
    id                  CHAR(36)    NOT NULL,
    rental_agreement_id CHAR(36)    NOT NULL,
    landlord_id         CHAR(36)    NOT NULL COMMENT 'Nguoi danh gia',
    tenant_id           CHAR(36)    NOT NULL COMMENT 'Nguoi duoc danh gia',
    rating              INT         NOT NULL,
    comment             TEXT        NULL,
    is_hidden           BOOLEAN     NOT NULL DEFAULT FALSE,
    hidden_by           CHAR(36)    NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_tenant_reviews PRIMARY KEY (id),
    CONSTRAINT FK_tr_agreement FOREIGN KEY (rental_agreement_id) REFERENCES rental_agreements(id),
    CONSTRAINT FK_tr_landlord  FOREIGN KEY (landlord_id)         REFERENCES users(id),
    CONSTRAINT FK_tr_tenant    FOREIGN KEY (tenant_id)           REFERENCES users(id),
    CONSTRAINT FK_tr_hider     FOREIGN KEY (hidden_by)           REFERENCES users(id),
    CONSTRAINT CK_tr_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX UX_tr_agreement ON tenant_reviews(rental_agreement_id);
CREATE INDEX IX_tr_landlord ON tenant_reviews(landlord_id);
CREATE INDEX IX_tr_tenant   ON tenant_reviews(tenant_id);

-- =====================================================================
-- IX. NHOM CHAT & THONG BAO
-- =====================================================================

-- ---------- CHAT_CONVERSATIONS ----------
CREATE TABLE chat_conversations (
    id                CHAR(36)    NOT NULL,
    listing_id        CHAR(36)    NULL COMMENT 'Tin dang lien quan',
    rental_request_id CHAR(36)    NULL COMMENT 'Request lien quan neu co',
    tenant_id         CHAR(36)    NOT NULL,
    landlord_id       CHAR(36)    NOT NULL,
    last_message_at   DATETIME(6) NULL COMMENT 'Thoi diem tin nhan cuoi',
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_chat_conversations PRIMARY KEY (id),
    CONSTRAINT FK_cc_listing  FOREIGN KEY (listing_id)        REFERENCES listings(id),
    CONSTRAINT FK_cc_request  FOREIGN KEY (rental_request_id) REFERENCES rental_requests(id),
    CONSTRAINT FK_cc_tenant   FOREIGN KEY (tenant_id)         REFERENCES users(id),
    CONSTRAINT FK_cc_landlord FOREIGN KEY (landlord_id)       REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_cc_listing     ON chat_conversations(listing_id);
CREATE INDEX IX_cc_request     ON chat_conversations(rental_request_id);
CREATE INDEX IX_cc_tenant      ON chat_conversations(tenant_id);
CREATE INDEX IX_cc_landlord    ON chat_conversations(landlord_id);
CREATE INDEX IX_cc_last_message ON chat_conversations(last_message_at DESC);

-- ---------- CHAT_MESSAGES ----------
CREATE TABLE chat_messages (
    id              CHAR(36)    NOT NULL,
    conversation_id CHAR(36)    NOT NULL,
    sender_id       CHAR(36)    NOT NULL,
    content         TEXT        NOT NULL,
    message_type    VARCHAR(20) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT / IMAGE / SYSTEM',
    is_read         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_chat_messages PRIMARY KEY (id),
    CONSTRAINT FK_cm_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id),
    CONSTRAINT FK_cm_sender       FOREIGN KEY (sender_id)       REFERENCES users(id),
    CONSTRAINT CK_cm_type CHECK (message_type IN ('TEXT','IMAGE','SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_cm_conversation ON chat_messages(conversation_id, created_at);
CREATE INDEX IX_cm_sender       ON chat_messages(sender_id);
CREATE INDEX IX_cm_unread       ON chat_messages(conversation_id, is_read);

-- ---------- NOTIFICATIONS ----------
-- reference_type + reference_id la POLYMORPHIC reference: khong tao FK vat ly.
CREATE TABLE notifications (
    id             CHAR(36)    NOT NULL,
    user_id        CHAR(36)    NOT NULL COMMENT 'Nguoi nhan',
    type           VARCHAR(30) NOT NULL COMMENT 'SYSTEM / RENTAL_REQUEST / RENTAL_AGREEMENT / REPORT / CHAT',
    title          VARCHAR(255) NOT NULL,
    body           TEXT        NULL,
    reference_type VARCHAR(50) NULL COMMENT 'Loai entity lien quan',
    reference_id   CHAR(36)    NULL COMMENT 'ID entity lien quan (polymorphic)',
    is_read        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_notifications PRIMARY KEY (id),
    CONSTRAINT FK_nt_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT CK_nt_type CHECK (type IN ('SYSTEM','RENTAL_REQUEST','RENTAL_AGREEMENT','REPORT','CHAT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_nt_user      ON notifications(user_id, is_read, created_at);
CREATE INDEX IX_nt_reference ON notifications(reference_type, reference_id);

-- =====================================================================
-- X. NHOM BAO CAO & KHIEU NAI
-- =====================================================================

-- ---------- REPORTS ----------
CREATE TABLE reports (
    id              CHAR(36)    NOT NULL,
    tenant_id       CHAR(36)    NOT NULL COMMENT 'Nguoi gui bao cao',
    landlord_id     CHAR(36)    NOT NULL COMMENT 'Chu tro bi bao cao',
    listing_id      CHAR(36)    NULL COMMENT 'Tin dang bi bao cao',
    reason_code     VARCHAR(50) NOT NULL COMMENT 'Ma ly do bao cao',
    description     TEXT        NULL COMMENT 'Noi dung chi tiet',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    handled_by      CHAR(36)    NULL COMMENT 'Admin xu ly',
    resolution_note TEXT        NULL COMMENT 'Ghi chu ket qua xu ly',
    resolved_at     DATETIME(6) NULL COMMENT 'Thoi diem giai quyet',
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_reports PRIMARY KEY (id),
    CONSTRAINT FK_rp_tenant   FOREIGN KEY (tenant_id)   REFERENCES users(id),
    CONSTRAINT FK_rp_landlord FOREIGN KEY (landlord_id) REFERENCES users(id),
    CONSTRAINT FK_rp_listing  FOREIGN KEY (listing_id)  REFERENCES listings(id),
    CONSTRAINT FK_rp_handler  FOREIGN KEY (handled_by)  REFERENCES users(id),
    CONSTRAINT CK_rp_status CHECK (status IN ('PENDING','PROCESSING','RESOLVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_rp_tenant   ON reports(tenant_id);
CREATE INDEX IX_rp_landlord ON reports(landlord_id);
CREATE INDEX IX_rp_listing  ON reports(listing_id);
CREATE INDEX IX_rp_status   ON reports(status);

-- ---------- REPORT_EVIDENCES ----------
CREATE TABLE report_evidences (
    id              CHAR(36)     NOT NULL,
    report_id       CHAR(36)     NOT NULL,
    image_url       VARCHAR(500) NOT NULL COMMENT 'URL anh bang chung tren Cloudinary',
    image_public_id VARCHAR(255) NOT NULL COMMENT 'public_id cua anh, dung de xoa',
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT PK_report_evidences PRIMARY KEY (id),
    CONSTRAINT FK_re_report FOREIGN KEY (report_id) REFERENCES reports(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IX_re_report_id ON report_evidences(report_id);

-- =====================================================================
-- DU LIEU KHOI TAO (SEED DATA)
-- =====================================================================

-- ---------- ROLES ----------
INSERT INTO roles (id, code, name, description) VALUES
 (UUID(), 'TENANT',   'Người thuê',    'Tài khoản người thuê phòng'),
 (UUID(), 'LANDLORD', 'Chủ trọ',       'Tài khoản chủ trọ, sở hữu phòng'),
 (UUID(), 'ADMIN',    'Quản trị viên', 'Quản trị hệ thống');

-- ---------- PERMISSIONS ----------
INSERT INTO permissions (id, code, name, description) VALUES
 -- Tai khoan
 (UUID(), 'USER_READ',           'Xem tài khoản người dùng',      'Xem danh sách và chi tiết tài khoản'),
 (UUID(), 'USER_WRITE',          'Cập nhật tài khoản người dùng', 'Tạo và cập nhật tài khoản'),
 (UUID(), 'USER_BLOCK',          'Khóa tài khoản người dùng',     'Khóa mở tài khoản vi phạm'),
 -- Vai tro va quyen
 (UUID(), 'ROLE_READ',           'Xem vai trò',                   'Xem danh sách vai trò'),
 (UUID(), 'ROLE_WRITE',          'Cập nhật vai trò',              'Tạo và cập nhật vai trò'),
 (UUID(), 'PERMISSION_READ',     'Xem quyền',                     'Xem danh sách quyền'),
 (UUID(), 'PERMISSION_WRITE',    'Cập nhật quyền',                'Tạo và cập nhật quyền'),
 -- Dia chi hanh chinh
 (UUID(), 'PROVINCE_READ',       'Xem tỉnh/thành',                'Xem danh sách tỉnh thành phố'),
 (UUID(), 'DISTRICT_READ',       'Xem quận/huyện',                'Xem danh sách quận huyện'),
 (UUID(), 'WARD_READ',           'Xem phường/xã',                 'Xem danh sách phường xã'),
 -- Phong
 (UUID(), 'ROOM_READ',           'Xem phòng',                     'Xem danh sách và chi tiết phòng'),
 (UUID(), 'ROOM_WRITE',          'Quản lý phòng',                 'Tạo và cập nhật phòng'),
 (UUID(), 'ROOM_TYPE_READ',      'Xem loại phòng',                'Xem danh sách loại phòng'),
 (UUID(), 'AMENITY_READ',        'Xem tiện ích',                  'Xem danh sách tiện ích'),
 -- Tin dang
 (UUID(), 'LISTING_READ',        'Xem tin đăng',                  'Xem danh sách tin đăng'),
 (UUID(), 'LISTING_WRITE',       'Quản lý tin đăng',              'Tạo và cập nhật tin đăng'),
 (UUID(), 'LISTING_APPROVE',     'Duyệt tin đăng',                'Phê duyệt hoặc từ chối tin đăng'),
 -- Yeu cau thue va hop dong
 (UUID(), 'RENTAL_REQUEST_READ',    'Xem yêu cầu thuê',      'Xem danh sách yêu cầu thuê'),
 (UUID(), 'RENTAL_REQUEST_WRITE',   'Xử lý yêu cầu thuê',    'Tạo, chấp nhận, từ chối yêu cầu'),
 (UUID(), 'RENTAL_AGREEMENT_READ',  'Xem hợp đồng thuê',     'Xem danh sách hợp đồng'),
 (UUID(), 'RENTAL_AGREEMENT_WRITE', 'Quản lý hợp đồng thuê', 'Tạo và cập nhật hợp đồng'),
 -- Thanh toan
 (UUID(), 'PAYMENT_READ',        'Xem thanh toán',                 'Xem lịch sử thanh toán'),
 (UUID(), 'PAYMENT_WRITE',       'Quản lý thanh toán',             'Xử lý giao dịch thanh toán'),
 (UUID(), 'INVOICE_READ',        'Xem hóa đơn',                    'Xem hóa đơn'),
 -- Goi dich vu
 (UUID(), 'SERVICE_PACKAGE_READ',  'Xem gói dịch vụ',      'Xem danh sách gói dịch vụ'),
 (UUID(), 'SERVICE_PACKAGE_WRITE', 'Quản lý gói dịch vụ',  'Tạo và cập nhật gói dịch vụ'),
 (UUID(), 'BOOST_PACKAGE_READ',    'Xem gói đẩy tin',      'Xem danh sách gói đẩy tin'),
 -- Danh gia
 (UUID(), 'REVIEW_READ',         'Xem đánh giá',                   'Xem danh sách đánh giá'),
 (UUID(), 'REVIEW_WRITE',        'Quản lý đánh giá',               'Tạo và cập nhật đánh giá'),
 -- Chat va thong bao
 (UUID(), 'CHAT_READ',           'Xem hội thoại',                  'Xem cuộc trò chuyện'),
 (UUID(), 'NOTIFICATION_READ',   'Xem thông báo',                  'Xem danh sách thông báo'),
 (UUID(), 'NOTIFICATION_WRITE',  'Quản lý thông báo',              'Gửi thông báo'),
 -- Bao cao
 (UUID(), 'REPORT_READ',         'Xem báo cáo',                    'Xem danh sách báo cáo'),
 (UUID(), 'REPORT_HANDLE',       'Xử lý báo cáo',                  'Xử lý và giải quyết báo cáo'),
 -- Thong ke
 (UUID(), 'STATISTICS_READ',     'Xem thống kê',                   'Xem báo cáo thống kê');

-- ---------- ROLE_PERMISSIONS ----------
-- ADMIN: toan bo quyen
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN';

-- LANDLORD: quan ly phong + tin dang + yeu cau thue + hop dong + goi dich vu + thanh toan
--          KHONG co: LISTING_APPROVE, USER_BLOCK, USER_WRITE, REPORT_HANDLE, ROLE_*, PERMISSION_*
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'USER_READ',
    'PROVINCE_READ','DISTRICT_READ','WARD_READ',
    'ROOM_READ','ROOM_WRITE','ROOM_TYPE_READ','AMENITY_READ',
    'LISTING_READ','LISTING_WRITE',
    'RENTAL_REQUEST_READ','RENTAL_REQUEST_WRITE',
    'RENTAL_AGREEMENT_READ','RENTAL_AGREEMENT_WRITE',
    'PAYMENT_READ','INVOICE_READ',
    'SERVICE_PACKAGE_READ','BOOST_PACKAGE_READ',
    'REVIEW_READ','REVIEW_WRITE',
    'CHAT_READ','NOTIFICATION_READ','NOTIFICATION_WRITE',
    'REPORT_READ','STATISTICS_READ'
)
WHERE r.code = 'LANDLORD';

-- TENANT: tim kiem phong + luu tin + yeu cau thue + hop dong + danh gia + chat
--         KHONG co: ROOM_WRITE, LISTING_WRITE, LISTING_APPROVE, USER_BLOCK, REPORT_HANDLE
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'USER_READ','USER_WRITE',
    'PROVINCE_READ','DISTRICT_READ','WARD_READ',
    'ROOM_READ','ROOM_TYPE_READ','AMENITY_READ',
    'LISTING_READ',
    'RENTAL_REQUEST_READ','RENTAL_REQUEST_WRITE',
    'RENTAL_AGREEMENT_READ',
    'PAYMENT_READ',
    'REVIEW_READ','REVIEW_WRITE',
    'CHAT_READ','NOTIFICATION_READ','NOTIFICATION_WRITE',
    'REPORT_READ','STATISTICS_READ'
)
WHERE r.code = 'TENANT';

-- ---------- USERS DEMO ----------
-- Mat khau chung cua ca 3 tai khoan: Rental@123  (BCrypt, doi sau khi chay that)
INSERT INTO users (id, email, phone, password, full_name, status) VALUES
 (UUID(), 'admin@rental.local',    '0900000001',
  '$2a$10$rESLuXqKgAf6GT2oG7.IkeXQlsAxhNJvhYe9C1SNgDhGvnqplgJEO', 'Quản trị viên',  'ACTIVE'),
 (UUID(), 'landlord@rental.local', '0900000002',
  '$2a$10$rESLuXqKgAf6GT2oG7.IkeXQlsAxhNJvhYe9C1SNgDhGvnqplgJEO', 'Chủ trọ demo',   'ACTIVE'),
 (UUID(), 'tenant@rental.local',    '0900000003',
  '$2a$10$rESLuXqKgAf6GT2oG7.IkeXQlsAxhNJvhYe9C1SNgDhGvnqplgJEO', 'Người thuê demo','ACTIVE');

-- Gan role cho user demo
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'ADMIN'
WHERE u.email = 'admin@rental.local';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'LANDLORD'
WHERE u.email = 'landlord@rental.local';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'TENANT'
WHERE u.email = 'tenant@rental.local';

-- ---------- ROOM_TYPES DEMO ----------
INSERT INTO room_types (id, name, description) VALUES
 (UUID(), 'Phòng trọ',   'Phòng trọ đơn giản, đầy đủ tiện ích cơ bản'),
 (UUID(), 'Studio',      'Căn hộ studio cho thuê ngắn hạn'),
 (UUID(), 'Căn hộ mini', 'Căn hộ mini 1-2 phòng ngủ'),
 (UUID(), 'Phòng chung', 'Phòng trọ chung, nhiều người cùng thuê');

-- ---------- AMENITIES DEMO ----------
INSERT INTO amenities (id, name, icon) VALUES
 (UUID(), 'Wifi',      'wifi'),
 (UUID(), 'Điều hòa',  'air-conditioner'),
 (UUID(), 'Máy giặt',  'washing-machine'),
 (UUID(), 'Nóng lạnh', 'water-heater'),
 (UUID(), 'Bếp',       'kitchen'),
 (UUID(), 'Tủ lạnh',   'refrigerator'),
 (UUID(), 'Ban công',  'balcony'),
 (UUID(), 'Gửi xe',    'parking');