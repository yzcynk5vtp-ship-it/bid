-- U1065: Rollback personnel extensions
-- PR: #fix-ci-gaps-0609 补齐回滚脚本

DROP TABLE IF EXISTS personnel_operation_log;

ALTER TABLE personnel_education DROP COLUMN IF EXISTS is_highest_education_school;
ALTER TABLE personnel_certificate DROP COLUMN IF EXISTS is_permanent;
ALTER TABLE personnel_certificate DROP COLUMN IF EXISTS title;
ALTER TABLE personnel DROP COLUMN IF EXISTS birth_date;
