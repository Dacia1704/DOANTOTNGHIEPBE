-- Keep purchased limits in the existing service/Boost subscription snapshots.
-- Consumption is recorded on listings and boost_allocations; no new table is needed.
ALTER TABLE listings
    ADD COLUMN posting_quota_period_id varchar(36) NULL,
    ADD COLUMN posting_quota_consumed boolean NOT NULL DEFAULT FALSE,
    ADD COLUMN priority_quota_period_id varchar(36) NULL,
    ADD COLUMN consumed_priority_levels int NOT NULL DEFAULT 0,
    ADD KEY ix_listing_posting_quota (posting_quota_period_id, posting_quota_consumed),
    ADD KEY ix_listing_priority_quota (priority_quota_period_id, consumed_priority_levels),
    ADD CONSTRAINT fk_listing_posting_quota_period FOREIGN KEY (posting_quota_period_id)
        REFERENCES service_subscription_periods(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_listing_consumed_priority_period FOREIGN KEY (priority_quota_period_id)
        REFERENCES service_subscription_periods(id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_listing_consumed_priority CHECK (
        (consumed_priority_levels = 0 AND priority_quota_period_id IS NULL)
        OR (consumed_priority_levels BETWEEN 1 AND 3 AND priority_quota_period_id IS NOT NULL));

-- Reconstruct the latest known submission, including hidden and expired posts.
UPDATE listings l JOIN properties property ON property.id = l.property_id
SET l.posting_quota_consumed = TRUE,
    l.posting_quota_period_id = COALESCE(l.service_subscription_period_id, (
        SELECT p.id FROM service_subscription_periods p
        JOIN service_package_subscriptions s ON s.id = p.subscription_id
        WHERE s.landlord_id = property.landlord_id AND p.start_at <= COALESCE(l.submitted_at, l.created_at)
            AND p.end_at > COALESCE(l.submitted_at, l.created_at)
        ORDER BY p.start_at DESC, p.id LIMIT 1
    ))
WHERE l.status <> 'REJECTED' OR l.published_at IS NOT NULL;

UPDATE listings
SET priority_quota_period_id = service_subscription_period_id,
    consumed_priority_levels = package_priority_level
WHERE package_priority_level IN (1,2) AND service_subscription_period_id IS NOT NULL;

-- Removing Boost changes only its active selection, keeping the used credit on the same table.
ALTER TABLE boost_allocations
    ADD COLUMN active boolean NOT NULL DEFAULT TRUE,
    ADD UNIQUE KEY uq_boost_allocation_consumption (listing_id, boost_subscription_id, priority_level);

-- Install the replacement FK-supporting index before removing the old unique index.
ALTER TABLE boost_allocations DROP INDEX uq_boost_allocation_listing;
ALTER TABLE boost_allocations
    ADD COLUMN active_listing_id varchar(36) GENERATED ALWAYS AS (CASE WHEN active THEN listing_id ELSE NULL END) VIRTUAL,
    ADD UNIQUE KEY uq_boost_allocation_active_listing (active_listing_id);
