-- Input: migration-mysql/V1120__expand_tender_info_capacity_to_20000.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1120: 回滚 V1120 — 恢复 tenders.tender_info VARCHAR(5000) + 蓝图 maxLength 5000
--
-- 数据影响：
-- - TEXT → VARCHAR(5000)：超过 5000 字的 tender_info 值会被截断（数据丢失）
-- - 蓝图 maxLength 20000 → 5000：仅影响前端校验，不影响已存数据
-- - 回滚前请确认没有 tender_info 值超过 5000 字，否则请先备份或手动截断
--
-- 注意：本回滚脚本仅恢复列类型和蓝图配置，不恢复被截断的数据。
ALTER TABLE tenders
    MODIFY COLUMN tender_info VARCHAR(5000) NULL COMMENT '标讯信息';

UPDATE form_definition_registry
SET schema_json = REPLACE(
    schema_json,
    '"key":"tenderInfo","label":"标讯信息","type":"TEXTAREA","required":false,"rows":3,"maxLength":20000',
    '"key":"tenderInfo","label":"标讯信息","type":"TEXTAREA","required":false,"rows":3,"maxLength":5000'
)
WHERE scope = 'tender.entry' AND org_id IS NULL;
