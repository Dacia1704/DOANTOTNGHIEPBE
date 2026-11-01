-- Fail before altering production tables if legacy agreements conflict. No historical data is rewritten.
CREATE TEMPORARY TABLE rental_legacy_guard (guard_key VARCHAR(100) PRIMARY KEY);
INSERT INTO rental_legacy_guard
SELECT CONCAT('room:',room_id) FROM rental_agreements
WHERE status IN ('DRAFT','PENDING_CONFIRMATION','ACTIVE');
TRUNCATE TABLE rental_legacy_guard;
INSERT INTO rental_legacy_guard
SELECT CONCAT('request:',rental_request_id) FROM rental_agreements WHERE rental_request_id IS NOT NULL;
DROP TEMPORARY TABLE rental_legacy_guard;

ALTER TABLE rental_requests
  ADD room_id VARCHAR(36) NULL,
  ADD tenant_full_name VARCHAR(100) NULL,
  ADD tenant_phone VARCHAR(20) NULL,
  ADD tenant_email VARCHAR(255) NULL,
  ADD tenant_citizen_id VARCHAR(20) NULL,
  ADD tenant_permanent_address VARCHAR(500) NULL,
  ADD occupant_count INT NULL,
  ADD duration_months INT NULL,
  ADD resolution_reason VARCHAR(500) NULL,
  ADD open_request_key VARCHAR(73) GENERATED ALWAYS AS
    (CASE WHEN status='PENDING' AND room_id IS NOT NULL THEN CONCAT(tenant_id,':',room_id) ELSE NULL END) VIRTUAL,
  ADD CONSTRAINT fk_rr_room FOREIGN KEY(room_id) REFERENCES rooms(id) ON DELETE RESTRICT,
  ADD UNIQUE KEY uk_rr_open_room_tenant(open_request_key),
  ADD INDEX idx_rr_room_status_created(room_id,status,created_at),
  ADD INDEX idx_rr_expiry(status,expires_at),
  ADD CONSTRAINT chk_rr_duration CHECK(duration_months IS NULL OR duration_months BETWEEN 1 AND 36),
  ADD CONSTRAINT chk_rr_occupants CHECK(occupant_count IS NULL OR occupant_count > 0);

ALTER TABLE rental_agreements
  ADD confirmation_expires_at DATETIME NULL,
  ADD pdf_status ENUM('PENDING','PROCESSING','READY','FAILED') NOT NULL DEFAULT 'PENDING',
  ADD pdf_error VARCHAR(500) NULL,
  ADD pdf_generated_at DATETIME NULL,
  ADD pdf_sha256 CHAR(64) NULL,
  ADD contract_snapshot JSON NULL,
  ADD pdf_attempt_count INT NOT NULL DEFAULT 0,
  ADD pdf_next_retry_at DATETIME NULL,
  ADD pdf_lease_until DATETIME NULL,
  ADD pdf_claim_token VARCHAR(36) NULL,
  ADD cancel_reason VARCHAR(500) NULL,
  ADD occupied_room_id VARCHAR(36) GENERATED ALWAYS AS
    (CASE WHEN status IN ('DRAFT','PENDING_CONFIRMATION','ACTIVE') THEN room_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_rag_occupied_room(occupied_room_id),
  ADD UNIQUE KEY uk_rag_request(rental_request_id),
  ADD INDEX idx_rag_confirmation(status,confirmation_expires_at),
  ADD INDEX idx_rag_room_status(room_id,status),
  ADD INDEX idx_rag_pdf_job(pdf_status,pdf_next_retry_at,pdf_lease_until);

-- Durable audit + notification outbox: never contains request bodies, identity documents or tokens.
CREATE TABLE rental_events (
  id VARCHAR(36) PRIMARY KEY,
  actor_id VARCHAR(36) NULL,
  recipient_id VARCHAR(36) NOT NULL,
  reference_id VARCHAR(36) NOT NULL,
  reference_type VARCHAR(50) NOT NULL,
  transition VARCHAR(80) NOT NULL,
  pdf_sha256 CHAR(64) NULL,
  created_at DATETIME NOT NULL,
  delivered_at DATETIME NULL,
  INDEX idx_rental_event_delivery(delivered_at,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Upload intent is durable before HTTP starts, allowing orphan cleanup after a process crash.
CREATE TABLE rental_pdf_assets (
  public_id VARCHAR(255) PRIMARY KEY,
  agreement_id VARCHAR(36) NOT NULL,
  created_at DATETIME NOT NULL,
  last_cleanup_at DATETIME NULL,
  INDEX idx_rental_pdf_asset_created(created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
