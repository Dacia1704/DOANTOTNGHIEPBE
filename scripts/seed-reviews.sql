-- MySQL 8, chạy thủ công trên DATABASE DEMO đã migrate V1-V3 trở lên.
-- Chọn database trước (USE ten_database), chạy toàn bộ file trong cùng connection.
-- Không đặt file này trong Flyway: đây là dữ liệu mẫu tùy chọn.
-- Phạm vi: tài khoản demo tenant@gmail.com / tenant02..tenant100@gmail.com,
-- landlord@gmail.com / landlord02..landlord24@gmail.com, đúng role và ACTIVE.
-- Tạo hợp đồng lịch sử giả lập EXPIRED; KHÔNG tạo PDF hoặc thay trạng thái phòng.
-- Xác nhận trong các hợp đồng này cũng là dữ liệu giả lập, không phải giao dịch thật.
-- Có thể chạy lại: không sửa/xóa hợp đồng, nội dung review hoặc moderation đã có.
-- Khi client báo lỗi SQL: ROLLBACK; không tiếp tục đến COMMIT.

SET NAMES utf8mb4;
SET @demo_review_now = CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+07:00');

DROP TEMPORARY TABLE IF EXISTS tmp_review_demo_tenants;
DROP TEMPORARY TABLE IF EXISTS tmp_review_demo_rooms;
DROP TEMPORARY TABLE IF EXISTS tmp_review_demo_plan;

CREATE TEMPORARY TABLE tmp_review_demo_tenants AS
SELECT u.id, ROW_NUMBER() OVER (ORDER BY u.email, u.id) AS seq
FROM users u
WHERE u.status='ACTIVE'
  AND (u.email='tenant@gmail.com' OR u.email REGEXP '^tenant(0[2-9]|[1-9][0-9]|100)@gmail[.]com$')
  AND EXISTS (SELECT 1 FROM user_roles ur JOIN roles role ON role.id=ur.role_id
              WHERE ur.user_id=u.id AND role.code='TENANT');

-- Một phòng cho mỗi landlord. Ưu tiên phòng thuộc cơ sở có tin công khai để xem demo trên FE.
CREATE TEMPORARY TABLE tmp_review_demo_rooms AS
SELECT id AS room_id, landlord_id, price, default_deposit, electricity_price,
       water_price, service_fee, ROW_NUMBER() OVER (ORDER BY landlord_id) AS seq
FROM (
    SELECT r.*, p.landlord_id,
           ROW_NUMBER() OVER (PARTITION BY p.landlord_id ORDER BY
               EXISTS(SELECT 1 FROM listings l WHERE l.property_id=p.id
                      AND l.status='PUBLISHED' AND l.expires_at>@demo_review_now) DESC, r.id) AS chosen
    FROM rooms r JOIN properties p ON p.id=r.property_id JOIN users u ON u.id=p.landlord_id
    WHERE u.status='ACTIVE'
      AND (u.email='landlord@gmail.com' OR u.email REGEXP '^landlord(0[2-9]|1[0-9]|2[0-4])@gmail[.]com$')
      AND EXISTS (SELECT 1 FROM user_roles ur JOIN roles role ON role.id=ur.role_id
                  WHERE ur.user_id=u.id AND role.code='LANDLORD')
) candidates WHERE chosen=1;

SET @demo_review_landlord_count=(SELECT COUNT(*) FROM tmp_review_demo_rooms);

CREATE TEMPORARY TABLE tmp_review_demo_plan AS
SELECT MD5(CONCAT('demo-review-agreement:',t.id)) AS agreement_id,
       t.id AS tenant_id, r.landlord_id, r.room_id, r.price, r.default_deposit,
       r.electricity_price, r.water_price, r.service_fee
FROM tmp_review_demo_tenants t JOIN tmp_review_demo_rooms r
  ON r.seq=1+MOD(t.seq-1,NULLIF(@demo_review_landlord_count,0))
WHERE t.id<>r.landlord_id;

START TRANSACTION;

INSERT INTO rental_agreements
    (id, agreement_code, room_id, tenant_id, landlord_id, price, deposit,
     electricity_price, water_price, service_fee, payment_cycle_months,
     start_date, end_date, terms_text, status, created_by_role,
     landlord_confirmed_at, tenant_confirmed_at, pdf_status, pdf_error, created_at, updated_at)
