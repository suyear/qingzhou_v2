-- 旧库升级脚本：给 qz_schedule_job 增加调度类型与触发入参。
-- 仅在「当前表还没有这些列」时执行。新库若已跑过含这些列的 V1，请跳过本文件。
-- 扩展调度任务：支持固定间隔 / 每天 / 每周 / 一次性，以及触发入参
ALTER TABLE `qz_schedule_job`
  ADD COLUMN `schedule_type`   VARCHAR(16)  NOT NULL DEFAULT 'CRON' COMMENT 'CRON|INTERVAL|DAILY|WEEKLY|ONCE' AFTER `job_name`,
  ADD COLUMN `interval_seconds` INT                  DEFAULT NULL COMMENT 'INTERVAL 模式间隔秒数' AFTER `cron_expr`,
  ADD COLUMN `fire_at`         DATETIME              DEFAULT NULL COMMENT 'ONCE 模式触发时间' AFTER `interval_seconds`,
  ADD COLUMN `daily_time`      VARCHAR(8)            DEFAULT NULL COMMENT 'DAILY/WEEKLY 时刻 HH:mm' AFTER `fire_at`,
  ADD COLUMN `week_days`       VARCHAR(32)           DEFAULT NULL COMMENT 'WEEKLY 星期 1-7 逗号分隔' AFTER `daily_time`,
  ADD COLUMN `trigger_input`   JSON                  DEFAULT NULL COMMENT '触发时传入工作流的参数' AFTER `week_days`,
  MODIFY COLUMN `cron_expr`    VARCHAR(64)           DEFAULT NULL COMMENT 'Cron 表达式（CRON 模式或派生）';
