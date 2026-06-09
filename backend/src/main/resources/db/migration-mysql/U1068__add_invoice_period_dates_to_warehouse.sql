-- U1068: Rollback invoice_period_start/end columns
-- PR: #fix-ci-gaps-0609 补齐回滚脚本

ALTER TABLE warehouse
    DROP COLUMN IF EXISTS invoice_period_end,
    DROP COLUMN IF EXISTS invoice_period_start;
