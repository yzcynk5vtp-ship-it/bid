-- U1064: Rollback certificate_no unique index
-- PR: #fix-ci-gaps-0609 补齐回滚脚本
-- 数据清理（空字符串→NULL、重复值→NULL）不可逆，仅恢复约束结构

DROP INDEX IF EXISTS uk_certificate_no ON business_qualifications;
