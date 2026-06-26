-- Input: migration-mysql/V1027__widen_evaluation_role_key.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- V1027: MODIFY COLUMN role_key VARCHAR(50) → VARCHAR(30)
-- Rollback: 改回 VARCHAR(30)（注意：如果原列有数据可能丢失，需提前备份）
ALTER TABLE tender_evaluation_customer_info
    MODIFY COLUMN role_key VARCHAR(30) NOT NULL COMMENT '角色枚举键';
