-- V120: Add employee_number to users table for CO-167
-- Supports "姓名（工号）" display format

ALTER TABLE users
    ADD COLUMN employee_number VARCHAR(32) DEFAULT NULL;
