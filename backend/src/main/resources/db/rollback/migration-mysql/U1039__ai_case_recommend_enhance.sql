-- Input: migration-mysql/V1039__ai_case_recommend_enhance.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- V1039: 为 knowledge_case 添加 bid_result, scoring_category 列及索引
-- Rollback: 删除添加的列和索引（注意：已有数据会丢失，需提前备份）
ALTER TABLE knowledge_case
    DROP INDEX idx_knowledge_case_category,
    DROP INDEX idx_knowledge_case_bid_result,
    DROP COLUMN IF EXISTS scoring_category,
    DROP COLUMN IF EXISTS bid_result;
