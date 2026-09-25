-- 数据库接口组件：拓宽 URL/方法字段，凭证类型扩展为 MySQL 数据源。
-- 新库在 V1 之后执行；已有库直接执行本脚本即可（幂等：仅 MODIFY 已有列）。
ALTER TABLE `qz_api_component`
  MODIFY COLUMN `provider`     VARCHAR(32)  NOT NULL DEFAULT 'CUSTOM' COMMENT '提供方: WECOM / CUSTOM / DATABASE',
  MODIFY COLUMN `category`     VARCHAR(32)  NOT NULL DEFAULT 'HTTP' COMMENT '分类: MESSAGE / ORG / GROUP / HTTP / DATABASE',
  MODIFY COLUMN `http_method`  VARCHAR(16)  NOT NULL COMMENT 'HTTP 方法或 SQL 操作: GET/POST/PUT/DELETE/PATCH/QUERY/UPDATE',
  MODIFY COLUMN `url_template` TEXT         NOT NULL COMMENT 'HTTP URL 模板，或数据库组件的参数化 SQL';

ALTER TABLE `qz_credential`
  MODIFY COLUMN `credential_type` VARCHAR(32) NOT NULL COMMENT '凭证类型: WECOM / CUSTOM / MYSQL',
  MODIFY COLUMN `secret_cipher`   VARCHAR(512) DEFAULT NULL COMMENT '【加密存储点】CorpSecret/自定义Secret/数据库密码，AES-GCM 密文';
