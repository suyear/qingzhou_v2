-- =============================================================================
-- V7：权限点与角色权限（菜单 / 操作）
-- =============================================================================

USE `qingzhou`;

CREATE TABLE IF NOT EXISTS `qz_permission` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `perm_code`       VARCHAR(64)  NOT NULL COMMENT '权限编码，如 menu:components / component:write',
  `perm_name`       VARCHAR(64)  NOT NULL COMMENT '显示名',
  `perm_type`       VARCHAR(16)  NOT NULL DEFAULT 'MENU' COMMENT 'MENU / ACTION',
  `parent_code`     VARCHAR(64)           DEFAULT NULL COMMENT '分组父级编码',
  `sort_order`      INT          NOT NULL DEFAULT 0,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限点';

CREATE TABLE IF NOT EXISTS `qz_role_permission` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT,
  `role_id`         BIGINT   NOT NULL,
  `permission_id`   BIGINT   NOT NULL,
  `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
  KEY `idx_perm` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限';

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'menu:workbench', '工作台', 'MENU', NULL, 10
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'menu:workbench' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'menu:components', '接口组件', 'MENU', 'group:编排', 20
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'menu:components' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'menu:workflows', '工作流编排', 'MENU', 'group:编排', 30
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'menu:workflows' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'menu:credentials', '凭证管理', 'MENU', 'group:编排', 40
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'menu:credentials' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'menu:schedules', '定时调度', 'MENU', 'group:运行', 50
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'menu:schedules' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'menu:executions', '运行结果', 'MENU', 'group:运行', 60
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'menu:executions' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'menu:openapi', '开放平台', 'MENU', 'group:开放', 70
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'menu:openapi' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'menu:users', '用户管理', 'MENU', 'group:系统', 80
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'menu:users' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'menu:roles', '角色权限', 'MENU', 'group:系统', 90
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'menu:roles' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'menu:audit', '审计日志', 'MENU', 'group:系统', 100
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'menu:audit' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'menu:settings', '系统设置', 'MENU', 'group:系统', 110
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'menu:settings' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'component:write', '组件增改删', 'ACTION', 'group:操作', 200
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'component:write' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'credential:write', '凭证增改删', 'ACTION', 'group:操作', 210
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'credential:write' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'workflow:write', '工作流发布', 'ACTION', 'group:操作', 220
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'workflow:write' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'schedule:write', '调度启停', 'ACTION', 'group:操作', 230
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'schedule:write' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'openapi:write', '开放应用管理', 'ACTION', 'group:操作', 240
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'openapi:write' AND `deleted` = 0);

INSERT INTO `qz_permission` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT 'system:admin', '系统管理', 'ACTION', 'group:操作', 250
WHERE NOT EXISTS (SELECT 1 FROM `qz_permission` WHERE `perm_code` = 'system:admin' AND `deleted` = 0);

-- ADMIN：全部
INSERT INTO `qz_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `qz_role` r
CROSS JOIN `qz_permission` p
WHERE r.role_code = 'ADMIN' AND r.deleted = 0 AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `qz_role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- DEVELOPER
INSERT INTO `qz_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `qz_role` r
CROSS JOIN `qz_permission` p
WHERE r.role_code = 'DEVELOPER' AND r.deleted = 0 AND p.deleted = 0
  AND p.perm_code IN (
    'menu:workbench','menu:components','menu:workflows','menu:credentials',
    'menu:schedules','menu:executions','menu:openapi',
    'component:write','credential:write','workflow:write','schedule:write','openapi:write'
  )
  AND NOT EXISTS (
    SELECT 1 FROM `qz_role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- VIEWER
INSERT INTO `qz_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `qz_role` r
CROSS JOIN `qz_permission` p
WHERE r.role_code = 'VIEWER' AND r.deleted = 0 AND p.deleted = 0
  AND p.perm_code IN (
    'menu:workbench','menu:components','menu:workflows',
    'menu:schedules','menu:executions','menu:openapi'
  )
  AND NOT EXISTS (
    SELECT 1 FROM `qz_role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
