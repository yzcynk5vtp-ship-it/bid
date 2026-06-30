-- V1117: 修复 tenders.evaluation_source 列类型漂移
--
-- 背景：
-- - V1079 创建 evaluation_source VARCHAR(20)，但实体 Tender.evaluationSource 用
--   @Enumerated(EnumType.STRING) + enum EvaluationSource {CRM_PUSH, BID_SYSTEM_LINK}。
-- - Hibernate 6 + @Enumerated(EnumType.STRING) 在 ddl-auto=validate 时期望 MySQL ENUM 类型
--   （参考 V1113 修复模式与注释）。
-- - 当前 VARCHAR(20) 导致 Schema-validation 失败：found [varchar], expecting [enum]。
--
-- 修复策略：DB VARCHAR(20) → ENUM('CRM_PUSH','BID_SYSTEM_LINK')，与 V1113 同向。
--
-- 风险评估：
-- - 现有数据若为 NULL 或 'CRM_PUSH'/'BID_SYSTEM_LINK'，转换无风险。
-- - 若存在非法值（大小写不一致或脏数据），ALTER 会被 sql_mode 拦截；
--   生产执行前应先 SELECT DISTINCT evaluation_source FROM tenders 确认。
-- - MySQL 8.0 ENUM 修改是 INSTANT 元数据操作，不锁表。

ALTER TABLE tenders
    MODIFY COLUMN evaluation_source
    ENUM('CRM_PUSH','BID_SYSTEM_LINK') DEFAULT NULL COMMENT '评估表数据来源: CRM_PUSH/BID_SYSTEM_LINK';
