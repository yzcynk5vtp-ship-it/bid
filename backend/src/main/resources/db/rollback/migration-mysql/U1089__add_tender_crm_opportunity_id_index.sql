-- Input: V1089__add_tender_crm_opportunity_id_index.sql
-- CO-297: 回滚 V1089 — 把 crm_opportunity_id 的 UNIQUE 索引退回到普通 INDEX。
-- 应用层去重仍由 TenderCrmOccupancyChecker 在写入前检查；
-- schema 层面回滚到 V1006 后的状态（普通 INDEX）。

DROP INDEX idx_tender_crm_opportunity_id ON tenders;
CREATE INDEX idx_tender_crm_opportunity_id ON tenders (crm_opportunity_id);
