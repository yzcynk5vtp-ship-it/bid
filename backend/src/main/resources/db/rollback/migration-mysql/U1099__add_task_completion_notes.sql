-- Input: V1099__add_task_completion_notes.sql
-- Rollback for V1099__add_task_completion_notes.sql
-- 撤销 V1099：删除 tasks 表的 completion_notes 列，回退到 V1098 后的状态。
-- 注：回滚后 completion_notes 数据丢失，需重新跑 V1099 才能恢复。
-- 幂等：用 information_schema 前置判断。

DROP PROCEDURE IF EXISTS p_u1099_drop_col_if_exists;
DELIMITER $$
CREATE PROCEDURE p_u1099_drop_col_if_exists()
BEGIN
  IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'tasks'
      AND COLUMN_NAME = 'completion_notes'
  ) THEN
    ALTER TABLE tasks DROP COLUMN completion_notes;
  END IF;
END$$
DELIMITER ;

CALL p_u1099_drop_col_if_exists();
DROP PROCEDURE IF EXISTS p_u1099_drop_col_if_exists;
