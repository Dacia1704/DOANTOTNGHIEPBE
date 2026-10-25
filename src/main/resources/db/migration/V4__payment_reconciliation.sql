-- Keep each successful collection even when its entitlement cannot be applied.
ALTER TABLE payments
    ADD requires_review BOOLEAN NOT NULL DEFAULT FALSE,
    ADD review_reason VARCHAR(255),
    ADD INDEX ix_payment_review_created (requires_review, created_at);

ALTER TABLE boost_service_package_subscriptions
    ADD payment_expires_at DATETIME,
    ADD INDEX ix_boost_payment_expiry (status, payment_expires_at);

UPDATE boost_service_package_subscriptions SET payment_expires_at = created_at + INTERVAL 15 MINUTE;
ALTER TABLE boost_service_package_subscriptions
    MODIFY payment_expires_at DATETIME NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL 15 MINUTE);
