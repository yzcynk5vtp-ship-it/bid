-- V1110: 扩展 platform_accounts.status enum 新增 PENDING_APPROVAL 值
-- 关联: CO-386 [资源-账户管理] 账户借用申请全流程功能
-- 背景: B73 baseline 中 platform_accounts.status 定义为
--       enum('AVAILABLE','IN_USE','MAINTENANCE','DISABLED')（仅 4 个值）
--       CO-386 在 Java 实体 AccountStatus 中新增了 PENDING_APPROVAL（审批中），
--       用于借用申请提交后、审批通过前的中间状态，但漏配 Flyway 迁移，
--       导致 markPendingApproval() 写入 'PENDING_APPROVAL' 时 MySQL 报
--       "Data truncated for column 'status'" → HTTP 500
-- 修复策略: 仅扩展 enum 取值列表，不删除任何现有值，MySQL 8.0 INSTANT 操作
-- 风险: 低。新值是新增，不破坏现有数据；INSTANT 元数据操作，不锁表

ALTER TABLE platform_accounts
  MODIFY COLUMN status
  ENUM('AVAILABLE','PENDING_APPROVAL','IN_USE','MAINTENANCE','DISABLED')
  NOT NULL;
