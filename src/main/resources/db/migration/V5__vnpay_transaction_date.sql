-- QueryDr needs the original vnp_CreateDate, including when checkout is reopened.
ALTER TABLE payments ADD vnp_create_date DATETIME;
-- For older attempts this is the best available first-checkout timestamp.
UPDATE payments SET vnp_create_date = created_at WHERE payment_method = 'VNPAY';
