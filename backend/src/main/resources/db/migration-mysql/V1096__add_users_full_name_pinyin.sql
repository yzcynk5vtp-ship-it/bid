-- Migration V1096: add full_name_pinyin column to users for pinyin-based search
-- This enables searching users by pinyin (e.g. "zhangsan", "zhang") in
-- addition to name, username, and employee_number.
--
-- Idempotent pattern: uses a stored procedure to check information_schema
-- before ALTER TABLE, so re-running this migration is safe.

DELIMITER $$

DROP PROCEDURE IF EXISTS p_v1096_add_col_if_missing$$
CREATE PROCEDURE p_v1096_add_col_if_missing()
BEGIN
    DECLARE col_count INT;

    SELECT COUNT(*) INTO col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'users'
      AND COLUMN_NAME  = 'full_name_pinyin';

    IF col_count = 0 THEN
        ALTER TABLE users
            ADD COLUMN full_name_pinyin VARCHAR(255) DEFAULT NULL
            COMMENT '姓名拼音（全拼小写，空格分隔，用于搜索）'
            AFTER full_name;
    END IF;
END$$

CALL p_v1096_add_col_if_missing()$$
DROP PROCEDURE IF EXISTS p_v1096_add_col_if_missing$$

DELIMITER ;