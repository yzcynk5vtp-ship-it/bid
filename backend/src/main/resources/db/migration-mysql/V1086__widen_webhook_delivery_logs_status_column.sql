-- V1086: webhook_delivery_logs.status 列扩容 varchar(10) -> varchar(32)
-- 根因: WebhookDeliveryJobService 写日志时 status = decision.action().name(),
-- 可能值为 DEAD_LETTER(11)/SUCCEED_WITH_LOG(16)/FAIL_MAIN_TRANSACTION(21),
-- 超出 varchar(10) 导致 Data truncation,投递失败路径崩溃、task 卡 DEAD_LETTER。
-- 修复: 扩容到 varchar(32) 容纳所有动作枚举名。

ALTER TABLE webhook_delivery_logs MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
