-- Keep the first-review decision on the existing listing; no new table.
ALTER TABLE listings ADD COLUMN initial_review boolean NOT NULL DEFAULT FALSE;

-- Only an unpublished post still on its original submission can be refunded.
-- Older rows do not retain every transition; a later submission is not a first review.
UPDATE listings
SET initial_review = TRUE
WHERE status = 'PENDING' AND published_at IS NULL
  AND submitted_at BETWEEN created_at - INTERVAL 1 SECOND AND created_at + INTERVAL 1 SECOND;
