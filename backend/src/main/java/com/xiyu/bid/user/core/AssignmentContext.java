// Input: 业务场景类型 + 可选预过滤条件
// Output: 不可变的上下文值对象
// Pos: Core/纯核心（无 Spring 依赖）
// 维护声明: 纯值对象，不依赖框架 / IO / 状态；AssignmentCandidateAppService 据此编排调用链。
package com.xiyu.bid.user.core;

/**
 * 分配候选人查询上下文（Pure Core — no Spring dependencies）。
 *
 * <p>contextType 标识业务场景（"task" 或 "tender"），deptCode / roleCode
 * 为可选的预过滤参数，允许调用方在编排前收窄候选范围。
 */
public record AssignmentContext(
        String contextType,
        String deptCode,
        String roleCode
) {

    public static AssignmentContext of(String contextType, String deptCode, String roleCode) {
        return new AssignmentContext(contextType, deptCode, roleCode);
    }

    public boolean isValidContextType() {
        return "task".equalsIgnoreCase(contextType) || "tender".equalsIgnoreCase(contextType);
    }
}
