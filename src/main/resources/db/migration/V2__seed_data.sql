-- Bootstrap authentication data in the backend database.
USE rental_db;

-- Bootstrap roles. Preserve IDs if a role was already created by the application.
INSERT INTO roles (id, code, name, created_at, updated_at)
VALUES
    (UUID(), 'ADMIN', 'Administrator', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (UUID(), 'LANDLORD', 'Landlord', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (UUID(), 'TENANT', 'Tenant', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE name = roles.name;

-- Three permissions per resource: READ, WRITE (create/update), DELETE.
INSERT INTO permissions (id, code, name, created_at, updated_at)
VALUES
    (UUID(), 'USER_READ', 'Read users', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (UUID(), 'USER_WRITE', 'Create and update users', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (UUID(), 'USER_DELETE', 'Delete users', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (UUID(), 'PERMISSION_READ', 'Read permissions', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (UUID(), 'PERMISSION_WRITE', 'Create and update permissions', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (UUID(), 'PERMISSION_DELETE', 'Delete permissions', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (UUID(), 'ROOM_READ', 'Read rooms', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (UUID(), 'ROOM_WRITE', 'Create and update rooms', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (UUID(), 'ROOM_DELETE', 'Delete rooms', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE name = permissions.name;

-- Development bootstrap account: admin@rental.local / Admin@123456.
-- BCrypt cost 10, matching PasswordEncoderConfig. Change after first login.
-- Do not overwrite an existing account or promote it to administrator.
INSERT INTO users (id, email, password, full_name, status, email_verified_at, created_at, updated_at)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'admin@rental.local',
    '$2a$10$Wv53grF65.eBa4EM8P8/ne3IXZk3tNFQz9gdQZE9iyBSn1fezYgJy',
    'System Administrator',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE email = 'admin@rental.local'
);

INSERT INTO user_roles (id, user_id, role_id)
SELECT UUID(), u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.id = '00000000-0000-4000-8000-000000000001'
  AND u.email = 'admin@rental.local'
  AND r.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- Only ADMIN receives permissions here. Other role policies are configured separately.
INSERT INTO role_permissions (id, role_id, permission_id)
SELECT UUID(), r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN (
      'USER_READ', 'USER_WRITE', 'USER_DELETE',
      'PERMISSION_READ', 'PERMISSION_WRITE', 'PERMISSION_DELETE',
      'ROOM_READ', 'ROOM_WRITE', 'ROOM_DELETE'
  )
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Invalidates access tokens after password/status changes.
ALTER TABLE users
    ADD COLUMN auth_version INT NOT NULL DEFAULT 0;
