-- Legacy transactions did not capture the purchased rights or calendar-month terms.
-- Refuse to invent those snapshots or reinterpret payments. Reconcile them explicitly first.
-- This guard runs before persistent schema/data changes and survives no connection.
CREATE TEMPORARY TABLE v3_requires_legacy_financial_reconciliation (id INT PRIMARY KEY);
INSERT INTO v3_requires_legacy_financial_reconciliation VALUES (1);
INSERT INTO v3_requires_legacy_financial_reconciliation
SELECT 1 WHERE EXISTS (SELECT 1 FROM service_package_subscriptions)
    OR EXISTS (SELECT 1 FROM boost_service_package_subscriptions)
    OR EXISTS (SELECT 1 FROM payments)
    OR EXISTS (SELECT 1 FROM invoices);
DROP TEMPORARY TABLE v3_requires_legacy_financial_reconciliation;

CREATE TEMPORARY TABLE v3_requires_legacy_catalog_or_listing_review (id INT PRIMARY KEY);
INSERT INTO v3_requires_legacy_catalog_or_listing_review VALUES (1);
INSERT INTO v3_requires_legacy_catalog_or_listing_review
SELECT 1 WHERE EXISTS (
    SELECT 1 FROM service_packages
    WHERE (max_listings IS NOT NULL AND max_listings < 0)
       OR high_priority_quota IS NULL OR high_priority_quota < 0
       OR low_priority_quota IS NULL OR low_priority_quota < 0
       OR has_stats IS NULL OR has_stats NOT IN (0, 1)
       OR is_active IS NULL OR is_active NOT IN (0, 1)
       OR price < 0
) OR EXISTS (SELECT 1 FROM boost_service_packages WHERE price < 0)
  OR EXISTS (
    SELECT 1 FROM listings WHERE status = 'APPROVED'
      AND (approved_at IS NULL OR (expires_at IS NOT NULL AND expires_at <= approved_at))
);
DROP TEMPORARY TABLE v3_requires_legacy_catalog_or_listing_review;

-- Retain old catalogue amounts/day terms for audit, without guessing months or selling them.
ALTER TABLE service_packages
    RENAME COLUMN high_priority_quota TO high_priority_slots,
    RENAME COLUMN low_priority_quota TO low_priority_slots,
    RENAME COLUMN price TO legacy_price,
    RENAME COLUMN billing_cycle_days TO legacy_billing_cycle_days;
ALTER TABLE service_packages
    MODIFY high_priority_slots INT NOT NULL DEFAULT 0,
    MODIFY low_priority_slots INT NOT NULL DEFAULT 0,
    MODIFY has_stats BOOLEAN NOT NULL DEFAULT FALSE,
    MODIFY is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD default_priority_level INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_sp_limits CHECK (
        (max_listings IS NULL OR max_listings >= 0)
        AND high_priority_slots >= 0 AND low_priority_slots >= 0
        AND default_priority_level IN (0, 1)
    );

-- New catalogue rows do not have legacy values.
ALTER TABLE service_packages
    MODIFY legacy_price DECIMAL(12,2) NULL,
    MODIFY legacy_billing_cycle_days INT NULL;

INSERT INTO service_packages
    (id, code, name, max_listings, high_priority_slots, low_priority_slots,
     default_priority_level, has_stats, is_active)
SELECT UUID(), 'FREE', 'Free', 3, 0, 0, 0, FALSE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM service_packages WHERE code = 'FREE');

