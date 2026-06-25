// Input: 任务状态列表 + 是否已上传标书文件
// Output: Decision(ready/reason) - 标书可提交审核/投标的纯核心闸门
// Pos: project/core/ - 纯规则，无 Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import java.util.List;

/**
 * PRD §3.2.3 标书编制提交闸门：所有任务已完成 + 标书文件已上传。
 * <p>纯核心：不依赖数据库、I/O、Spring 或日志。所有方法返回 {@link Decision} 值，
 * 编排层按 {@link Decision.Cause} 映射 HTTP 状态码（STATE→409）。</p>
 *
 * <p>本闸门同时被 {@code submitBid}（推进入评标）与 {@code submitForReview}（提交标书给审核人）
 * 两处业务入口复用，避免规则漂移。</p>
 */
public final class BidReadinessPolicy {

    /** 标书文件在项目文档表中的 documentCategory 取值。 */
    public static final String BID_DOCUMENT_CATEGORY = "BID_DOCUMENT";

    private BidReadinessPolicy() {
    }

    /**
     * 检查项目是否准备好提交标书（提交审核 / 提交投标通用）。
     *
     * @param taskStates     项目下所有任务的状态快照（来自 shell 层）
     * @param hasBidDocument 是否存在标书文件（{@code documentCategory=BID_DOCUMENT}）
     * @return 允许或拒绝决定 + 拒绝原因
     */
    public static Decision check(List<AllTasksCompletedPolicy.TaskState> taskStates,
                                  boolean hasBidDocument) {
        // 1. 所有任务都已终态完成
        AllTasksCompletedPolicy.Decision taskDecision = AllTasksCompletedPolicy.decide(taskStates);
        if (!taskDecision.allowed()) {
            int incomplete = ((AllTasksCompletedPolicy.Decision.Deny) taskDecision).incompleteCount();
            return Decision.deny(Decision.Cause.STATE,
                    "仍有 " + incomplete + " 个任务未完成");
        }

        // 2. 已上传标书文件
        if (!hasBidDocument) {
            return Decision.deny(Decision.Cause.STATE, "尚未上传标书文件");
        }

        return Decision.permit();
    }

    /**
     * 闸门决策结果。
     */
    public record Decision(boolean allowed, Cause cause, String reason) {

        public enum Cause {
            STATE
        }

        public static Decision permit() {
            return new Decision(true, null, null);
        }

        public static Decision deny(Cause cause, String reason) {
            return new Decision(false, cause, reason);
        }
    }
}