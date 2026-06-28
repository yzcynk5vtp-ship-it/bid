-- Input: V1107__account_borrow_application_project_and_comment.sql
-- 回滚 V1107：删除 project_id / approval_comment 字段，并将 BORROWED 恢复为 APPROVED
ALTER TABLE account_borrow_applications
    DROP COLUMN project_id,
    DROP COLUMN approval_comment;

UPDATE account_borrow_applications
SET status = 'APPROVED'
WHERE status = 'BORROWED';
