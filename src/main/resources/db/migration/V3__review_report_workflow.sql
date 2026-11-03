-- Preserve existing review rows while making visibility deterministic.
UPDATE room_reviews SET is_hidden=0 WHERE is_hidden IS NULL;
UPDATE landlord_reviews SET is_hidden=0 WHERE is_hidden IS NULL;
UPDATE tenant_reviews SET is_hidden=0 WHERE is_hidden IS NULL;

ALTER TABLE room_reviews
    MODIFY is_hidden TINYINT(1) NOT NULL DEFAULT 0,
    ADD hidden_at DATETIME NULL,
    ADD hidden_reason VARCHAR(500) NULL,
    ADD INDEX ix_room_review_visible (room_id, is_hidden, created_at, id);
ALTER TABLE landlord_reviews
    MODIFY is_hidden TINYINT(1) NOT NULL DEFAULT 0,
    ADD hidden_at DATETIME NULL,
    ADD hidden_reason VARCHAR(500) NULL,
    ADD INDEX ix_landlord_review_visible (landlord_id, is_hidden, created_at, id);
ALTER TABLE tenant_reviews
    MODIFY is_hidden TINYINT(1) NOT NULL DEFAULT 0,
    ADD hidden_at DATETIME NULL,
    ADD hidden_reason VARCHAR(500) NULL,
    ADD INDEX ix_tenant_review_visible (tenant_id, is_hidden, created_at, id);

-- Invalid legacy rows intentionally fail this constraint; never delete complaint history.
ALTER TABLE reports
    ADD CONSTRAINT chk_report_target CHECK (listing_id IS NOT NULL OR landlord_id IS NOT NULL),
    ADD INDEX ix_report_status_created (status, created_at, id),
    ADD INDEX ix_report_tenant_created (tenant_id, created_at, id),
    ADD INDEX ix_report_tenant_listing_status (tenant_id, listing_id, status),
    ADD INDEX ix_report_tenant_landlord_status (tenant_id, landlord_id, status);

-- Completed rentals/reviews must survive attempts to delete a physical room or agreement.
ALTER TABLE room_reviews DROP FOREIGN KEY fk_rrev_agreement,
    ADD CONSTRAINT fk_rrev_agreement_history FOREIGN KEY (rental_agreement_id) REFERENCES rental_agreements(id) ON DELETE RESTRICT;
ALTER TABLE landlord_reviews DROP FOREIGN KEY fk_lrev_agreement,
    ADD CONSTRAINT fk_lrev_agreement_history FOREIGN KEY (rental_agreement_id) REFERENCES rental_agreements(id) ON DELETE RESTRICT;
ALTER TABLE tenant_reviews DROP FOREIGN KEY fk_trev_agreement,
    ADD CONSTRAINT fk_trev_agreement_history FOREIGN KEY (rental_agreement_id) REFERENCES rental_agreements(id) ON DELETE RESTRICT;
ALTER TABLE room_reviews DROP FOREIGN KEY fk_rrev_room,
    ADD CONSTRAINT fk_rrev_room_history FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE RESTRICT;
ALTER TABLE reports DROP FOREIGN KEY fk_rep_listing,
    ADD CONSTRAINT fk_rep_listing_history FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE RESTRICT;
