package com.xiyu.bid.project.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link BidReadinessPolicy}.
 * No Spring context, no mocks — verifies ready/not-ready decisions.
 *
 * <p>CO-400 防复发覆盖：两个语义不同的方法必须各有正反例。</p>
 */
class BidReadinessPolicyTest {

    // ── checkBidSubmissionReady（submitBid 用：任务全完成 + 标书文件已上传）──

    @Test
    void checkBidSubmissionReady_permits_whenAllTasksCompleted_andHasBidDocument() {
        var decision = BidReadinessPolicy.checkBidSubmissionReady(
                List.of(AllTasksCompletedPolicy.TaskState.COMPLETED,
                        AllTasksCompletedPolicy.TaskState.COMPLETED),
                true);
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void checkBidSubmissionReady_denies_whenTasksIncomplete_evenIfHasDocument() {
        var decision = BidReadinessPolicy.checkBidSubmissionReady(
                List.of(AllTasksCompletedPolicy.TaskState.COMPLETED,
                        AllTasksCompletedPolicy.TaskState.TODO),
                true);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("1 个任务未完成");
    }

    @Test
    void checkBidSubmissionReady_denies_whenNoBidDocument_evenIfAllTasksCompleted() {
        var decision = BidReadinessPolicy.checkBidSubmissionReady(
                List.of(AllTasksCompletedPolicy.TaskState.COMPLETED),
                false);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("尚未上传标书文件");
    }

    @Test
    void checkBidSubmissionReady_denies_whenBothFailures_prefersTaskGatingMessage() {
        // 任务问题优先级高，先报告
        var decision = BidReadinessPolicy.checkBidSubmissionReady(
                List.of(AllTasksCompletedPolicy.TaskState.REVIEW),
                false);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("任务未完成");
    }

    @Test
    void checkBidSubmissionReady_permits_whenNoTasks_atAll() {
        var decision = BidReadinessPolicy.checkBidSubmissionReady(List.of(), true);
        assertThat(decision.allowed()).isTrue();
    }

    // ── checkBidDocumentUploaded（submitForReview 用：仅校验标书文件已上传）──

    @Test
    void checkBidDocumentUploaded_permits_whenHasBidDocument() {
        // 即使任务未完成，只要标书文件已上传就允许提交审核（CO-400 修复语义）
        var decision = BidReadinessPolicy.checkBidDocumentUploaded(true);
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void checkBidDocumentUploaded_denies_whenNoBidDocument() {
        var decision = BidReadinessPolicy.checkBidDocumentUploaded(false);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("尚未上传标书文件");
    }
}
