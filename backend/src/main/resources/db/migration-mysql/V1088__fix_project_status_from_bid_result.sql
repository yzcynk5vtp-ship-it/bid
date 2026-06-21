-- V1088: 修复 projects.status 与 stage + result_type 不一致的历史数据
--
-- 根因：ProjectStageService.syncProjectStatus() 之前调用
-- ProjectStatusPolicy.compute(targetStage, null, true)
-- bidResult 传了 null，导致 RETROSPECTIVE/CLOSED 阶段的项目 status
-- 永远算成 BIDDING/INITIATED，而不是 WON/LOST/FAILED/ABANDONED。
--
-- 代码修复：requestTransition 新增 bidResult 参数重载。
-- 本脚本根据 project_result.result_type 修正历史数据。
-- 注意：project_initiation_details.bid_result_status 可能为 NULL（结果登记时未回填），
-- 因此以 project_result 表为准（结果登记的权威来源）。
--
-- Idempotent：可重复执行，只修复 status 不正确的记录。

UPDATE projects p
JOIN project_result pr ON pr.project_id = p.id
SET p.status = CASE pr.result_type
    WHEN 'WON' THEN 'WON'
    WHEN 'LOST' THEN 'LOST'
    WHEN 'FAILED' THEN 'FAILED'
    WHEN 'ABANDONED' THEN 'ABANDONED'
END
WHERE p.stage IN ('RETROSPECTIVE', 'CLOSED')
  AND p.status NOT IN ('WON', 'LOST', 'FAILED', 'ABANDONED');
