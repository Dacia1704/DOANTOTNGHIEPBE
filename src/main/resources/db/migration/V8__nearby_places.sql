-- Shared destination catalogue. Tenant anchor_* remains the coordinate snapshot used by AI.
CREATE TABLE nearby_places (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(30) NOT NULL,
    address VARCHAR(500) NULL,
    latitude DECIMAL(10,8) NOT NULL,
    longitude DECIMAL(11,8) NOT NULL,
    INDEX idx_nearby_places_category_name (category, name),
    CONSTRAINT chk_np_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_np_lng CHECK (longitude BETWEEN -180 AND 180)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE tenant_profiles
    ADD COLUMN anchor_place_id VARCHAR(36) NULL,
    ADD CONSTRAINT fk_tp_anchor_place FOREIGN KEY (anchor_place_id) REFERENCES nearby_places(id) ON DELETE SET NULL;

-- Demo coordinates are approximate campus/park centres, not routing entrances.
INSERT INTO nearby_places (id, name, category, address, latitude, longitude) VALUES
('poi-hcm-hcmut', 'Đại học Bách khoa TP.HCM - Lý Thường Kiệt', 'UNIVERSITY', '268 Lý Thường Kiệt, TP.HCM', 10.77210000, 106.65790000),
('poi-hcm-hutech', 'Đại học HUTECH - Điện Biên Phủ', 'UNIVERSITY', '475A Điện Biên Phủ, TP.HCM', 10.80200000, 106.71460000),
('poi-hcm-uit', 'Đại học Công nghệ Thông tin - ĐHQG TP.HCM', 'UNIVERSITY', 'Khu phố 6, Linh Trung, TP.HCM', 10.87000000, 106.80310000),
('poi-hcm-ussh', 'Đại học Khoa học Xã hội và Nhân văn - Đinh Tiên Hoàng', 'UNIVERSITY', '10-12 Đinh Tiên Hoàng, TP.HCM', 10.78580000, 106.70290000),
('poi-hcm-gia-dinh', 'Công viên Gia Định', 'PARK', 'Hoàng Minh Giám, TP.HCM', 10.81250000, 106.67360000),
('poi-hcm-tao-dan', 'Công viên Tao Đàn', 'PARK', 'Trương Định, TP.HCM', 10.77660000, 106.68680000),
('poi-hn-hust', 'Đại học Bách khoa Hà Nội', 'UNIVERSITY', '1 Đại Cồ Việt, Hà Nội', 21.00700000, 105.84310000),
('poi-hn-neu', 'Đại học Kinh tế Quốc dân', 'UNIVERSITY', '207 Giải Phóng, Hà Nội', 21.00090000, 105.84290000),
('poi-hn-thuong-mai', 'Đại học Thương mại', 'UNIVERSITY', '79 Hồ Tùng Mậu, Hà Nội', 21.03670000, 105.77400000),
('poi-hn-thong-nhat', 'Công viên Thống Nhất', 'PARK', 'Trần Nhân Tông, Hà Nội', 21.01310000, 105.84120000),
('poi-dn-dut', 'Đại học Bách khoa - Đại học Đà Nẵng', 'UNIVERSITY', '54 Nguyễn Lương Bằng, Đà Nẵng', 16.07370000, 108.14990000),
('poi-dn-29-3', 'Công viên 29/3', 'PARK', 'Điện Biên Phủ, Đà Nẵng', 16.06020000, 108.20310000),
('poi-ct-ctu', 'Đại học Cần Thơ - Khu II', 'UNIVERSITY', 'Đường 3/2, Cần Thơ', 10.03080000, 105.76850000);