SELECT p.agreement_id, CONCAT('DEMO-REVIEW-',p.agreement_id), p.room_id, p.tenant_id, p.landlord_id,
       p.price, COALESCE(p.default_deposit,0), p.electricity_price, p.water_price, p.service_fee, 1,
       DATE_SUB(DATE(@demo_review_now),INTERVAL 8 MONTH), DATE_SUB(DATE(@demo_review_now),INTERVAL 2 MONTH),
       '[DEMO] Hợp đồng lịch sử giả lập dùng minh họa đánh giá; không có hợp đồng PDF hoặc giao dịch thật.',
       'EXPIRED', 'LANDLORD', DATE_SUB(@demo_review_now,INTERVAL 8 MONTH),
       DATE_SUB(@demo_review_now,INTERVAL 8 MONTH), 'FAILED', '[DEMO] Không tạo PDF cho hợp đồng giả lập.',
       DATE_SUB(@demo_review_now,INTERVAL 8 MONTH), DATE_SUB(@demo_review_now,INTERVAL 2 MONTH)
FROM tmp_review_demo_plan p
WHERE NOT EXISTS (SELECT 1 FROM rental_agreements a WHERE a.id=p.agreement_id
                  OR a.agreement_code=CONCAT('DEMO-REVIEW-',p.agreement_id));
SET @demo_review_inserted_agreements=ROW_COUNT();

-- Lấy lại các bên từ hợp đồng, chỉ dùng hợp đồng demo đủ điều kiện nghiệp vụ.
INSERT INTO landlord_reviews
    (id,rental_agreement_id,tenant_id,landlord_id,rating,comment,landlord_reply,is_hidden,created_at,updated_at)
SELECT UUID(),a.id,a.tenant_id,a.landlord_id,1+MOD(CRC32(CONCAT('landlord:',a.id)),5),
       CASE MOD(CRC32(CONCAT('landlord:',a.id)),5)
           WHEN 0 THEN '[DEMO] Việc xử lý yêu cầu sửa chữa còn chậm, cần cải thiện.'
           WHEN 1 THEN '[DEMO] Mong chủ trọ thông báo chi phí và hỗ trợ rõ ràng hơn.'
           WHEN 2 THEN '[DEMO] Chủ trọ hỗ trợ ở mức ổn, một số việc còn phải nhắc lại.'
           WHEN 3 THEN '[DEMO] Chủ trọ thân thiện, thông báo chi phí rõ ràng.'
           ELSE '[DEMO] Chủ trọ hỗ trợ nhanh, trao đổi minh bạch và đúng thỏa thuận.' END,
       IF(MOD(CRC32(a.id),2)=0,'[DEMO] Cảm ơn góp ý, chúng tôi đã ghi nhận để cải thiện dịch vụ.',NULL),
       0,DATE_ADD(a.end_date,INTERVAL 2 DAY),DATE_ADD(a.end_date,INTERVAL 3 DAY)
FROM rental_agreements a JOIN tmp_review_demo_plan p ON p.agreement_id=a.id
WHERE a.tenant_id=p.tenant_id AND a.landlord_id=p.landlord_id AND a.room_id=p.room_id
  AND a.status IN ('ACTIVE','EXPIRED','TERMINATED') AND a.start_date<=DATE(@demo_review_now)
  AND a.landlord_confirmed_at IS NOT NULL AND a.tenant_confirmed_at IS NOT NULL
  AND NOT EXISTS(SELECT 1 FROM landlord_reviews v WHERE v.rental_agreement_id=a.id);
SET @demo_review_inserted_landlords=ROW_COUNT();

INSERT INTO tenant_reviews
    (id,rental_agreement_id,landlord_id,tenant_id,rating,comment,is_hidden,created_at,updated_at)
SELECT UUID(),a.id,a.landlord_id,a.tenant_id,1+MOD(CRC32(CONCAT('tenant:',a.id)),5),
       CASE MOD(CRC32(CONCAT('tenant:',a.id)),5)
           WHEN 0 THEN '[DEMO] Cần cải thiện việc giữ gìn phòng và tuân thủ nội quy.'
           WHEN 1 THEN '[DEMO] Đôi lúc thanh toán chậm, cần chủ động trao đổi hơn.'
           WHEN 2 THEN '[DEMO] Sinh hoạt nhìn chung ổn, còn vài điểm cần nhắc nhở.'
           WHEN 3 THEN '[DEMO] Người thuê giữ phòng sạch, trao đổi lịch sự.'
           ELSE '[DEMO] Người thuê thanh toán đúng hạn, giữ phòng tốt và bàn giao đầy đủ.' END,
       0,DATE_ADD(a.end_date,INTERVAL 4 DAY),DATE_ADD(a.end_date,INTERVAL 4 DAY)
