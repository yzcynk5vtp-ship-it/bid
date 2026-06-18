-- Input: migration-mysql/V1086__widen_webhook_delivery_logs_status_column.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.
-- U1086: 回滚 webhook_delivery_logs.status 到 varchar(10)
-- 注意: 回滚前若已有 status 值长度 > 10 的记录,需先清理,否则 ALTER 会失败。
ALTER TABLE webhook_delivery_logs MODIFY COLUMN status VARCHAR(10) NOT NULL DEFAULT 'PENDING';
