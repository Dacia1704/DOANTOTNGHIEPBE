ALTER TABLE users ADD confirmed_report_count INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_confirmed_report_count CHECK (confirmed_report_count >= 0);
ALTER TABLE listings ADD priority_suppressed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE reports ADD warning_level INT NOT NULL DEFAULT 0,
    ADD sanction_action VARCHAR(40) NOT NULL DEFAULT 'NONE',
    ADD sanctioned_listing_id VARCHAR(36) NULL,
    ADD CONSTRAINT chk_report_warning_level CHECK (warning_level BETWEEN 0 AND 3);
-- Previous RESOLVED reports remain historical records; do not punish users retroactively.