FROM rental_agreements a JOIN tmp_review_demo_plan p ON p.agreement_id=a.id
WHERE a.tenant_id=p.tenant_id AND a.landlord_id=p.landlord_id AND a.room_id=p.room_id
  AND a.status IN ('ACTIVE','EXPIRED','TERMINATED') AND a.start_date<=DATE(@demo_review_now)
  AND a.landlord_confirmed_at IS NOT NULL AND a.tenant_confirmed_at IS NOT NULL
  AND NOT EXISTS(SELECT 1 FROM tenant_reviews v WHERE v.rental_agreement_id=a.id);
SET @demo_review_inserted_tenants=ROW_COUNT();

INSERT INTO room_reviews
    (id,rental_agreement_id,tenant_id,room_id,rating,comment,landlord_reply,is_hidden,created_at,updated_at)
SELECT UUID(),a.id,a.tenant_id,a.room_id,1+MOD(CRC32(CONCAT('room:',a.id)),5),
       CASE MOD(CRC32(CONCAT('room:',a.id)),5)
           WHEN 0 THEN '[DEMO] Phòng cách âm kém, cần cải thiện bảo trì.'
           WHEN 1 THEN '[DEMO] Một số thiết bị chưa tốt, cần kiểm tra thường xuyên hơn.'
           WHEN 2 THEN '[DEMO] Phòng đáp ứng nhu cầu cơ bản, tiện nghi ở mức vừa phải.'
           WHEN 3 THEN '[DEMO] Phòng sạch, tiện ích đầy đủ, phù hợp giá thuê.'
           ELSE '[DEMO] Phòng thoáng, yên tĩnh và thuận tiện cho sinh hoạt.' END,
       IF(MOD(CRC32(a.id),3)=0,'[DEMO] Cảm ơn bạn đã chia sẻ trải nghiệm về phòng.',NULL),
       0,DATE_ADD(a.end_date,INTERVAL 1 DAY),DATE_ADD(a.end_date,INTERVAL 3 DAY)
FROM rental_agreements a JOIN tmp_review_demo_plan p ON p.agreement_id=a.id
WHERE a.tenant_id=p.tenant_id AND a.landlord_id=p.landlord_id AND a.room_id=p.room_id
  AND a.status IN ('ACTIVE','EXPIRED','TERMINATED') AND a.start_date<=DATE(@demo_review_now)
  AND a.landlord_confirmed_at IS NOT NULL AND a.tenant_confirmed_at IS NOT NULL
  AND NOT EXISTS(SELECT 1 FROM room_reviews v WHERE v.rental_agreement_id=a.id);
SET @demo_review_inserted_rooms=ROW_COUNT();

COMMIT;

SELECT @demo_review_inserted_agreements AS new_demo_agreements,
       @demo_review_inserted_tenants AS new_tenant_reviews,
       @demo_review_inserted_landlords AS new_landlord_reviews,
       @demo_review_inserted_rooms AS new_room_reviews;

-- Với bộ V2 đầy đủ, lần đầu thêm 100 hợp đồng và 100 review mỗi loại;
-- mỗi tenant có review nhận được, cả 24 landlord có review nhận được.
-- Nếu thiếu tài khoản/phòng tương ứng, số lượng thực tế sẽ thấp hơn.
SELECT COUNT(DISTINCT a.tenant_id) AS tenants_with_demo_reviews,
       COUNT(DISTINCT a.landlord_id) AS landlords_with_demo_reviews,
       COUNT(*) AS demo_review_agreements
FROM rental_agreements a JOIN tmp_review_demo_plan p ON p.agreement_id=a.id
JOIN tenant_reviews tr ON tr.rental_agreement_id=a.id
JOIN landlord_reviews lr ON lr.rental_agreement_id=a.id;

DROP TEMPORARY TABLE tmp_review_demo_plan;
DROP TEMPORARY TABLE tmp_review_demo_rooms;
DROP TEMPORARY TABLE tmp_review_demo_tenants;
