-- V7 is already deployed. Add calendar billing metadata in a new migration.
ALTER TABLE service_subscription_orders
    ADD upgrade_months DECIMAL(20,12) NULL;

ALTER TABLE service_subscription_periods
    ADD billing_months DECIMAL(20,12) NULL;

-- Reconstruct even chains of upgrades from the original purchased periods.
-- Each source period contributes only its unused fraction at the upgrade quote.
CREATE TEMPORARY TABLE upgrade_period_months AS
WITH RECURSIVE period_values AS (
    SELECT p.id, p.subscription_id, p.start_at, p.end_at,
        CAST(o.duration_months AS DECIMAL(20,12)) AS months
    FROM service_subscription_periods p
    JOIN service_subscription_orders o ON o.id = p.order_id
    WHERE o.immediate_upgrade = FALSE
    UNION ALL
    SELECT p.id, p.subscription_id, p.start_at, p.end_at,
        CAST(v.months * TIMESTAMPDIFF(SECOND, GREATEST(v.start_at, o.created_at), v.end_at)
            / TIMESTAMPDIFF(SECOND, v.start_at, v.end_at) AS DECIMAL(20,12))
    FROM period_values v
    JOIN service_subscription_orders o ON o.previous_subscription_id = v.subscription_id
        AND o.immediate_upgrade = TRUE AND o.created_at < v.end_at
    JOIN service_subscription_periods p ON p.order_id = o.id
)
SELECT id, SUM(months) AS months FROM period_values GROUP BY id;

UPDATE service_subscription_periods p
JOIN upgrade_period_months v ON v.id = p.id
SET p.billing_months = v.months;

DROP TEMPORARY TABLE upgrade_period_months;

-- Pending upgrade quotes also need their original remaining billing months.
UPDATE service_subscription_orders o
JOIN (
    SELECT q.id,
        SUM(p.billing_months * TIMESTAMPDIFF(SECOND, GREATEST(p.start_at, q.created_at), p.end_at)
            / TIMESTAMPDIFF(SECOND, p.start_at, p.end_at)) AS months
    FROM service_subscription_orders q
    JOIN service_subscription_periods p ON p.subscription_id = q.previous_subscription_id
        AND p.end_at > q.created_at
    WHERE q.immediate_upgrade = TRUE
    GROUP BY q.id
) v ON v.id = o.id
SET o.upgrade_months = v.months;

ALTER TABLE service_subscription_periods
    MODIFY billing_months DECIMAL(20,12) NOT NULL DEFAULT 1,
    DROP CHECK chk_ssp_value,
    ADD CONSTRAINT chk_ssp_value CHECK (entitlement_value >= 0 AND billing_months > 0);

ALTER TABLE service_subscription_orders
    DROP CHECK chk_sso_upgrade,
    ADD CONSTRAINT chk_sso_upgrade CHECK (
        immediate_upgrade = FALSE OR (type = 'CHANGE' AND previous_subscription_id IS NOT NULL
        AND previous_end_at IS NOT NULL AND upgrade_value IS NOT NULL AND upgrade_value >= 0
        AND upgrade_months IS NOT NULL AND upgrade_months > 0)
    );
