-- ================================================================
-- V1120: 扩展 tenderInfo 字段容量以支持完整招标公告原文（≤20000字）
-- 背景：AI 识别标讯时需要把完整招标公告原文回填到 tenderInfo 字段，
--       当前 VARCHAR(5000) 容量不足，需升级到 TEXT（支持 65535 字节）。
--       同步更新 form_definition_registry 中 tender.entry 蓝图配置的 maxLength。
-- 说明：
--   1. ALTER TABLE 修改 tenders.tender_info 列类型 VARCHAR(5000) → TEXT
--   2. UPDATE form_definition_registry 更新蓝图配置中 tenderInfo.maxLength 5000 → 20000
-- 数据影响：
--   - VARCHAR(5000) → TEXT：现有数据自动迁移，无数据丢失
--   - 蓝图配置 maxLength 5000 → 20000：仅影响前端校验，不影响已存数据
-- ================================================================

-- ----------------------------------------------------------
-- 1. 扩展 tenders.tender_info 列类型 VARCHAR(5000) → TEXT
-- ----------------------------------------------------------
ALTER TABLE tenders
    MODIFY COLUMN tender_info TEXT NULL COMMENT '标讯信息';

-- ----------------------------------------------------------
-- 2. 更新 form_definition_registry 中 tender.entry 蓝图配置
--    将 tenderInfo 字段的 maxLength 从 5000 提升到 20000
-- ----------------------------------------------------------
UPDATE form_definition_registry
SET schema_json = REPLACE(
    schema_json,
    '"key":"tenderInfo","label":"标讯信息","type":"TEXTAREA","required":false,"rows":3,"maxLength":5000',
    '"key":"tenderInfo","label":"标讯信息","type":"TEXTAREA","required":false,"rows":3,"maxLength":20000'
)
WHERE scope = 'tender.entry' AND org_id IS NULL;
