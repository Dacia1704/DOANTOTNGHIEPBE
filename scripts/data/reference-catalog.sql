-- 1. ROLES / PERMISSIONS: actual codes consumed by JWT and endpoint authorization.

INSERT INTO roles (id, code, name)
SELECT 'e6aa0708-cd88-5285-82d6-394ebff994e9', 'ADMIN', 'Quản trị viên'
    WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code='ADMIN');

INSERT INTO roles (id, code, name)
SELECT 'de6f7f9d-f11b-55a0-bc92-c75d26572f40', 'LANDLORD', 'Chủ trọ'
    WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code='LANDLORD');

INSERT INTO roles (id, code, name)
SELECT 'd84a6b9e-2a25-5d69-879e-8e97445d5c1f', 'TENANT', 'Người thuê'
    WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code='TENANT');

INSERT INTO permissions (id, code, name)
SELECT 'c4419f37-25f0-5526-b9ec-4dc1daf32511', 'USER_READ', 'Xem danh sách người dùng'
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='USER_READ');

INSERT INTO permissions (id, code, name)
SELECT '481b081e-9671-52ea-91ac-b816f9a9716d', 'USER_MANAGE', 'Quản lý người dùng'
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='USER_MANAGE');

INSERT INTO permissions (id, code, name)
SELECT 'dd96672e-f106-5847-a00b-314baa2d1e85', 'ROOM_READ', 'Xem thông tin phòng'
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='ROOM_READ');

INSERT INTO permissions (id, code, name)
SELECT '6ba605d0-f1a4-56cd-8f3e-535a5b815a2a', 'ROOM_CREATE', 'Tạo phòng cho thuê'
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='ROOM_CREATE');

INSERT INTO permissions (id, code, name)
SELECT '34ad667c-3964-5cbc-b2a5-5ceb47c2944a', 'ROOM_UPDATE', 'Cập nhật phòng cho thuê'
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='ROOM_UPDATE');

INSERT INTO permissions (id, code, name)
SELECT '2b753176-343a-57b1-8f3a-8032026185d7', 'ROOM_DELETE', 'Xóa phòng cho thuê'
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='ROOM_DELETE');

INSERT INTO permissions (id, code, name)
SELECT '2f539ef6-ec0b-5e29-a1ab-55467530d01e', 'LISTING_READ', 'Xem bài đăng cho thuê'
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='LISTING_READ');

INSERT INTO permissions (id, code, name)
SELECT '1b155e22-93cb-5a09-8c30-45c0fe0415aa', 'LISTING_CREATE', 'Tạo bài đăng cho thuê'
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='LISTING_CREATE');

INSERT INTO permissions (id, code, name)
SELECT '26d165de-af26-570b-b7f1-31b88560dc6a', 'LISTING_MANAGE', 'Quản lý và kiểm duyệt bài đăng'
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='LISTING_MANAGE');

INSERT INTO permissions (id, code, name)
SELECT '8ca4a8d5-45b8-5575-83ed-7114bff1e1f8', 'WARD_WRITE', 'Tạo và cập nhật xã, phường, đặc khu'
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='WARD_WRITE');

INSERT INTO permissions (id, code, name)
SELECT '8999d0aa-d8e0-5be6-965e-1008e9016a4d', 'WARD_DELETE', 'Xóa xã, phường, đặc khu'
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='WARD_DELETE');

