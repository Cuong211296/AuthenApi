-- Ensure base roles exist and give every existing non-admin user without a role the USER role.
INSERT INTO role (id, name, description, status, created_at, updated_at)
SELECT UUID(), 'USER', 'Customer', 'ACTIVE', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM role WHERE name = 'USER');

INSERT INTO role (id, name, description, status, created_at, updated_at)
SELECT UUID(), 'ADMIN', 'Administrator', 'ACTIVE', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM role WHERE name = 'ADMIN');

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM `user` u
JOIN role r ON r.name = 'USER'
WHERE u.username <> 'admin'
  AND NOT EXISTS (SELECT 1 FROM user_role ur WHERE ur.user_id = u.id);
