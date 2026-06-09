-- U1068__add_invoice_period_dates_to_warehouse.sql
-- PR: (待补)
-- 回滚: 移除 invoice_period_start / invoice_period_end 列，恢复 invoice_period 原有状态
ALTER TABLE warehouse
    DROP COLUMN IF EXISTS invoice_period_start,
    DROP COLUMN IF EXISTS invoice_period_end;
