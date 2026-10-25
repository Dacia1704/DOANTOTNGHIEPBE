-- Keep legacy paid/pending orders and immutable period snapshots intact.
ALTER TABLE service_subscription_orders
    ADD immediate_upgrade BOOLEAN NOT NULL DEFAULT FALSE,
    ADD upgrade_value DECIMAL(12,2) NULL,
    DROP CHECK chk_sso_scheduled,
    DROP CHECK chk_sso_applied,
    ADD CONSTRAINT chk_sso_scheduled CHECK (
        status <> 'SCHEDULED' OR (type = 'CHANGE' AND subscription_id IS NOT NULL
        AND effective_at IS NOT NULL AND end_at IS NOT NULL)
    ),
    ADD CONSTRAINT chk_sso_applied CHECK (
        status <> 'APPLIED' OR (subscription_id IS NOT NULL AND applied_at IS NOT NULL
        AND effective_at IS NOT NULL AND end_at IS NOT NULL)
    ),
    ADD CONSTRAINT chk_sso_upgrade CHECK (
        immediate_upgrade = FALSE OR (type = 'CHANGE' AND previous_subscription_id IS NOT NULL
        AND previous_end_at IS NOT NULL AND upgrade_value IS NOT NULL AND upgrade_value >= 0)
    );

ALTER TABLE service_subscription_periods
    ADD entitlement_value DECIMAL(12,2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_ssp_value CHECK (entitlement_value >= 0);

UPDATE service_subscription_periods p
JOIN service_subscription_orders o ON o.id = p.order_id
SET p.entitlement_value = o.price;
