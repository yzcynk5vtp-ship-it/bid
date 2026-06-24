-- Input: V1095__add_users_employee_number.sql
-- Rollback: 移除 users 表 employee_number 列与索引。

DROP INDEX idx_users_employee_number ON users;
ALTER TABLE users DROP COLUMN employee_number;
