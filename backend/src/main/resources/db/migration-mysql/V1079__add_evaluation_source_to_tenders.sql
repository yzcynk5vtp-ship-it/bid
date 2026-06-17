-- CO-232: 评估表数据来源标记，区分 CRM 推送 vs 投标系统主动关联
ALTER TABLE tenders
    ADD COLUMN evaluation_source VARCHAR(20) DEFAULT NULL COMMENT '评估表数据来源: CRM_PUSH/BID_SYSTEM_LINK'
    AFTER crm_opportunity_id;
