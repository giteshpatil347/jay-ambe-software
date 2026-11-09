-- Manual stock adjustments (opening stock). Idempotent: safe if the permission/mapping already exists.
INSERT INTO permissions (permission_code, module_code, action_code)
VALUES ('INVENTORY.ADJUST', 'INVENTORY', 'ADJUST')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.permission_code = 'INVENTORY.ADJUST'
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
ON CONFLICT DO NOTHING;
