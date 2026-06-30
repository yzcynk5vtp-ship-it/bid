-- Input: migration-mysql/V1117__fix_tenders_evaluation_source_to_enum.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1117: 回滚 V1117 — 恢复 tenders.evaluation_source VARCHAR(20)
ALTER TABLE tenders
    MODIFY COLUMN evaluation_source VARCHAR(20) DEFAULT NULL COMMENT '评估表数据来源: CRM_PUSH/BID_SYSTEM_LINK';
