-- Input: V1095__add_users_employee_number.sql
-- Rollback: 移除 users 表 employee_number 列与索引。
-- MySQL 8.0 不支持 DROP INDEX IF EXISTS / DROP COLUMN IF EXISTS；
-- 回滚脚本也做 information_schema 判断，兼容半迁移状态。

DROP PROCEDURE IF EXISTS p_u1095_drop_index_if_exists;
DELIMITER $$
CREATE PROCEDURE p_u1095_drop_index_if_exists()
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND INDEX_NAME = 'idx_users_employee_number'
  ) THEN
    DROP INDEX idx_users_employee_number ON users;
  END IF;
END$$
DELIMITER ;

CALL p_u1095_drop_index_if_exists();
DROP PROCEDURE IF EXISTS p_u1095_drop_index_if_exists;

DROP PROCEDURE IF EXISTS p_u1095_drop_col_if_exists;
DELIMITER $$
CREATE PROCEDURE p_u1095_drop_col_if_exists()
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'employee_number'
  ) THEN
    ALTER TABLE users DROP COLUMN employee_number;
  END IF;
END$$
DELIMITER ;

CALL p_u1095_drop_col_if_exists();
DROP PROCEDURE IF EXISTS p_u1095_drop_col_if_exists;
