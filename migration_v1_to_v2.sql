-- v1 -> v2 migration (MySQL). Run once via: python scripts/dbtool.py run migration_v1_to_v2.sql
-- Take a backup first: python scripts/dbtool.py backup

-- 1. Give existing roles/permissions real UUID ids
UPDATE role SET id = UUID() WHERE id IS NULL OR id = '';
UPDATE permission SET id = UUID() WHERE id IS NULL OR id = '';

-- 2. Copy legacy join-table data (keyed by name) into the new id-keyed join tables
INSERT INTO user_role (user_id, role_id)
SELECT ur.user_id, r.id FROM user_roles ur JOIN role r ON r.name = ur.roles_name;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role_permissions rp
JOIN role r ON r.name = rp.role_name
JOIN permission p ON p.name = rp.permissions_name;

-- 3. Retire legacy join tables (kept as *_legacy, not dropped)
ALTER TABLE user_roles DROP FOREIGN KEY FK55itppkw3i07do3h7qoclqd4k;
ALTER TABLE user_roles DROP FOREIGN KEY FK6pmbiap985ue1c0qjic44pxlc;
ALTER TABLE role_permissions DROP FOREIGN KEY FKcppvu8fk24eqqn6q4hws7ajux;
ALTER TABLE role_permissions DROP FOREIGN KEY FKf5aljih4mxtdgalvr7xvngfn1;
RENAME TABLE user_roles TO user_roles_legacy, role_permissions TO role_permissions_legacy;

-- 4. Switch primary keys from name to id
-- (unique indexes idx_role_name / idx_permission_name on name already exist, created by Hibernate)
ALTER TABLE role DROP PRIMARY KEY, MODIFY id VARCHAR(36) NOT NULL, ADD PRIMARY KEY (id);
ALTER TABLE permission DROP PRIMARY KEY, MODIFY id VARCHAR(36) NOT NULL, ADD PRIMARY KEY (id);

-- 5. Audit columns and status defaults
ALTER TABLE role ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE permission ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
UPDATE role SET status = 'ACTIVE' WHERE status IS NULL OR status = '';
UPDATE permission SET status = 'ACTIVE' WHERE status IS NULL OR status = '';

-- 6. Backfill existing users
UPDATE user SET status = 'ACTIVE' WHERE status IS NULL OR status = '';
UPDATE user SET login_method = 'LOCAL' WHERE login_method IS NULL OR login_method = '';
UPDATE user SET created_at = NOW(6) WHERE created_at IS NULL;
UPDATE user SET updated_at = NOW(6) WHERE updated_at IS NULL;
UPDATE user SET login_attempts = 0 WHERE login_attempts IS NULL;

-- 7. Token blacklist: old rows hold non-parseable date strings and no jti; clear and retype
DELETE FROM invalidated_token;
ALTER TABLE invalidated_token MODIFY expiry_time DATETIME(6) NOT NULL, MODIFY jti VARCHAR(64) NOT NULL, ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), ADD UNIQUE KEY uk_invalidated_token_jti (jti);

-- 8. Sanity check
SELECT u.username, r.name AS role FROM user u JOIN user_role ur ON ur.user_id = u.id JOIN role r ON r.id = ur.role_id;
SELECT r.name AS role, p.name AS permission FROM role_permission rp JOIN role r ON r.id = rp.role_id JOIN permission p ON p.id = rp.permission_id;
