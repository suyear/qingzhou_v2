-- =============================================================================
-- V8：License 管理菜单权限
-- =============================================================================

USE `qingzhou`;

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'menu:license', 'License 管理', 'MENU', 'group:系统', 105
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'menu:license' AND `deleted` = 0);

INSERT INTO `qz_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `qz_role` r
CROSS JOIN `qz_permission` p
WHERE r.role_code = 'ADMIN' AND r.deleted = 0 AND p.deleted = 0
  AND p.perm_code = 'menu:license'
  AND NOT EXISTS (
    SELECT 1 FROM `qz_role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
