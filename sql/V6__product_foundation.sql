-- =============================================================================
-- V6：产品化地基 — 用户/角色、审计、License、系统设置
-- 在 V5 之后执行；新库按 V1→V2→V4→V5→V6 顺序执行。
-- =============================================================================

USE `qingzhou`;

-- -----------------------------------------------------------------------------
-- 用户
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `qz_user` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`             VARCHAR(64)  NOT NULL COMMENT '登录名',
  `display_name`         VARCHAR(64)  NOT NULL COMMENT '显示名',
  `password_hash`        VARCHAR(128) NOT NULL COMMENT 'BCrypt 密码哈希',
  `status`               TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
  `must_change_password` TINYINT      NOT NULL DEFAULT 0 COMMENT '1-首次登录须改密',
  `last_login_at`        DATETIME              DEFAULT NULL COMMENT '最近登录时间',
  `last_login_ip`        VARCHAR(64)           DEFAULT NULL COMMENT '最近登录 IP',
  `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by`            VARCHAR(64)           DEFAULT NULL,
  `update_by`            VARCHAR(64)           DEFAULT NULL,
  `deleted`              TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`, `deleted`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='控制台用户';

-- -----------------------------------------------------------------------------
-- 角色（固定三角色，种子数据）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `qz_role` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `role_code`   VARCHAR(32)  NOT NULL COMMENT 'ADMIN / DEVELOPER / VIEWER',
  `role_name`   VARCHAR(64)  NOT NULL,
  `description` VARCHAR(256)          DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色';

CREATE TABLE IF NOT EXISTS `qz_user_role` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT   NOT NULL,
  `role_id`     BIGINT   NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色';

INSERT INTO `qz_role` (`role_code`, `role_name`, `description`)
SELECT 'ADMIN', '管理员', '用户/系统/License 与全部业务'
WHERE NOT EXISTS (SELECT 1 FROM `qz_role` WHERE `role_code` = 'ADMIN' AND `deleted` = 0);

INSERT INTO `qz_role` (`role_code`, `role_name`, `description`)
SELECT 'DEVELOPER', '开发者', '组件/凭证/工作流/调度/开放应用 CRUD'
WHERE NOT EXISTS (SELECT 1 FROM `qz_role` WHERE `role_code` = 'DEVELOPER' AND `deleted` = 0);

INSERT INTO `qz_role` (`role_code`, `role_name`, `description`)
SELECT 'VIEWER', '只读运维', '工作台与运行结果只读'
WHERE NOT EXISTS (SELECT 1 FROM `qz_role` WHERE `role_code` = 'VIEWER' AND `deleted` = 0);

-- -----------------------------------------------------------------------------
-- 操作审计
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `qz_audit_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `actor_id`      BIGINT                DEFAULT NULL COMMENT '操作人用户 ID',
  `actor_name`    VARCHAR(64)           DEFAULT NULL COMMENT '操作人登录名',
  `action`        VARCHAR(64)  NOT NULL COMMENT '动作编码，如 USER_CREATE / WORKFLOW_PUBLISH',
  `resource_type` VARCHAR(64)           DEFAULT NULL COMMENT '资源类型',
  `resource_id`   VARCHAR(64)           DEFAULT NULL COMMENT '资源 ID',
  `result`        VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAIL',
  `summary`       VARCHAR(512)          DEFAULT NULL COMMENT '摘要',
  `detail_json`   JSON                  DEFAULT NULL COMMENT '扩展详情',
  `client_ip`     VARCHAR(64)           DEFAULT NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_actor` (`actor_id`),
  KEY `idx_action` (`action`),
  KEY `idx_resource` (`resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志';

-- -----------------------------------------------------------------------------
-- License（当前生效的一条；历史可覆盖）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `qz_license` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `license_payload` TEXT         NOT NULL COMMENT 'License JSON 原文',
  `signature`       VARCHAR(256) NOT NULL COMMENT 'HMAC 签名',
  `issuer`          VARCHAR(128)          DEFAULT NULL,
  `customer`        VARCHAR(128)          DEFAULT NULL,
  `expires_at`      DATE                  DEFAULT NULL,
  `seats`           INT          NOT NULL DEFAULT 5,
  `features_json`   JSON                  DEFAULT NULL,
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / EXPIRED / INVALID',
  `imported_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `imported_by`     VARCHAR(64)           DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品 License';

-- -----------------------------------------------------------------------------
-- 系统设置（键值）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `qz_system_setting` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `setting_key`  VARCHAR(64)  NOT NULL,
  `setting_value` TEXT                 DEFAULT NULL,
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `update_by`    VARCHAR(64)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_setting_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统设置';

INSERT INTO `qz_system_setting` (`setting_key`, `setting_value`)
SELECT 'site_name', '轻舟'
WHERE NOT EXISTS (SELECT 1 FROM `qz_system_setting` WHERE `setting_key` = 'site_name');

INSERT INTO `qz_system_setting` (`setting_key`, `setting_value`)
SELECT 'openapi_public_base_url', ''
WHERE NOT EXISTS (SELECT 1 FROM `qz_system_setting` WHERE `setting_key` = 'openapi_public_base_url');

INSERT INTO `qz_system_setting` (`setting_key`, `setting_value`)
SELECT 'execution_retention_days', '90'
WHERE NOT EXISTS (SELECT 1 FROM `qz_system_setting` WHERE `setting_key` = 'execution_retention_days');

INSERT INTO `qz_system_setting` (`setting_key`, `setting_value`)
SELECT 'timezone', 'Asia/Shanghai'
WHERE NOT EXISTS (SELECT 1 FROM `qz_system_setting` WHERE `setting_key` = 'timezone');