INSERT INTO role_permissions (id, role_id, permission_id)
SELECT UUID(), r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code='ADMIN' AND p.code IN ('USER_READ', 'USER_MANAGE', 'ROOM_READ', 'ROOM_CREATE', 'ROOM_UPDATE', 'ROOM_DELETE', 'LISTING_READ', 'LISTING_CREATE', 'LISTING_MANAGE', 'WARD_WRITE', 'WARD_DELETE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO role_permissions (id, role_id, permission_id)
SELECT UUID(), r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code='LANDLORD' AND p.code IN ('ROOM_READ', 'ROOM_CREATE', 'ROOM_UPDATE', 'ROOM_DELETE', 'LISTING_READ', 'LISTING_CREATE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO role_permissions (id, role_id, permission_id)
SELECT UUID(), r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code='TENANT' AND p.code IN ('ROOM_READ', 'LISTING_READ')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);


-- 4. PROPERTY TYPES describe the building/site, not a room size or furniture feature.

INSERT INTO property_types (id, name, description)
SELECT '5d67dece-5541-54d5-99ba-4f655ef68c2a', 'Nhà trọ', 'Cơ sở gồm các phòng trọ cho thuê riêng; tiện ích chung quản lý tại cơ sở.'
    WHERE NOT EXISTS (SELECT 1 FROM property_types WHERE name='Nhà trọ');

INSERT INTO property_types (id, name, description)
SELECT '738e5846-bf96-5bf2-8431-cea0b47ce964', 'Tòa căn hộ dịch vụ', 'Tòa nhà gồm các căn hộ độc lập, có khu bếp và vệ sinh riêng.'
    WHERE NOT EXISTS (SELECT 1 FROM property_types WHERE name='Tòa căn hộ dịch vụ');

INSERT INTO property_types (id, name, description)
SELECT 'f3eb51dc-7fb3-541f-b554-4983f9e6886c', 'Nhà riêng', 'Căn nhà độc lập; có thể cho thuê nguyên căn hoặc chia phòng theo cấu hình thực tế.'
    WHERE NOT EXISTS (SELECT 1 FROM property_types WHERE name='Nhà riêng');

INSERT INTO property_types (id, name, description)
SELECT '6636c9af-683b-587b-b4ef-45840639941b', 'Khu lưu trú sinh viên', 'Cơ sở nhiều phòng phục vụ sinh viên, có khu giặt và chỗ để xe chung.'
    WHERE NOT EXISTS (SELECT 1 FROM property_types WHERE name='Khu lưu trú sinh viên');


-- 5. ROOM TYPE TREE. Group nodes classify; actual Room rows use leaf nodes only.

INSERT INTO room_types (id, name, description, parent_id)
SELECT 'd5f2e08b-4a6d-575a-9cfc-9118556064b5', 'Phòng trọ', 'Nhóm phòng cho thuê riêng trong một cơ sở nhà trọ.', NULL
    WHERE NOT EXISTS (SELECT 1 FROM room_types WHERE name='Phòng trọ');

INSERT INTO room_types (id, name, description, parent_id)
SELECT 'abbc7ae4-3bfc-58a3-accc-950fda24f227', 'Phòng trọ khép kín', 'Nhóm phòng có nhà vệ sinh riêng; các biến thể nằm ở cấp con.', (SELECT id FROM room_types WHERE name='Phòng trọ')
    WHERE NOT EXISTS (SELECT 1 FROM room_types WHERE name='Phòng trọ khép kín');

INSERT INTO room_types (id, name, description, parent_id)
SELECT 'abc3d25a-801e-59c9-a9d9-07d6e84b7bf2', 'Phòng khép kín tiêu chuẩn', 'Phòng một sàn, có khu vệ sinh riêng, không có gác lửng.', (SELECT id FROM room_types WHERE name='Phòng trọ khép kín')
    WHERE NOT EXISTS (SELECT 1 FROM room_types WHERE name='Phòng khép kín tiêu chuẩn');

INSERT INTO room_types (id, name, description, parent_id)
SELECT '66724cfe-6cb7-51fa-8d9c-dbde10729254', 'Phòng khép kín có gác', 'Phòng có nhà vệ sinh riêng và gác lửng; diện tích khai báo là diện tích sử dụng.', (SELECT id FROM room_types WHERE name='Phòng trọ khép kín')
    WHERE NOT EXISTS (SELECT 1 FROM room_types WHERE name='Phòng khép kín có gác');

INSERT INTO room_types (id, name, description, parent_id)
SELECT '08147d0c-2914-5228-b378-fac603650bd3', 'Phòng trọ dùng vệ sinh chung', 'Phòng ngủ riêng, sử dụng khu vệ sinh chung của cơ sở.', (SELECT id FROM room_types WHERE name='Phòng trọ')
    WHERE NOT EXISTS (SELECT 1 FROM room_types WHERE name='Phòng trọ dùng vệ sinh chung');

INSERT INTO room_types (id, name, description, parent_id)
SELECT '80c665bd-9e6b-547f-9e70-35d5d72d651c', 'Căn hộ', 'Nhóm đơn vị ở có khu bếp và vệ sinh riêng.', NULL
    WHERE NOT EXISTS (SELECT 1 FROM room_types WHERE name='Căn hộ');

INSERT INTO room_types (id, name, description, parent_id)
SELECT '0deea8fe-4e85-5325-bd68-09ed4ca5ede1', 'Căn hộ studio', 'Căn hộ không gian mở, không tách phòng ngủ bằng phòng riêng.', (SELECT id FROM room_types WHERE name='Căn hộ')
    WHERE NOT EXISTS (SELECT 1 FROM room_types WHERE name='Căn hộ studio');

INSERT INTO room_types (id, name, description, parent_id)
SELECT 'fc30e97b-3233-5eca-893f-e8365361aad4', 'Căn hộ một phòng ngủ', 'Căn hộ có một phòng ngủ riêng, ngoài khu sinh hoạt và bếp.', (SELECT id FROM room_types WHERE name='Căn hộ')
    WHERE NOT EXISTS (SELECT 1 FROM room_types WHERE name='Căn hộ một phòng ngủ');

INSERT INTO room_types (id, name, description, parent_id)
SELECT '7a240c54-d9fd-54db-bcd6-a388555248d6', 'Căn hộ hai phòng ngủ', 'Căn hộ có hai phòng ngủ riêng, ngoài khu sinh hoạt và bếp.', (SELECT id FROM room_types WHERE name='Căn hộ')
    WHERE NOT EXISTS (SELECT 1 FROM room_types WHERE name='Căn hộ hai phòng ngủ');

INSERT INTO room_types (id, name, description, parent_id)
SELECT 'fe137fa6-89e4-5469-a9a7-82c00d97328c', 'Nhà nguyên căn', 'Nhóm đơn vị cho thuê toàn bộ căn nhà với một giá thuê chung.', NULL
    WHERE NOT EXISTS (SELECT 1 FROM room_types WHERE name='Nhà nguyên căn');

INSERT INTO room_types (id, name, description, parent_id)
SELECT 'e1aa4737-a778-5591-a3ac-bdb02bdac5ed', 'Nhà riêng một tầng', 'Căn nhà độc lập một tầng; giá thuê áp dụng cho toàn bộ căn.', (SELECT id FROM room_types WHERE name='Nhà nguyên căn')
    WHERE NOT EXISTS (SELECT 1 FROM room_types WHERE name='Nhà riêng một tầng');

INSERT INTO room_types (id, name, description, parent_id)
SELECT 'd1eb15ce-eaf2-55e6-89a5-d46a6f304528', 'Nhà riêng nhiều tầng', 'Căn nhà độc lập từ hai tầng; một đơn vị WHOLE đại diện cho cả căn.', (SELECT id FROM room_types WHERE name='Nhà nguyên căn')
    WHERE NOT EXISTS (SELECT 1 FROM room_types WHERE name='Nhà riêng nhiều tầng');


-- 6. AMENITIES. Shared services belong to Property; private equipment belongs to Room.

INSERT INTO amenities (id, name, icon)
SELECT 'bee1dbee-07cb-594a-938e-c18ecd73ed5f', 'WiFi', 'wifi'
    WHERE NOT EXISTS (SELECT 1 FROM amenities WHERE name='WiFi');

INSERT INTO amenities (id, name, icon)
SELECT '635b0087-56aa-5266-90e9-beb4ea751bb8', 'Chỗ để xe', 'local_parking'
    WHERE NOT EXISTS (SELECT 1 FROM amenities WHERE name='Chỗ để xe');

INSERT INTO amenities (id, name, icon)
SELECT '1802dd56-1122-57ba-bf70-4af0203bcac1', 'Camera an ninh', 'videocam'
    WHERE NOT EXISTS (SELECT 1 FROM amenities WHERE name='Camera an ninh');

INSERT INTO amenities (id, name, icon)
SELECT '8a39d422-87d3-5388-bfc4-cae54300cde0', 'Thang máy', 'elevator'
    WHERE NOT EXISTS (SELECT 1 FROM amenities WHERE name='Thang máy');

INSERT INTO amenities (id, name, icon)
SELECT '8a9c7e49-d043-53d9-acb0-ef6eafba38cd', 'Máy giặt dùng chung', 'local_laundry_service'
    WHERE NOT EXISTS (SELECT 1 FROM amenities WHERE name='Máy giặt dùng chung');

INSERT INTO amenities (id, name, icon)
SELECT '80e7693b-c0d0-51db-a9c8-c1d76f28fe98', 'Điều hòa', 'ac_unit'
    WHERE NOT EXISTS (SELECT 1 FROM amenities WHERE name='Điều hòa');

INSERT INTO amenities (id, name, icon)
SELECT 'ff775c18-fe3a-5846-b28d-aa985584486d', 'Bình nước nóng', 'water_heater'
    WHERE NOT EXISTS (SELECT 1 FROM amenities WHERE name='Bình nước nóng');

INSERT INTO amenities (id, name, icon)
SELECT 'd306dc0c-fe60-5f7b-8fd5-f127418ebb9e', 'Bếp riêng', 'cooking'
    WHERE NOT EXISTS (SELECT 1 FROM amenities WHERE name='Bếp riêng');

INSERT INTO amenities (id, name, icon)
SELECT '7938e67d-8413-5917-949b-e9032a11356e', 'Ban công', 'balcony'
    WHERE NOT EXISTS (SELECT 1 FROM amenities WHERE name='Ban công');

INSERT INTO amenities (id, name, icon)
SELECT 'c02c6f9a-78d0-5cd8-84a7-2e2070ec2f85', 'Tủ lạnh', 'kitchen'
    WHERE NOT EXISTS (SELECT 1 FROM amenities WHERE name='Tủ lạnh');

INSERT INTO amenities (id, name, icon)
SELECT '18c98417-a3f7-50f9-bcec-6110180ef294', 'Giường', 'bed'
    WHERE NOT EXISTS (SELECT 1 FROM amenities WHERE name='Giường');

INSERT INTO amenities (id, name, icon)
SELECT 'e4eb7ac3-5ec8-5c66-9a03-cff47258ab17', 'Tủ quần áo', 'dresser'
    WHERE NOT EXISTS (SELECT 1 FROM amenities WHERE name='Tủ quần áo');

INSERT INTO amenities (id, name, icon)
SELECT '6a3ed9f4-6899-5a3f-8868-f402a3157faf', 'Cửa sổ', 'window'
    WHERE NOT EXISTS (SELECT 1 FROM amenities WHERE name='Cửa sổ');

INSERT INTO amenities (id, name, icon)
SELECT 'fa1f99bb-9611-5b00-9cf1-9c4fbff7e4cb', 'Nhà vệ sinh riêng', 'bathroom'
    WHERE NOT EXISTS (SELECT 1 FROM amenities WHERE name='Nhà vệ sinh riêng');
