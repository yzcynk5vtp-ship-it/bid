-- Input: V1081__remove_task_executor_role.sql
-- Data rollback required: 恢复 task_executor 角色
INSERT INTO roles (code, name, description, is_active, is_system, scope, created_at, updated_at)
VALUES ('task_executor', '任务执行人', '标书任务承接与执行', true, true, 'self', NOW(), NOW());
