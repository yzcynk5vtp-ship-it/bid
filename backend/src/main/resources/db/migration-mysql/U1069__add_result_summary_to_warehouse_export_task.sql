-- U1069: Rollback warehouse_export_task result_summary column
-- PR: #fix-ci-gaps-0609 补齐回滚脚本

ALTER TABLE warehouse_export_task DROP COLUMN IF EXISTS result_summary;
