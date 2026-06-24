-- U1095: 回滚 users 表 employee_number 列
DROP INDEX idx_users_employee_number ON users;
ALTER TABLE users DROP COLUMN employee_number;
