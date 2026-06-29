package com.xiyu.bid.project.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import org.junit.jupiter.api.Test;

/**
 * ClosureReviewAuthorizationPolicy 独立单元测试（纯核心，无 Spring/Mockito）。
 * 覆盖 CO-403 纠偏后的结项审核权矩阵：全局审核角色直接放行、投标辅助项目级匹配、
 * 职责分离（投标项目负责人作为提交人不得审核）。
 */
class ClosureReviewAuthorizationPolicyTest {

    private static ProjectLeadAssignment lead(Long primary, Long secondary) {
        return ProjectLeadAssignment.builder()
                .id(1L).projectId(100L)
                .primaryLeadUserId(primary)
                .secondaryLeadUserId(secondary)
                .build();
    }

    // ── 全局审核角色直接放行（admin/投标管理员/投标组长）──────────────────────

    @Test
    void canReviewClosure_admin_directPermit_noLeadNeeded() {
        var d = ClosureReviewAuthorizationPolicy.canReviewClosure("admin", 1L, null);
        assertThat(d.allowed()).isTrue();
        assertThat(d.cause()).isNull();
        assertThat(d.reason()).isNull();
    }

    @Test
    void canReviewClosure_bidAdmin_directPermit() {
        var d = ClosureReviewAuthorizationPolicy.canReviewClosure("/bidAdmin", 1L, null);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void canReviewClosure_bidTeamLeader_directPermit() {
        var d = ClosureReviewAuthorizationPolicy.canReviewClosure("bid-TeamLeader", 1L, null);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void canReviewClosure_admin_directPermit_evenIfLeadDoesNotMatch() {
        // 全局审核角色不查 lead，即使 lead 分配给别人也放行
        var d = ClosureReviewAuthorizationPolicy.canReviewClosure("admin", 1L, lead(99L, 88L));
        assertThat(d.allowed()).isTrue();
    }

    // ── 职责分离：投标项目负责人(提交人)不可审核 ──────────────────────────────

    @Test
    void canReviewClosure_sales_submitter_cannotReview_selfSeperation() {
        // 投标项目负责人是结项提交人，即使被分配为该项目 lead 也不得审核自己提交的结项
        var d = ClosureReviewAuthorizationPolicy.canReviewClosure("bid-projectLeader", 1L, lead(1L, 2L));
        assertThat(d.allowed()).isFalse();
        assertThat(d.cause()).isEqualTo(ClosureReviewAuthorizationPolicy.Decision.Cause.IDENTITY);
        assertThat(d.reason()).contains("不可审核自己提交的结项");
    }

    @Test
    void canReviewClosure_sales_submitter_denied_evenIfNotLead() {
        var d = ClosureReviewAuthorizationPolicy.canReviewClosure("bid-projectLeader", 1L, lead(2L, 3L));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).contains("不可审核自己提交的结项");
    }

    // ── 投标辅助(bid-Team)：须匹配项目级 primaryLead 或 secondaryLead ─────────

    @Test
    void canReviewClosure_bidSpecialist_asSecondaryLead_allowed() {
        var d = ClosureReviewAuthorizationPolicy.canReviewClosure("bid-Team", 1L, lead(2L, 1L));
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void canReviewClosure_bidSpecialist_asPrimaryLead_allowed() {
        var d = ClosureReviewAuthorizationPolicy.canReviewClosure("bid-Team", 1L, lead(1L, 2L));
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void canReviewClosure_bidSpecialist_notLead_denied() {
        var d = ClosureReviewAuthorizationPolicy.canReviewClosure("bid-Team", 1L, lead(2L, 3L));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).contains("不是该项目的投标负责人或投标辅助");
    }

    @Test
    void canReviewClosure_bidSpecialist_leadNull_denied() {
        var d = ClosureReviewAuthorizationPolicy.canReviewClosure("bid-Team", 1L, null);
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).contains("不是该项目的投标负责人或投标辅助");
    }

    @Test
    void canReviewClosure_bidSpecialist_leadFieldsNull_denied() {
        var d = ClosureReviewAuthorizationPolicy.canReviewClosure("bid-Team", 1L, lead(null, null));
        assertThat(d.allowed()).isFalse();
    }

    // ── 其他角色 / 边界 ───────────────────────────────────────────────────────

    @Test
    void canReviewClosure_adminStaff_notInWhitelist_denied() {
        var d = ClosureReviewAuthorizationPolicy.canReviewClosure("bid-administration", 1L, lead(1L, 2L));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).contains("当前角色无权审核结项");
    }

    @Test
    void canReviewClosure_bidOtherDept_notInWhitelist_denied() {
        var d = ClosureReviewAuthorizationPolicy.canReviewClosure("bid-otherDept", 1L, lead(1L, 2L));
        assertThat(d.allowed()).isFalse();
    }

    @Test
    void canReviewClosure_nullRole_denied_identity() {
        var d = ClosureReviewAuthorizationPolicy.canReviewClosure(null, 1L, lead(1L, 2L));
        assertThat(d.allowed()).isFalse();
        assertThat(d.cause()).isEqualTo(ClosureReviewAuthorizationPolicy.Decision.Cause.IDENTITY);
    }

    // ── Decision 值对象语义 ──────────────────────────────────────────────────

    @Test
    void decision_deny_hasCauseAndReason() {
        var d = ClosureReviewAuthorizationPolicy.canReviewClosure("bid-administration", 1L, null);
        assertThat(d.allowed()).isFalse();
        assertThat(d.cause()).isEqualTo(ClosureReviewAuthorizationPolicy.Decision.Cause.IDENTITY);
        assertThat(d.reason()).isNotBlank();
    }

    @Test
    void decision_causeEnum_hasIdentityAndState() {
        var causes = ClosureReviewAuthorizationPolicy.Decision.Cause.values();
        assertThat(causes).contains(
                ClosureReviewAuthorizationPolicy.Decision.Cause.IDENTITY,
                ClosureReviewAuthorizationPolicy.Decision.Cause.STATE);
    }
}
