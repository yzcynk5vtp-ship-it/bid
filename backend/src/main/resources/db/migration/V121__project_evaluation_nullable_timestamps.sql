-- PR: evaluation-404-fix
-- 修复 project_evaluation 表 timestamp 列不允许 NULL 且默认 zero-date，
-- 导致 JPA 保存/读取时报 "Zero date value prohibited" 的问题。
ALTER TABLE project_evaluation MODIFY COLUMN board_received_at timestamp NULL;
ALTER TABLE project_evaluation MODIFY COLUMN announced_at timestamp NULL;

-- 清理历史 zero-date 记录，避免查询时触发 zero date prohibited。
UPDATE project_evaluation SET board_received_at = NULL WHERE board_received_at = '0000-00-00 00:00:00';
UPDATE project_evaluation SET announced_at = NULL WHERE announced_at = '0000-00-00 00:00:00';
