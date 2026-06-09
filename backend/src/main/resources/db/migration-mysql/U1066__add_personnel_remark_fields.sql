-- U1066: Rollback personnel remark fields
-- PR: #fix-ci-gaps-0609 补齐回滚脚本

ALTER TABLE personnel_certificate DROP COLUMN IF EXISTS remark;
ALTER TABLE personnel DROP COLUMN IF EXISTS remark;
