-- V1039: 为 knowledge_case 增加中标结果和评分项类别字段，支持 AI 智能案例推荐的匹配度计算和筛选
ALTER TABLE knowledge_case
    ADD COLUMN bid_result VARCHAR(20) NULL COMMENT '中标结果: WON/LOST',
    ADD COLUMN scoring_category VARCHAR(50) NULL COMMENT '评分项类别: 技术/商务/实施/资质等',
    ADD INDEX idx_knowledge_case_category (scoring_category),
    ADD INDEX idx_knowledge_case_bid_result (bid_result);
