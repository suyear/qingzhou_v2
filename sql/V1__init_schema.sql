-- =============================================================================
-- 轻舟 (qingzhou) 低代码集成调度中台 - MySQL 8.0 DDL
-- 字符集: utf8mb4 / 引擎: InnoDB
-- 约定:
--   1. 逻辑删除字段 deleted: 0-正常 1-已删除
--   2. 标注【加密存储点】的字段，应用层写入前必须 AES-GCM 加密，禁止明文落库
--   3. JSON 字段由业务层序列化，库内不做结构化约束
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `qingzhou`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `qingzhou`;

-- -----------------------------------------------------------------------------
-- 1. 接口组件 (第三方 API 原子能力，含预置企业微信组件与自定义 HTTP)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `qz_api_component`;
CREATE TABLE `qz_api_component` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `component_code`    VARCHAR(64)  NOT NULL COMMENT '组件编码，全局唯一，如 wecom.send_message',
  `component_name`    VARCHAR(128) NOT NULL COMMENT '组件显示名称',
  `provider`          VARCHAR(32)  NOT NULL DEFAULT 'CUSTOM' COMMENT '提供方: WECOM / CUSTOM',
  `category`          VARCHAR(32)  NOT NULL DEFAULT 'HTTP' COMMENT '分类: MESSAGE / ORG / GROUP / HTTP',
  `http_method`       VARCHAR(10)  NOT NULL COMMENT 'HTTP 方法: GET/POST/PUT/DELETE/PATCH',
  `url_template`      VARCHAR(512) NOT NULL COMMENT 'URL 模板，支持 ${access_token} 等占位符',
  `headers_schema`    JSON                  DEFAULT NULL COMMENT '请求头 Schema (JSON Schema)',
  `query_schema`      JSON                  DEFAULT NULL COMMENT 'Query 参数 Schema',
  `body_schema`       JSON                  DEFAULT NULL COMMENT '请求体 Schema，画布节点卡片入参预览来源',
  `response_schema`   JSON                  DEFAULT NULL COMMENT '响应体 Schema，画布节点卡片出参预览来源',
  `timeout_ms`        INT          NOT NULL DEFAULT 10000 COMMENT '超时时间(毫秒)',
  `retry_times`       INT          NOT NULL DEFAULT 0 COMMENT '失败重试次数',
  `retry_interval_ms` INT          NOT NULL DEFAULT 1000 COMMENT '重试间隔(毫秒)',
  `is_preset`         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否预置组件: 1-是(不可删) 0-否',
  `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-停用',
  `description`       VARCHAR(512)          DEFAULT NULL COMMENT '组件说明',
  `extra_config`      JSON                  DEFAULT NULL COMMENT '扩展配置(如是否需要 access_token)',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by`         VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `update_by`         VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_component_code` (`component_code`, `deleted`),
  KEY `idx_provider_status` (`provider`, `status`),
  KEY `idx_category` (`category`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口组件';

-- -----------------------------------------------------------------------------
-- 2. 凭证配置 (企业微信 Token 等，支持全局共享 / 工作流独立)
--    secret_cipher 为【加密存储点】
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `qz_credential`;
CREATE TABLE `qz_credential` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `credential_name`   VARCHAR(128) NOT NULL COMMENT '凭证名称',
  `credential_type`   VARCHAR(32)  NOT NULL COMMENT '凭证类型: WECOM / CUSTOM',
  `scope`             VARCHAR(16)  NOT NULL DEFAULT 'GLOBAL' COMMENT '作用域: GLOBAL-全局共享 / WORKFLOW-工作流独立',
  `workflow_id`       BIGINT                DEFAULT NULL COMMENT '独立配置时绑定的工作流ID，GLOBAL 时为空',
  `corp_id`           VARCHAR(128)          DEFAULT NULL COMMENT '企业微信 CorpId',
  `agent_id`          VARCHAR(64)           DEFAULT NULL COMMENT '企业微信 AgentId',
  `secret_cipher`     VARCHAR(512)          DEFAULT NULL COMMENT '【加密存储点】CorpSecret/自定义Secret，AES-GCM 密文',
  `token_cache_key`   VARCHAR(128)          DEFAULT NULL COMMENT 'Redis 中 AccessToken 的缓存 Key',
  `extra_config`      JSON                  DEFAULT NULL COMMENT '扩展字段(自定义 Header/Token URL 等)',
  `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-停用',
  `remark`            VARCHAR(256)          DEFAULT NULL COMMENT '备注',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by`         VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `update_by`         VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_scope_workflow` (`scope`, `workflow_id`),
  KEY `idx_type_status` (`credential_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='凭证配置(Token)';

-- -----------------------------------------------------------------------------
-- 3. 工作流定义 (DAG: nodes + edges 存 graph_json，参数映射存 param_mapping)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `qz_workflow`;
CREATE TABLE `qz_workflow` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workflow_code`     VARCHAR(64)  NOT NULL COMMENT '工作流编码，全局唯一',
  `workflow_name`     VARCHAR(128) NOT NULL COMMENT '工作流名称',
  `description`       VARCHAR(512)          DEFAULT NULL COMMENT '描述',
  `status`            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED/DISABLED',
  `version`           INT          NOT NULL DEFAULT 1 COMMENT '当前版本号，发布时自增',
  `graph_json`        JSON                  DEFAULT NULL COMMENT 'DAG 图数据 {nodes:[], edges:[]}',
  `param_mapping`     JSON                  DEFAULT NULL COMMENT '节点间参数映射 [{fromNode,fromPath,toNode,toPath}]',
  `input_schema`      JSON                  DEFAULT NULL COMMENT '工作流入参 Schema (OpenAPI 调用时校验)',
  `output_schema`     JSON                  DEFAULT NULL COMMENT '工作流出参 Schema',
  `credential_mode`   VARCHAR(16)  NOT NULL DEFAULT 'GLOBAL' COMMENT 'Token 模式: GLOBAL / INDEPENDENT',
  `credential_id`     BIGINT                DEFAULT NULL COMMENT '绑定凭证ID',
  `timeout_ms`        INT          NOT NULL DEFAULT 60000 COMMENT '整条工作流超时(毫秒)',
  `cron_expr`         VARCHAR(64)           DEFAULT NULL COMMENT '定时表达式(对接 XXL-JOB)',
  `xxl_job_id`        INT                   DEFAULT NULL COMMENT 'XXL-JOB 任务ID',
  `publish_time`      DATETIME              DEFAULT NULL COMMENT '最近发布时间',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by`         VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `update_by`         VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_code` (`workflow_code`, `deleted`),
  KEY `idx_status` (`status`),
  KEY `idx_xxl_job_id` (`xxl_job_id`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流定义';

-- -----------------------------------------------------------------------------
-- 4. 工作流发布快照 (执行时绑定已发布版本，避免编排中途改图影响在途任务)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `qz_workflow_snapshot`;
CREATE TABLE `qz_workflow_snapshot` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workflow_id`       BIGINT       NOT NULL COMMENT '工作流ID',
  `version`           INT          NOT NULL COMMENT '版本号',
  `graph_json`        JSON         NOT NULL COMMENT '发布时的 DAG 快照',
  `param_mapping`     JSON                  DEFAULT NULL COMMENT '发布时的参数映射快照',
  `input_schema`      JSON                  DEFAULT NULL COMMENT '入参 Schema 快照',
  `output_schema`     JSON                  DEFAULT NULL COMMENT '出参 Schema 快照',
  `credential_mode`   VARCHAR(16)           DEFAULT NULL COMMENT 'Token 模式快照',
  `credential_id`     BIGINT                DEFAULT NULL COMMENT '凭证ID快照',
  `timeout_ms`        INT                   DEFAULT NULL COMMENT '超时快照',
  `publish_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `publish_by`        VARCHAR(64)           DEFAULT NULL COMMENT '发布人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_version` (`workflow_id`, `version`),
  KEY `idx_publish_time` (`publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流发布快照';

-- -----------------------------------------------------------------------------
-- 5. 执行实例 (一次工作流运行)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `qz_execution_instance`;
CREATE TABLE `qz_execution_instance` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `execution_no`      VARCHAR(64)  NOT NULL COMMENT '执行单号，全局唯一',
  `workflow_id`       BIGINT       NOT NULL COMMENT '工作流ID',
  `workflow_version`  INT          NOT NULL COMMENT '执行时绑定的版本',
  `snapshot_id`       BIGINT                DEFAULT NULL COMMENT '快照ID',
  `trigger_type`      VARCHAR(16)  NOT NULL COMMENT '触发方式: MANUAL / TRY_RUN / SCHEDULE / OPENAPI',
  `trigger_app_id`    BIGINT                DEFAULT NULL COMMENT 'OpenAPI 触发时的应用ID',
  `status`            VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELLED',
  `input_params`      JSON                  DEFAULT NULL COMMENT '入参',
  `output_result`     JSON                  DEFAULT NULL COMMENT '最终出参',
  `error_msg`         TEXT                  DEFAULT NULL COMMENT '失败原因',
  `trace_id`          VARCHAR(64)           DEFAULT NULL COMMENT '链路追踪ID',
  `start_time`        DATETIME              DEFAULT NULL COMMENT '开始时间',
  `end_time`          DATETIME              DEFAULT NULL COMMENT '结束时间',
  `duration_ms`       BIGINT                DEFAULT NULL COMMENT '耗时(毫秒)',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_execution_no` (`execution_no`),
  KEY `idx_workflow_status` (`workflow_id`, `status`),
  KEY `idx_trigger` (`trigger_type`, `trigger_app_id`),
  KEY `idx_trace_id` (`trace_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流执行实例';

-- -----------------------------------------------------------------------------
-- 6. 节点执行日志 (试运行/正式执行每一步的 HTTP 调用记录)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `qz_execution_node_log`;
CREATE TABLE `qz_execution_node_log` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `execution_id`      BIGINT       NOT NULL COMMENT '执行实例ID',
  `node_id`           VARCHAR(64)  NOT NULL COMMENT '画布节点ID',
  `node_name`         VARCHAR(128)          DEFAULT NULL COMMENT '节点名称',
  `component_id`      BIGINT                DEFAULT NULL COMMENT '接口组件ID',
  `component_code`    VARCHAR(64)           DEFAULT NULL COMMENT '组件编码(冗余，便于检索)',
  `status`            VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/TIMEOUT',
  `request_url`       VARCHAR(1024)         DEFAULT NULL COMMENT '实际请求 URL',
  `request_method`    VARCHAR(10)           DEFAULT NULL COMMENT 'HTTP 方法',
  `request_headers`   JSON                  DEFAULT NULL COMMENT '请求头(脱敏后)',
  `request_body`      JSON                  DEFAULT NULL COMMENT '请求体',
  `response_status`   INT                   DEFAULT NULL COMMENT 'HTTP 状态码',
  `response_body`     JSON                  DEFAULT NULL COMMENT '响应体',
  `retry_count`       INT          NOT NULL DEFAULT 0 COMMENT '实际重试次数',
  `error_msg`         TEXT                  DEFAULT NULL COMMENT '失败原因(超时/4xx/5xx/业务码)',
  `start_time`        DATETIME              DEFAULT NULL COMMENT '开始时间',
  `end_time`          DATETIME              DEFAULT NULL COMMENT '结束时间',
  `duration_ms`       BIGINT                DEFAULT NULL COMMENT '耗时(毫秒)',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_execution_id` (`execution_id`),
  KEY `idx_execution_node` (`execution_id`, `node_id`),
  KEY `idx_component_code` (`component_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='节点执行日志';

-- -----------------------------------------------------------------------------
-- 7. OpenAPI 应用 (开放平台调用方)
--    app_secret_cipher 为【加密存储点】
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `qz_openapi_app`;
CREATE TABLE `qz_openapi_app` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `app_name`            VARCHAR(128) NOT NULL COMMENT '应用名称',
  `app_key`             VARCHAR(64)  NOT NULL COMMENT 'API Key，请求头标识调用方',
  `app_secret_cipher`   VARCHAR(512) NOT NULL COMMENT '【加密存储点】HMAC 签名密钥，AES-GCM 密文，明文仅创建时回显一次',
  `status`              TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-停用',
  `rate_limit_qps`      INT          NOT NULL DEFAULT 10 COMMENT 'QPS 限流',
  `ip_whitelist`        JSON                  DEFAULT NULL COMMENT 'IP 白名单，空表示不限制',
  `expire_time`         DATETIME              DEFAULT NULL COMMENT '过期时间，空表示永不过期',
  `remark`              VARCHAR(256)          DEFAULT NULL COMMENT '备注',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by`           VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `update_by`           VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_key` (`app_key`, `deleted`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OpenAPI 应用';

-- -----------------------------------------------------------------------------
-- 8. OpenAPI 应用授权工作流 (一个应用可调用哪些工作流)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `qz_openapi_app_workflow`;
CREATE TABLE `qz_openapi_app_workflow` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `app_id`            BIGINT       NOT NULL COMMENT 'OpenAPI 应用ID',
  `workflow_id`       BIGINT       NOT NULL COMMENT '工作流ID',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  `create_by`         VARCHAR(64)           DEFAULT NULL COMMENT '授权人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_workflow` (`app_id`, `workflow_id`),
  KEY `idx_workflow_id` (`workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OpenAPI 应用授权工作流';

-- -----------------------------------------------------------------------------
-- 9. 调度任务映射 (对接 XXL-JOB 内核)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `qz_schedule_job`;
CREATE TABLE `qz_schedule_job` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workflow_id`       BIGINT       NOT NULL COMMENT '工作流ID',
  `job_name`          VARCHAR(128) NOT NULL COMMENT '任务名称',
  `schedule_type`     VARCHAR(16)  NOT NULL DEFAULT 'CRON' COMMENT 'CRON|INTERVAL|DAILY|WEEKLY|ONCE',
  `cron_expr`         VARCHAR(64)           DEFAULT NULL COMMENT 'Cron 表达式',
  `interval_seconds`  INT                   DEFAULT NULL COMMENT 'INTERVAL 模式间隔秒数',
  `fire_at`           DATETIME              DEFAULT NULL COMMENT 'ONCE 模式触发时间',
  `daily_time`        VARCHAR(8)            DEFAULT NULL COMMENT 'DAILY/WEEKLY 时刻 HH:mm',
  `week_days`         VARCHAR(32)           DEFAULT NULL COMMENT 'WEEKLY 星期 1-7 逗号分隔',
  `trigger_input`     JSON                  DEFAULT NULL COMMENT '触发时传入工作流的参数',
  `xxl_job_id`        INT                   DEFAULT NULL COMMENT 'XXL-JOB admin 中的任务ID',
  `executor_handler`  VARCHAR(64)  NOT NULL DEFAULT 'qingzhouWorkflowHandler' COMMENT '执行器 Handler',
  `status`            TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0-停止 1-运行',
  `last_fire_time`    DATETIME              DEFAULT NULL COMMENT '上次触发时间',
  `next_fire_time`    DATETIME              DEFAULT NULL COMMENT '下次触发时间',
  `remark`            VARCHAR(256)          DEFAULT NULL COMMENT '备注',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_workflow_id` (`workflow_id`),
  KEY `idx_xxl_job_id` (`xxl_job_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调度任务映射(XXL-JOB)';