CREATE TABLE service_package_prices (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    package_id VARCHAR(36) NOT NULL,
    duration_months INT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_spp_package FOREIGN KEY (package_id) REFERENCES service_packages(id) ON DELETE RESTRICT,
    CONSTRAINT uq_spp_term UNIQUE (package_id, duration_months),
    CONSTRAINT chk_spp_term CHECK (duration_months IN (1,3,6,12)),
    CONSTRAINT chk_spp_price CHECK (price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Stored generated uniqueness keys cannot use cascading foreign-key base columns.
-- Financial history must not disappear when a user or listing is deleted.
ALTER TABLE service_package_subscriptions
    DROP FOREIGN KEY fk_sps_landlord,
    ADD CONSTRAINT fk_sps_landlord_history FOREIGN KEY (landlord_id) REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE service_package_subscriptions
    DROP COLUMN active_flag,
    MODIFY status ENUM('SCHEDULED','ACTIVE','EXPIRED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    ADD active_landlord_id VARCHAR(36)
        GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN landlord_id ELSE NULL END) STORED,
    ADD scheduled_landlord_id VARCHAR(36)
        GENERATED ALWAYS AS (CASE WHEN status = 'SCHEDULED' THEN landlord_id ELSE NULL END) STORED,
    ADD CONSTRAINT uq_sps_active_landlord UNIQUE (active_landlord_id),
    ADD CONSTRAINT uq_sps_scheduled_landlord UNIQUE (scheduled_landlord_id),
    ADD CONSTRAINT chk_sps_dates CHECK (end_at > start_at),
    ADD INDEX ix_sps_landlord_status_end (landlord_id, status, end_at),
    ADD INDEX ix_sps_status_start (status, start_at),
    ADD INDEX ix_sps_status_end (status, end_at);

CREATE TABLE service_subscription_orders (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    landlord_id VARCHAR(36) NOT NULL,
    package_id VARCHAR(36) NOT NULL,
    service_package_price_id VARCHAR(36) NOT NULL,
    subscription_id VARCHAR(36),
    previous_subscription_id VARCHAR(36),
    type ENUM('PURCHASE','RENEW','CHANGE') NOT NULL,
    status ENUM('PENDING_PAYMENT','AWAITING_ADMIN_CONFIRMATION','SCHEDULED',
                'APPLIED','CANCELLED','EXPIRED','REQUIRES_REVIEW') NOT NULL DEFAULT 'PENDING_PAYMENT',
    duration_months INT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    package_code_snapshot VARCHAR(50) NOT NULL,
    package_name_snapshot VARCHAR(100) NOT NULL,
    max_listings_snapshot INT,
    high_priority_slots_snapshot INT NOT NULL,
    low_priority_slots_snapshot INT NOT NULL,
    default_priority_level_snapshot INT NOT NULL,
    has_stats_snapshot BOOLEAN NOT NULL,
    expires_at DATETIME NOT NULL,
    effective_at DATETIME,
    end_at DATETIME,
    previous_end_at DATETIME,
    applied_at DATETIME,
    confirmed_by VARCHAR(36),
    confirmed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    open_change_landlord_id VARCHAR(36) GENERATED ALWAYS AS (
        CASE WHEN type = 'CHANGE' AND status IN
          ('PENDING_PAYMENT','AWAITING_ADMIN_CONFIRMATION','SCHEDULED')
        THEN landlord_id ELSE NULL END
    ) STORED,
    CONSTRAINT fk_sso_landlord FOREIGN KEY (landlord_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sso_package FOREIGN KEY (package_id) REFERENCES service_packages(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sso_price FOREIGN KEY (service_package_price_id) REFERENCES service_package_prices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sso_subscription FOREIGN KEY (subscription_id) REFERENCES service_package_subscriptions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sso_previous FOREIGN KEY (previous_subscription_id) REFERENCES service_package_subscriptions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sso_confirmer FOREIGN KEY (confirmed_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uq_sso_open_change UNIQUE (open_change_landlord_id),
    CONSTRAINT chk_sso_term CHECK (duration_months IN (1,3,6,12)),
    CONSTRAINT chk_sso_price CHECK (price >= 0),
    CONSTRAINT chk_sso_rights CHECK (
        (max_listings_snapshot IS NULL OR max_listings_snapshot >= 0)
        AND high_priority_slots_snapshot >= 0 AND low_priority_slots_snapshot >= 0
        AND default_priority_level_snapshot IN (0,1)
        AND has_stats_snapshot IN (0,1)
    ),
    CONSTRAINT chk_sso_dates CHECK (
        (effective_at IS NULL AND end_at IS NULL)
        OR (effective_at IS NOT NULL AND end_at IS NOT NULL AND end_at > effective_at)
    ),
    CONSTRAINT chk_sso_confirmation CHECK (
        (confirmed_by IS NULL AND confirmed_at IS NULL)
        OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)
    ),
    CONSTRAINT chk_sso_change CHECK (
        type <> 'CHANGE' OR previous_subscription_id IS NOT NULL
    ),
    CONSTRAINT chk_sso_scheduled CHECK (
        status <> 'SCHEDULED'
        OR (type = 'CHANGE' AND subscription_id IS NOT NULL
            AND confirmed_at IS NOT NULL AND effective_at IS NOT NULL AND end_at IS NOT NULL)
    ),
    CONSTRAINT chk_sso_awaiting_admin CHECK (
        status <> 'AWAITING_ADMIN_CONFIRMATION' OR type = 'CHANGE'
    ),
    CONSTRAINT chk_sso_applied CHECK (
        status <> 'APPLIED'
        OR (subscription_id IS NOT NULL AND applied_at IS NOT NULL
            AND effective_at IS NOT NULL AND end_at IS NOT NULL
            AND (type <> 'CHANGE' OR confirmed_at IS NOT NULL))
    ),
    INDEX ix_sso_landlord_created (landlord_id, created_at),
    INDEX ix_sso_status_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE service_subscription_periods (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    subscription_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    period_number INT NOT NULL,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    package_code_snapshot VARCHAR(50) NOT NULL,
    package_name_snapshot VARCHAR(100) NOT NULL,
    max_listings_snapshot INT,
    high_priority_slots_snapshot INT NOT NULL,
    low_priority_slots_snapshot INT NOT NULL,
    default_priority_level_snapshot INT NOT NULL,
    has_stats_snapshot BOOLEAN NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ssp_subscription FOREIGN KEY (subscription_id) REFERENCES service_package_subscriptions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ssp_order FOREIGN KEY (order_id) REFERENCES service_subscription_orders(id) ON DELETE RESTRICT,
    CONSTRAINT uq_ssp_order UNIQUE (order_id),
    CONSTRAINT uq_ssp_number UNIQUE (subscription_id, period_number),
    CONSTRAINT chk_ssp_dates CHECK (end_at > start_at AND period_number > 0),
    CONSTRAINT chk_ssp_rights CHECK (
        (max_listings_snapshot IS NULL OR max_listings_snapshot >= 0)
        AND high_priority_slots_snapshot >= 0 AND low_priority_slots_snapshot >= 0
        AND default_priority_level_snapshot IN (0,1)
        AND has_stats_snapshot IN (0,1)
    ),
    INDEX ix_ssp_subscription_dates (subscription_id, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE boost_service_packages
    RENAME COLUMN duration_days TO legacy_duration_days,
    RENAME COLUMN priority_level TO legacy_priority_level;
ALTER TABLE boost_service_packages
    ADD duration_months INT NULL,
    ADD priority_level INT NOT NULL DEFAULT 2;

-- These old offers were priced in days, so an admin must set their month term before selling.
UPDATE boost_service_packages SET is_active = FALSE;
ALTER TABLE boost_service_packages
    MODIFY legacy_duration_days INT NULL,
    MODIFY legacy_priority_level INT NULL,
    MODIFY is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT chk_bsp_level CHECK (priority_level = 2),
    ADD CONSTRAINT chk_bsp_price CHECK (price >= 0),
    ADD CONSTRAINT chk_bsp_months CHECK (duration_months IS NULL OR duration_months IN (1,3,6)),
    ADD CONSTRAINT chk_bsp_active_term CHECK (
        is_active = FALSE OR (duration_months IS NOT NULL AND duration_months IN (1,3,6))
    );

ALTER TABLE boost_service_package_subscriptions
    DROP FOREIGN KEY fk_bsps_landlord,
    DROP FOREIGN KEY fk_bsps_listing,
    ADD CONSTRAINT fk_bsps_landlord_history FOREIGN KEY (landlord_id) REFERENCES users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_bsps_listing_history FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE RESTRICT;
ALTER TABLE boost_service_package_subscriptions
    MODIFY start_at DATETIME NULL,
    MODIFY end_at DATETIME NULL,
    MODIFY status ENUM('PENDING_PAYMENT','ACTIVE','EXPIRED','CANCELLED') NOT NULL DEFAULT 'PENDING_PAYMENT',
    ADD package_name_snapshot VARCHAR(100) NOT NULL,
    ADD priority_level_snapshot INT NOT NULL,
    ADD duration_months_snapshot INT NOT NULL,
    ADD active_listing_id VARCHAR(36)
        GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN listing_id ELSE NULL END) STORED,
    ADD CONSTRAINT uq_bsps_active_listing UNIQUE (active_listing_id),
    ADD CONSTRAINT chk_bsps_snapshot CHECK (
        priority_level_snapshot = 2 AND duration_months_snapshot IN (1,3,6) AND price >= 0
    ),
    ADD CONSTRAINT chk_bsps_dates CHECK (
        (start_at IS NULL AND end_at IS NULL)
        OR (start_at IS NOT NULL AND end_at IS NOT NULL AND end_at > start_at)
    ),
    ADD CONSTRAINT chk_bsps_active_dates CHECK (
        status NOT IN ('ACTIVE','EXPIRED') OR (start_at IS NOT NULL AND end_at IS NOT NULL)
    ),
    ADD CONSTRAINT chk_bsps_pending_dates CHECK (
        status <> 'PENDING_PAYMENT' OR (start_at IS NULL AND end_at IS NULL)
    ),
    ADD INDEX ix_bsps_listing_status_end (listing_id, status, end_at),
    ADD INDEX ix_bsps_landlord_created (landlord_id, created_at),
    ADD INDEX ix_bsps_status_end (status, end_at);

ALTER TABLE payments
    DROP FOREIGN KEY fk_payments_landlord,
    ADD CONSTRAINT fk_payments_landlord_history FOREIGN KEY (landlord_id) REFERENCES users(id) ON DELETE RESTRICT,
    DROP COLUMN reference_id,
    MODIFY status ENUM('PENDING','SUCCESS','FAILED','EXPIRED','REFUNDED') NOT NULL DEFAULT 'PENDING',
    ADD service_subscription_order_id VARCHAR(36),
    ADD boost_subscription_id VARCHAR(36),
    ADD merchant_order_id VARCHAR(100) NOT NULL,
    ADD expires_at DATETIME NOT NULL,
    ADD CONSTRAINT fk_payment_order FOREIGN KEY (service_subscription_order_id) REFERENCES service_subscription_orders(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_payment_boost FOREIGN KEY (boost_subscription_id) REFERENCES boost_service_package_subscriptions(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_payment_merchant UNIQUE (merchant_order_id),
    ADD CONSTRAINT uq_payment_provider UNIQUE (payment_method, provider_txn_id),
    ADD CONSTRAINT chk_payment_target CHECK (
        (type = 'SERVICE_PACKAGE' AND service_subscription_order_id IS NOT NULL AND boost_subscription_id IS NULL)
        OR (type = 'BOOST_PACKAGE' AND boost_subscription_id IS NOT NULL AND service_subscription_order_id IS NULL)
    ),
    ADD CONSTRAINT chk_payment_amount CHECK (amount >= 0),
    ADD CONSTRAINT chk_payment_paid CHECK (status <> 'SUCCESS' OR paid_at IS NOT NULL),
    ADD INDEX ix_payment_landlord_created (landlord_id, created_at),
    ADD INDEX ix_payment_status_expiry (status, expires_at);
ALTER TABLE invoices
    DROP FOREIGN KEY fk_invoices_payment,
    ADD CONSTRAINT fk_invoices_payment_history FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_invoice_payment UNIQUE (payment_id);

-- Expand before translating values, then remove the old enum value.
ALTER TABLE listings
    MODIFY status ENUM('PENDING','APPROVED','PUBLISHED','REJECTED','HIDDEN','EXPIRED') NOT NULL DEFAULT 'PENDING',
    ADD submitted_at DATETIME,
    ADD pending_expires_at DATETIME,
    ADD published_at DATETIME,
    ADD service_subscription_period_id VARCHAR(36),
    ADD package_priority_level INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_listing_priority_period FOREIGN KEY (service_subscription_period_id)
        REFERENCES service_subscription_periods(id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_listing_package_priority CHECK (
        (package_priority_level = 0 AND service_subscription_period_id IS NULL)
        OR (package_priority_level IN (1,2) AND service_subscription_period_id IS NOT NULL)
    );
-- The old schema has no submission history: created_at is the documented initial-submission fallback.
UPDATE listings SET submitted_at = created_at, pending_expires_at = DATE_ADD(created_at, INTERVAL 7 DAY)
WHERE status = 'PENDING';
UPDATE listings SET status = 'PUBLISHED', published_at = approved_at,
    expires_at = COALESCE(expires_at, DATE_ADD(approved_at, INTERVAL 1 MONTH))
WHERE status = 'APPROVED';
ALTER TABLE listings
    MODIFY status ENUM('PENDING','PUBLISHED','REJECTED','HIDDEN','EXPIRED') NOT NULL DEFAULT 'PENDING',
    ADD CONSTRAINT chk_listing_pending CHECK (
        status <> 'PENDING' OR
        (submitted_at IS NOT NULL AND pending_expires_at IS NOT NULL AND pending_expires_at > submitted_at)
    ),
    ADD CONSTRAINT chk_listing_published CHECK (
        status <> 'PUBLISHED' OR
        (published_at IS NOT NULL AND expires_at IS NOT NULL AND expires_at > published_at)
    ),
    ADD INDEX ix_listing_room_status_pending (room_id, status, pending_expires_at),
    ADD INDEX ix_listing_room_status_expiry (room_id, status, expires_at),
    ADD INDEX ix_listing_status_published (status, published_at),
    ADD INDEX ix_listing_period_priority (service_subscription_period_id, package_priority_level, status, expires_at);
