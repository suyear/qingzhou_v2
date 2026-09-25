-- P1：凭证类型扩展为 WECOM / HTTP_AUTH / DATABASE；secret 支持 mTLS PEM。
-- 兼容旧值 CUSTOM（视为 HTTP_AUTH+bearer）、MYSQL（视为 DATABASE+mysql）。
-- 新库在 V4 之后执行；已有库直接执行本脚本即可。

ALTER TABLE `qz_credential`
  MODIFY COLUMN `credential_type` VARCHAR(32) NOT NULL COMMENT '凭证类型: WECOM / HTTP_AUTH / DATABASE（兼容 CUSTOM / MYSQL）',
  MODIFY COLUMN `secret_cipher`   TEXT DEFAULT NULL COMMENT '【加密存储点】单密钥明文密文，或多密钥 secrets JSON 的 AES-GCM 密文';
