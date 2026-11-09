-- Dashboard money sections (expenses, receivable/payable, recent transactions) get their own permission.
-- Idempotent: safe if the permission/mappings already exist.
INSERT INTO permissions (permission_code, module_code, action_code, description)
VALUES ('DASHBOARD.FINANCIAL', 'DASHBOARD', 'FINANCIAL',
        'See expenses, receivable/payable and recent transactions on the dashboard')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.permission_code = 'DASHBOARD.FINANCIAL'
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'ACCOUNTANT')
ON CONFLICT DO NOTHING;

-- VIEWER is reduced to material in/out and stock only. NOTE: this INTENTIONALLY OVERRIDES any manual edits
-- made to the VIEWER role before this migration; an admin can re-grant more through the permission matrix.
DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE role_code = 'VIEWER')
  AND permission_id NOT IN (SELECT id FROM permissions
                            WHERE permission_code IN ('DASHBOARD.VIEW', 'MATERIAL_IN.VIEW', 'MATERIAL_OUT.VIEW',
                                                      'INVENTORY.VIEW', 'PRODUCT.VIEW'));

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.permission_code IN ('DASHBOARD.VIEW', 'MATERIAL_IN.VIEW', 'MATERIAL_OUT.VIEW',
                                            'INVENTORY.VIEW', 'PRODUCT.VIEW')
WHERE r.role_code = 'VIEWER'
ON CONFLICT DO NOTHING;
