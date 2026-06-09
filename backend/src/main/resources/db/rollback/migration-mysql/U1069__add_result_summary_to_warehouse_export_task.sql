-- U1069__add_result_summary_to_warehouse_export_task.sql
-- Input: V1069__add_result_summary_to_warehouse_export_task.sql
-- Rollback for V1069__add_result_summary_to_warehouse_export_task.sql
-- PR: !391

-- No-op rollback: dropping result_summary column removes export summary JSON without further risk
ALTER TABLE warehouse_export_task
    DROP COLUMN IF EXISTS result_summary;
