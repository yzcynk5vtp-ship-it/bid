package com.xiyu.bid.integration.organization.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 用户在职判定（OSS 接口字段语义）。
 * <p>以 {@code /oauth/getUserInfo} 接口返回的 {@code status} 字段为最权威标志
 * （{@code status=1}=在职→启用，{@code status=0}=离职→关闭）。
 * <ul>
 *   <li>{@code del=1} → 已删除（最高优先级）</li>
 *   <li>{@code status=1} → 在职（覆盖 employeeStatus/activationState）</li>
 *   <li>{@code status=0} → 离职（覆盖 employeeStatus=3 在职判定）</li>
 *   <li>{@code employeeStatus=3} → 在职（status 缺失时 fallback）</li>
 *   <li>{@code employeeStatus=8/1} → 离职 / 待入职</li>
 *   <li>{@code activationState=1} → 已激活</li>
 *   <li>缺省 → 视为启用（避免漏同步）</li>
 * </ul>
 * <p>历史教训：曾用 YAPI mock 数据编写，把 {@code status==1} 当作启用条件；
 * 实际泊冉生产环境 {@code /oauth/getUserInfo} 接口的 status 语义为
 * {@code status=1=在职}、{@code status=0=离职}（与代码注释中的"反向"描述不同，
 * 已通过生产接口样本确认）。2026-06-30 调整为 status 最高优先级（仅次 del）。
 */
final class UserEnabledDetector {

    private UserEnabledDetector() {
    }

    static boolean isEnabled(JsonNode node) {
        // 1. del=1 明确=已删（最高优先级，已删除的用户不应被视为在职）
        JsonNode del = node.path("del");
        if (del.isInt() && del.asInt() == 1) {
            return false;
        }
        // 2. status 字段为最高优先级判定（与 /oauth/getUserInfo 接口语义一致）
        //    status=1 → 在职（启用），status=0 → 离职（关闭）
        //    覆盖 employeeStatus/activationState，避免多字段语义冲突
        JsonNode status = node.path("status");
        if (status.isInt()) {
            return status.asInt() == 1;
        }
        // 3. employeeStatus（status 缺失时 fallback）
        JsonNode employeeStatus = node.path("employeeStatus");
        if (employeeStatus.isInt()) {
            int es = employeeStatus.asInt();
            if (es == 3) {
                return true;   // 在职
            }
            if (es == 8 || es == 1) {
                return false;  // 离职 / 待入职
            }
            // 其他状态码：尝试 fallback
        }
        // 4. activationState=1 = 已激活
        JsonNode activationState = node.path("activationState");
        if (activationState.isInt()) {
            return activationState.asInt() == 1;
        }
        // 5. 显式 boolean 字段
        JsonNode enabled = node.path("enabled");
        if (enabled.isBoolean()) {
            return enabled.asBoolean();
        }
        JsonNode disabled = node.path("disabled");
        if (disabled.isBoolean()) {
            return !disabled.asBoolean();
        }
        // 6. 兜底：未明确禁用 = 视为启用
        return true;
    }
}
