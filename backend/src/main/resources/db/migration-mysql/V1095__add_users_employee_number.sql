-- V1095: 补齐 users 表 employee_number 列
-- 修复 User.java 实体定义了 employee_number 但迁移脚本中无对应列的 schema 漂移
-- 该字段被 UserSearchService.UserSearchResult 和 TaskAssignmentCandidateDTO 使用
-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS。
-- 生产曾出现 employee_number 列已存在但索引缺失、Flyway 记录失败的半迁移状态，
-- 因此这里用 information_schema 前置判断保证 repair 后重跑可补齐缺口。

DROP PROCEDURE IF EXISTS p_v1095_add_col_if_missing;
DELIMITER $$
CREATE PROCEDURE p_v1095_add_col_if_missing()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'employee_number'
  ) THEN
    ALTER TABLE users ADD COLUMN employee_number VARCHAR(32) NULL;
  END IF;
END$$
DELIMITER ;

CALL p_v1095_add_col_if_missing();
DROP PROCEDURE IF EXISTS p_v1095_add_col_if_missing;

DROP PROCEDURE IF EXISTS p_v1095_add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE p_v1095_add_index_if_missing()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND INDEX_NAME = 'idx_users_employee_number'
  ) THEN
    CREATE INDEX idx_users_employee_number ON users(employee_number);
  END IF;
END$$
DELIMITER ;

CALL p_v1095_add_index_if_missing();
DROP PROCEDURE IF EXISTS p_v1095_add_index_if_missing;
