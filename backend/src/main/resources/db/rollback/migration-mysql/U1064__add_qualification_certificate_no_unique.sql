-- PR #336: 回滚 certificate_no 唯一约束
ALTER TABLE business_qualifications
DROP INDEX IF EXISTS uk_certificate_no;
