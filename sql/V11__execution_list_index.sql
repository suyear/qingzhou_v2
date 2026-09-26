-- =============================================================================
-- V11：执行列表按工作流 + 创建时间倒序（运行结果页主查询）
-- 可重复执行：索引已存在时跳过
-- =============================================================================

USE `qingzhou`;

SET @idx_exists := (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'qz_execution_instance'
    AND index_name = 'idx_workflow_create_time'
);

SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `qz_execution_instance` ADD KEY `idx_workflow_create_time` (`workflow_id`, `create_time`)',
  'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
