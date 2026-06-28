-- 为 account_borrow_applications 增加关联项目与审批意见字段，并将历史 APPROVED 状态迁移为 BORROWED
ALTER TABLE account_borrow_applications
    ADD COLUMN project_id BIGINT NULL AFTER project_name,
    ADD COLUMN approval_comment VARCHAR(500) NULL AFTER reject_reason;

UPDATE account_borrow_applications
SET status = 'BORROWED'
WHERE status = 'APPROVED';
