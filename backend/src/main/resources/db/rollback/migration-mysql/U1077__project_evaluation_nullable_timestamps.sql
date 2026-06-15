-- Input: V1077__project_evaluation_nullable_timestamps.sql
-- 回滚：恢复 timestamp 列为 NOT NULL DEFAULT CURRENT_TIMESTAMP。
-- 注意：回滚会把当前 NULL 值填充为 CURRENT_TIMESTAMP，可能丢失业务精确性。
UPDATE project_evaluation SET board_received_at = CURRENT_TIMESTAMP WHERE board_received_at IS NULL;
UPDATE project_evaluation SET announced_at = CURRENT_TIMESTAMP WHERE announced_at IS NULL;

ALTER TABLE project_evaluation MODIFY COLUMN board_received_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE project_evaluation MODIFY COLUMN announced_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;
