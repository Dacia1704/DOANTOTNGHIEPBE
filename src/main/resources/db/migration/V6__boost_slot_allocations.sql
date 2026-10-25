-- Existing offers and purchased Boosts retain one high priority slot and their original term/price.
ALTER TABLE boost_service_packages
    DROP CHECK chk_bsp_level,
    DROP COLUMN priority_level,
    ADD high_priority_slots INT NOT NULL DEFAULT 1,
    ADD low_priority_slots INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_bsp_slots CHECK (
        high_priority_slots >= 0 AND low_priority_slots >= 0
        AND (high_priority_slots > 0 OR low_priority_slots > 0)
    );

ALTER TABLE boost_service_package_subscriptions
    DROP CHECK chk_bsps_snapshot,
    ADD high_priority_slots_snapshot INT NOT NULL DEFAULT 1,
    ADD low_priority_slots_snapshot INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_bsps_slots CHECK (
        high_priority_slots_snapshot >= 0 AND low_priority_slots_snapshot >= 0
        AND (high_priority_slots_snapshot > 0 OR low_priority_slots_snapshot > 0)
        AND duration_months_snapshot IN (1,3,6) AND price >= 0
    );

CREATE TABLE boost_allocations (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    boost_subscription_id VARCHAR(36) NOT NULL,
    listing_id VARCHAR(36) NOT NULL,
    priority_level INT NOT NULL,
    CONSTRAINT uq_boost_allocation_listing UNIQUE (listing_id),
    CONSTRAINT chk_boost_allocation_level CHECK (priority_level IN (1,2)),
    CONSTRAINT fk_boost_allocation_subscription FOREIGN KEY (boost_subscription_id)
        REFERENCES boost_service_package_subscriptions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_boost_allocation_listing FOREIGN KEY (listing_id)
        REFERENCES listings(id) ON DELETE CASCADE,
    INDEX ix_boost_allocation_subscription_level (boost_subscription_id, priority_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO boost_allocations (id, boost_subscription_id, listing_id, priority_level)
SELECT UUID(), b.id, b.listing_id, 2
FROM boost_service_package_subscriptions b JOIN listings l ON l.id = b.listing_id
WHERE b.status = 'ACTIVE' AND b.start_at <= CURRENT_TIMESTAMP AND b.end_at > CURRENT_TIMESTAMP
    AND l.status = 'PUBLISHED' AND l.expires_at > CURRENT_TIMESTAMP;

ALTER TABLE boost_service_package_subscriptions
    DROP FOREIGN KEY fk_bsps_listing_history,
    DROP INDEX uq_bsps_active_listing,
    DROP COLUMN active_listing_id,
    DROP INDEX ix_bsps_listing_status_end,
    DROP COLUMN listing_id,
    DROP COLUMN priority_level_snapshot;
