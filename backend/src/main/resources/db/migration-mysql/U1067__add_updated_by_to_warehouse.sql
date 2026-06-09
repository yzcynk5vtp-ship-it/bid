-- U1067: Rollback warehouse updated_by field
-- PR: #fix-ci-gaps-0609 补齐回滚脚本

ALTER TABLE warehouse DROP COLUMN IF EXISTS updated_by;
