-- V1098: 清理历史"【待立项】"占位任务 (CO-349)
-- 根因: TenderEvaluationService.proceedToBid 历史上调用 TenderBidTaskFactory.reuseOrCreate，
--       在投标立项时自动创建一个 title 以"【待立项】"开头、status=TODO 的占位任务。该任务在
--       前端被主动过滤不展示 (useProjectDetailInit/useProjectDetailDocumentActions)，却会卡住
--       "提交投标"的全任务完成闸门 (AllTasksCompletedPolicy)，导致 DRAFTING→EVALUATING 推进时
--       报"仍有 N 个任务未完成，无法提交投标"。
--       CO-349 已在代码层移除创建逻辑 (删除 TenderBidTaskFactory + proceedToBid 调用)，本脚本
--       清理存量数据库残留，恢复受影响项目 (如 project/56) 的提交投标能力。
-- 操作: 将所有 title LIKE '【待立项】%' 且 status='TODO' 的任务置为终态 CANCELLED。
--       显式 set updated_at (手动 UPDATE 绕过 JPA @UpdateTimestamp)。
-- Idempotency: WHERE status='TODO' 自排除，重跑为 no-op。
-- Backout: db/rollback/migration-mysql/U1098__cancel_legacy_pending_initiation_tasks.sql
-- PR: #<pending>

-- Pre-flight: 统计待清理条数，供迁移日志参考。
SELECT COUNT(*) AS pending_initiation_todo_before
  FROM tasks
 WHERE status = 'TODO'
   AND title LIKE '【待立项】%';

-- 清理：将历史"【待立项】"占位任务置为终态 CANCELLED。可安全重跑 (WHERE 自排除)。
UPDATE tasks
   SET status = 'CANCELLED',
       updated_at = NOW()
 WHERE status = 'TODO'
   AND title LIKE '【待立项】%';

-- Post-flight: 确认无 TODO 残留。若非零说明迁移异常，操作者应排查后再继续。
SELECT COUNT(*) AS remaining_pending_initiation_todo
  FROM tasks
 WHERE status = 'TODO'
   AND title LIKE '【待立项】%';
