-- Input: V1111__add_pending_approval_to_platform_accounts_status.sql
-- U1111: Rollback - 恢复 platform_accounts.status enum 为 4 个值
-- 关联: CO-386
-- 注意: 原 U1110 已重命名为 U1111（与 V1111 配套，因 V1110 与 !1340 撞号修复）
-- 注意:
--   1. 回滚前必须先把所有 status='PENDING_APPROVAL' 的行 UPDATE 回 'AVAILABLE'，
--      否则 MODIFY COLUMN 会因数据被截断而失败
--   2. 回滚后 Java 实体 AccountStatus.PENDING_APPROVAL 仍存在，调用 markPendingApproval()
--      会再次触发 "Data truncated" 异常。回滚必须同时回退应用代码到 !1338 之前的版本

-- Step 1: 把 PENDING_APPROVAL 状态的账号全部还原为 AVAILABLE
UPDATE platform_accounts
   SET status = 'AVAILABLE',
       borrowed_by = NULL,
       borrowed_at = NULL,
       due_at = NULL
 WHERE status = 'PENDING_APPROVAL';

-- Step 2: 恢复 status enum 为 4 个值（与 B73 baseline 一致）
ALTER TABLE platform_accounts
  MODIFY COLUMN status
  ENUM('AVAILABLE','IN_USE','MAINTENANCE','DISABLED')
  NOT NULL;
