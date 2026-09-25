-- =============================================================================
-- V9：开放平台授权接口组件（接口服务）
-- =============================================================================

USE `qingzhou`;

CREATE TABLE IF NOT EXISTS `qz_openapi_app_component` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `app_id`          BIGINT       NOT NULL COMMENT 'OpenAPI 应用ID',
  `component_id`    BIGINT       NOT NULL COMMENT '接口组件ID',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  `create_by`       VARCHAR(64)           DEFAULT NULL COMMENT '授权人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_component` (`app_id`, `component_id`),
  KEY `idx_component_id` (`component_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OpenAPI 应用授权接口组件';
