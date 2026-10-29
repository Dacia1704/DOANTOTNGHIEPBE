-- =====================================================================
-- AI SERVICE SUPPORT - tenant profile enrichment
-- ---------------------------------------------------------------------
-- Dich vu AI (FastAPI) doc schema nay de cham diem goi y va tra loi chatbot.
-- AI service chi co quyen SELECT tren cac bang nghiep vu, va co quyen
-- doc-ghi rieng tren schema rental_ai.
--
-- Luu y: Day la migration VI, nen chi them cot/bang, khong sua migration da
-- ap dung truoc do.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. COT MOI TRONG tenant_profiles
-- ---------------------------------------------------------------------
-- Ten moi truong cho nhu cau cua tenant. Cac cot nay KHONG bat buoc nhap
-- (truong hop chi co gia tri mac dinh), de form tao tai khoan van ngan.
-- AI service dung chung de cham diem va dien mac dinh cho chatbot.

ALTER TABLE tenant_profiles
    ADD COLUMN tenant_type ENUM('STUDENT','WORKER','FAMILY','OTHER') NULL AFTER user_id,
    ADD COLUMN occupants INT NULL,
    ADD COLUMN move_in_date DATE NULL,
    ADD COLUMN anchor_label VARCHAR(255) NULL,
    ADD COLUMN anchor_latitude DECIMAL(10, 8) NULL,
    ADD COLUMN anchor_longitude DECIMAL(11, 8) NULL,
    ADD COLUMN max_distance_km DECIMAL(5, 2) NULL,
    ADD COLUMN price_tolerance_pct TINYINT NOT NULL DEFAULT 20,
    ADD COLUMN top_priority ENUM('PRICE','LOCATION','AREA','AMENITIES') NULL,
    ADD COLUMN note VARCHAR(500) NULL,
    ADD COLUMN allow_personalization BOOLEAN NOT NULL DEFAULT TRUE;

-- Cac rang buoc CHECK dat tren cot KHONG co FK nen khong vuong gioi han
-- cua MySQL ve CHECK + ON DELETE.
ALTER TABLE tenant_profiles
    ADD CONSTRAINT chk_tp_price
        CHECK (price_min IS NULL OR price_max IS NULL OR price_min <= price_max),
    ADD CONSTRAINT chk_tp_area
        CHECK (area_min IS NULL OR area_max IS NULL OR area_min <= area_max),
    ADD CONSTRAINT chk_tp_occupants
        CHECK (occupants IS NULL OR occupants >= 1),
    ADD CONSTRAINT chk_tp_tolerance
        CHECK (price_tolerance_pct BETWEEN 0 AND 50),
    ADD CONSTRAINT chk_tp_anchor
        CHECK ((anchor_latitude IS NULL) = (anchor_longitude IS NULL));

-- ---------------------------------------------------------------------
-- 2. TIEN ICH UU TIEN
-- ---------------------------------------------------------------------
-- Dieu kien nhu "cho nuoi thu cung" hoac "chung chu" duoc tao thanh mot
-- dong trong bang amenities thay vi them cot vao rooms/tenant_profiles.
-- is_required = TRUE: phong thieu tien ich nay bi loai khi goi y.
-- is_required = FALSE: chi cong diem neu co.

CREATE TABLE tenant_profile_amenities (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    tenant_profile_id VARCHAR(36) NOT NULL,
    amenity_id VARCHAR(36) NOT NULL,

    is_required BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uk_tpa UNIQUE (tenant_profile_id, amenity_id),

    CONSTRAINT fk_tpa_profile
        FOREIGN KEY (tenant_profile_id)
        REFERENCES tenant_profiles(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_tpa_amenity
        FOREIGN KEY (amenity_id)
        REFERENCES amenities(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 3. NHIEU PHUONG MONG MUON (TUY CHON)
-- ---------------------------------------------------------------------
-- desired_ward_id giu nguyen lam "phuong chinh". Bang nay luu them cac
-- phuong khong ma tenant mong muon.

CREATE TABLE tenant_profile_wards (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    tenant_profile_id VARCHAR(36) NOT NULL,
    ward_id VARCHAR(36) NOT NULL,

    CONSTRAINT uk_tpw UNIQUE (tenant_profile_id, ward_id),

    CONSTRAINT fk_tpw_profile
        FOREIGN KEY (tenant_profile_id)
        REFERENCES tenant_profiles(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_tpw_ward
        FOREIGN KEY (ward_id)
        REFERENCES wards(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 4. LICH SU TIM KIEM
-- ---------------------------------------------------------------------
-- Spring ghi bang nay moi lan tenant tim kiem. Khi chatbot tim phong, AI
-- service KHONG ghi vao day (khong co quyen) ma ghi vao ai_messages.filters_json
-- trong schema rental_ai.

CREATE TABLE listing_search_history (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

    user_id VARCHAR(36) NOT NULL,

    source ENUM('SEARCH_PAGE','MAP','CHATBOT') NOT NULL DEFAULT 'SEARCH_PAGE',

    keyword VARCHAR(255),
    province_id VARCHAR(36),
    ward_id VARCHAR(36),
    price_min DECIMAL(12, 2),
    price_max DECIMAL(12, 2),
    area_min DECIMAL(8, 2),
    area_max DECIMAL(8, 2),
    room_type_id VARCHAR(36),

    result_count INT,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_lsh_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    INDEX IX_lsh_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
