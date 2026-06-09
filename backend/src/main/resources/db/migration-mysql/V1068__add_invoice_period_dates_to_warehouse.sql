-- V1068__add_invoice_period_dates_to_warehouse.sql
-- PR: (待补)
-- 蓝图追溯: 4.4.1.4 批量导入导出 - 发票租期起/止拆分为独立日期字段
-- 将 invoice_period (varchar) 拆分为 invoice_period_start / invoice_period_end (date)
ALTER TABLE warehouse
    ADD COLUMN invoice_period_start DATE NULL COMMENT '发票租期开始日期' AFTER invoice_period,
    ADD COLUMN invoice_period_end DATE NULL COMMENT '发票租期结束日期' AFTER invoice_period_start;
