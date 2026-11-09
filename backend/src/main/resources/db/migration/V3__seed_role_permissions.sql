-- Initial role -> permission mapping. Admins can change it later through the UI.
-- Super Admin protection (nobody but a Super Admin may assign SUPER_ADMIN) is enforced in the service layer.

CREATE TEMP TABLE _rp (role_code VARCHAR, permission_code VARCHAR);

-- SUPER_ADMIN: everything
INSERT INTO _rp SELECT 'SUPER_ADMIN', permission_code FROM permissions;

-- ADMIN: everything except business settings
INSERT INTO _rp SELECT 'ADMIN', permission_code FROM permissions WHERE permission_code <> 'SETTINGS.MANAGE';

-- PURCHASE_STAFF: create/view purchases only (edit & cancel need explicit grant)
INSERT INTO _rp SELECT 'PURCHASE_STAFF', c FROM unnest(ARRAY[
 'DASHBOARD.VIEW','PRODUCT.VIEW','INVENTORY.VIEW','PARTY.VIEW','PARTY.CREATE',
 'MATERIAL_IN.VIEW','MATERIAL_IN.CREATE']) c;

-- DISPATCH_STAFF
INSERT INTO _rp SELECT 'DISPATCH_STAFF', c FROM unnest(ARRAY[
 'DASHBOARD.VIEW','PRODUCT.VIEW','INVENTORY.VIEW','PARTY.VIEW','PARTY.CREATE',
 'MATERIAL_OUT.VIEW','MATERIAL_OUT.CREATE']) c;

-- ACCOUNTANT: money-side modules + read access to material
INSERT INTO _rp SELECT 'ACCOUNTANT', permission_code FROM permissions
 WHERE module_code IN ('EXPENSE','EXPENSE_CATEGORY','EMPLOYEE','PARTY','PAYMENT','REPORT')
    OR permission_code IN ('DASHBOARD.VIEW','PRODUCT.VIEW','INVENTORY.VIEW','INVENTORY.EXPORT',
                           'MATERIAL_IN.VIEW','MATERIAL_IN.EXPORT','MATERIAL_OUT.VIEW','MATERIAL_OUT.EXPORT');

-- VIEWER: read-only on business data (no users/roles/permissions/audit/settings)
INSERT INTO _rp SELECT 'VIEWER', permission_code FROM permissions
 WHERE action_code = 'VIEW' AND module_code NOT IN ('USER','ROLE','PERMISSION','AUDIT');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM _rp
JOIN roles r ON r.role_code = _rp.role_code
JOIN permissions p ON p.permission_code = _rp.permission_code;
DROP TABLE _rp;
