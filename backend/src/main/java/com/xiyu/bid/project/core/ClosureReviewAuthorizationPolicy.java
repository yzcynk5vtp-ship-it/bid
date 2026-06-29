// Input: 当前操作者角色 code + 当前用户 ID + 提交人 ID + 项目级投标负责人分配
// Output: Decision(allowed/reason) — 纯函数，无副作用
// Pos: project/core/ - pure core policy, no Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;

import java.util.Set;

/**
 * 结项审核授权策略（CO-403 纠偏后的结项审核权矩阵）。
 * <p>纯核心：不依赖数据库、I/O、Spring 或日志。所有方法返回 {@link Decision} 值，
 * 编排层按 {@link Decision.Cause} 映射 HTTP 状态码（IDENTITY→403, STATE→409）。</p>
 *
 * <p>审核权矩阵：</p>
 * <ul>
 *   <li>直接放行（全局审核角色）：{@code admin}/{@code /bidAdmin}/{@code bid-TeamLeader}</li>
 *   <li>需项目级负责人匹配：投标辅助 {@code bid-Team}，须匹配
 *       {@code ProjectLeadAssignment.primaryLeadUserId} 或 {@code secondaryLeadUserId}</li>
 *   <li>职责分离：结项提交人（投标项目负责人 {@code bid-projectLeader}）不得审核自己提交的结项，
 *       故本策略对 {@code bid-projectLeader} 角色直接拒绝</li>
 *   <li>其他角色：直接拒绝</li>
 * </ul>
 *
 * <p>注意：{@code @PreAuthorize} 已在 Controller 层做角色白名单（admin/BID_TEAMLEADER/BIDADMIN/BID_TEAM），
 * 本策略负责"项目级投标辅助匹配 + 职责分离"这层细粒度校验。须与 Controller 白名单保持一致。</p>
 */
public final class ClosureReviewAuthorizationPolicy {

    /** 直接放行角色：不依赖项目级负责人分配。须与 Controller @PreAuthorize 白名单一致。 */
    private static final Set<String> DIRECT_REVIEW_ROLES = Set.of(
            RoleProfileCatalog.ADMIN_CODE,
            RoleProfileCatalog.BID_ADMIN_CODE,
            RoleProfileCatalog.BID_LEAD_CODE
    );

    private ClosureReviewAuthorizationPolicy() {
    }

    /**
     * 校验当前用户是否可以审核结项（通过/驳回）。
     *
     * @param roleCode      当前操作者角色 code（已规范化，可为 null）
     * @param currentUserId 当前操作者用户 ID
     * @param lead          项目级投标负责人分配（可为 null 表示尚未分配）
     * @return 允许或拒绝决定
     */
    public static Decision canReviewClosure(String roleCode, Long currentUserId, ProjectLeadAssignment lead) {
        if (roleCode == null) {
            return Decision.deny(Decision.Cause.IDENTITY, "无法识别当前用户角色");
        }
        // 全局审核角色直接放行（admin/投标管理员/投标组长）
        if (DIRECT_REVIEW_ROLES.contains(roleCode)) {
            return Decision.permit();
        }
        // 职责分离：投标项目负责人(bid-projectLeader)是结项提交人，不得审核
        if (RoleProfileCatalog.SALES_CODE.equalsIgnoreCase(roleCode)) {
            return Decision.deny(Decision.Cause.IDENTITY, "投标项目负责人作为结项提交人，不可审核自己提交的结项");
        }
        // 投标辅助(bid-Team)：须匹配该项目的 primaryLead 或 secondaryLead
        if (RoleProfileCatalog.BID_SPECIALIST_CODE.equalsIgnoreCase(roleCode)) {
            if (lead == null) {
                return Decision.deny(Decision.Cause.IDENTITY, "您不是该项目的投标负责人或投标辅助，无权审核结项");
            }
            boolean matched = (lead.getPrimaryLeadUserId() != null
                    && lead.getPrimaryLeadUserId().equals(currentUserId))
                    || (lead.getSecondaryLeadUserId() != null
                    && lead.getSecondaryLeadUserId().equals(currentUserId));
            return matched
                    ? Decision.permit()
                    : Decision.deny(Decision.Cause.IDENTITY, "您不是该项目的投标负责人或投标辅助，无权审核结项");
        }
        return Decision.deny(Decision.Cause.IDENTITY, "当前角色无权审核结项");
    }

    /**
     * 授权决策结果。
     *
     * <p>{@code cause} 供编排层映射 HTTP 状态码：</p>
     * <ul>
     *   <li>{@link Cause#STATE} → 409 Conflict</li>
     *   <li>{@link Cause#IDENTITY} → 403 Forbidden</li>
     * </ul>
     *
     * @param allowed 是否允许
     * @param cause   拒绝原因类型（allowed=true 时为 null）
     * @param reason  拒绝原因描述（allowed=true 时为 null）
     */
    public record Decision(boolean allowed, Cause cause, String reason) {

        public enum Cause {
            /** 资源状态机不允许该操作 */
            STATE,
            /** 操作者身份不符（角色无权 / 非项目指派负责人 / 职责分离拦截） */
            IDENTITY
        }

        public static Decision permit() {
            return new Decision(true, null, null);
        }

        public static Decision deny(Cause cause, String reason) {
            return new Decision(false, cause, reason);
        }
    }
}
