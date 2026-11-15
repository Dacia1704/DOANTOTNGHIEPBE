-- Separate report context from listing_id, which identifies LISTING reports.
ALTER TABLE reports ADD origin_listing_id VARCHAR(36) NULL,
    ADD CONSTRAINT fk_report_origin_listing FOREIGN KEY (origin_listing_id) REFERENCES listings(id) ON DELETE RESTRICT;
UPDATE reports SET origin_listing_id=listing_id WHERE listing_id IS NOT NULL;
-- LANDLORD reports without recorded context cannot be backfilled reliably.
