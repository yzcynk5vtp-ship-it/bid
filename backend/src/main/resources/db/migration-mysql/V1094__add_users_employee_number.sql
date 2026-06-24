-- V1094: 补齐 users 表 employee_number 列
-- 修复 User.java 实体定义了 employee_number 但迁移脚本中无对应列的 schema 漂移
-- 该字段被 UserSearchService.UserSearchResult 和 TaskAssignmentCandidateDTO 使用

ALTER TABLE users ADD COLUMN employee_number VARCHAR(32) NULL;
CREATE INDEX idx_users_employee_number ON users(employee_number);
