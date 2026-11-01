-- Read-only report. Resolve business conflicts manually before retrying V3; never delete history automatically.
SELECT room_id,COUNT(*) AS open_agreements,GROUP_CONCAT(id) AS agreement_ids
FROM rental_agreements WHERE status IN ('DRAFT','PENDING_CONFIRMATION','ACTIVE')
GROUP BY room_id HAVING COUNT(*) > 1;
SELECT rental_request_id,COUNT(*) AS agreements,GROUP_CONCAT(id) AS agreement_ids
FROM rental_agreements WHERE rental_request_id IS NOT NULL
GROUP BY rental_request_id HAVING COUNT(*) > 1;
