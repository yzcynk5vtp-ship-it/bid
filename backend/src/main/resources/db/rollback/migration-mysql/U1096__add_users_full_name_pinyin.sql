-- Input: V1096__add_users_full_name_pinyin.sql
-- Rollback: 移除 users 表 full_name_pinyin 列。
-- MySQL 8.0 不支持 DROP COLUMN IF EXISTS；
-- 回滚脚本也做 information_schema 判断，兼容半迁移状态。

DROP PROCEDURE IF EXISTS p_u1096_drop_col_if_exists;
DELIMITER $$
CREATE PROCEDURE p_u1096_drop_col_if_exists()
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'full_name_pinyin'
  ) THEN
    ALTER TABLE users DROP COLUMN full_name_pinyin;
  END IF;
END$$
DELIMITER ;

CALL p_u1096_drop_col_if_exists();
DROP PROCEDURE IF EXISTS p_u1096_drop_col_if_exists;