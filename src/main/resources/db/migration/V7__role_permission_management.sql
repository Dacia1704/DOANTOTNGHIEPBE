ALTER TABLE roles ADD permission_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE permission_audit (
 id VARCHAR(36) PRIMARY KEY, actor_id VARCHAR(36) NOT NULL,
 role_id VARCHAR(36) NOT NULL, permission_id VARCHAR(36) NOT NULL,
 old_enabled BOOLEAN NOT NULL, new_enabled BOOLEAN NOT NULL,
 role_version BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX ix_permission_audit_created(created_at,id)
);
INSERT INTO permissions(id,code,name) SELECT UUID(),'USER_READ','Xem người dùng' WHERE NOT EXISTS(SELECT 1 FROM permissions WHERE code='USER_READ');
INSERT INTO role_permissions(id,role_id,permission_id) SELECT UUID(),r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.code='ADMIN' AND p.code='USER_READ' AND NOT EXISTS(SELECT 1 FROM role_permissions rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
INSERT INTO permissions(id,code,name) SELECT UUID(),'USER_MANAGE','Quản lý người dùng' WHERE NOT EXISTS(SELECT 1 FROM permissions WHERE code='USER_MANAGE');
INSERT INTO role_permissions(id,role_id,permission_id) SELECT UUID(),r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.code='ADMIN' AND p.code='USER_MANAGE' AND NOT EXISTS(SELECT 1 FROM role_permissions rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
INSERT INTO permissions(id,code,name) SELECT UUID(),'REVIEW_READ','Xem quản trị đánh giá' WHERE NOT EXISTS(SELECT 1 FROM permissions WHERE code='REVIEW_READ');
INSERT INTO role_permissions(id,role_id,permission_id) SELECT UUID(),r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.code='ADMIN' AND p.code='REVIEW_READ' AND NOT EXISTS(SELECT 1 FROM role_permissions rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
INSERT INTO permissions(id,code,name) SELECT UUID(),'REVIEW_MODERATE','Ẩn hiện đánh giá' WHERE NOT EXISTS(SELECT 1 FROM permissions WHERE code='REVIEW_MODERATE');
INSERT INTO role_permissions(id,role_id,permission_id) SELECT UUID(),r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.code='ADMIN' AND p.code='REVIEW_MODERATE' AND NOT EXISTS(SELECT 1 FROM role_permissions rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
INSERT INTO permissions(id,code,name) SELECT UUID(),'REPORT_READ','Xem hồ sơ báo cáo' WHERE NOT EXISTS(SELECT 1 FROM permissions WHERE code='REPORT_READ');
INSERT INTO role_permissions(id,role_id,permission_id) SELECT UUID(),r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.code='ADMIN' AND p.code='REPORT_READ' AND NOT EXISTS(SELECT 1 FROM role_permissions rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
INSERT INTO permissions(id,code,name) SELECT UUID(),'REPORT_HANDLE','Xử lý báo cáo' WHERE NOT EXISTS(SELECT 1 FROM permissions WHERE code='REPORT_HANDLE');
INSERT INTO role_permissions(id,role_id,permission_id) SELECT UUID(),r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.code='ADMIN' AND p.code='REPORT_HANDLE' AND NOT EXISTS(SELECT 1 FROM role_permissions rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
INSERT INTO permissions(id,code,name) SELECT UUID(),'ROLE_PERMISSION_READ','Xem phân quyền' WHERE NOT EXISTS(SELECT 1 FROM permissions WHERE code='ROLE_PERMISSION_READ');
INSERT INTO role_permissions(id,role_id,permission_id) SELECT UUID(),r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.code='ADMIN' AND p.code='ROLE_PERMISSION_READ' AND NOT EXISTS(SELECT 1 FROM role_permissions rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
INSERT INTO permissions(id,code,name) SELECT UUID(),'ROLE_PERMISSION_MANAGE','Quản lý phân quyền' WHERE NOT EXISTS(SELECT 1 FROM permissions WHERE code='ROLE_PERMISSION_MANAGE');
INSERT INTO role_permissions(id,role_id,permission_id) SELECT UUID(),r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.code='ADMIN' AND p.code='ROLE_PERMISSION_MANAGE' AND NOT EXISTS(SELECT 1 FROM role_permissions rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
