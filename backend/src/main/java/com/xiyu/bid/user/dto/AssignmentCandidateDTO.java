package com.xiyu.bid.user.dto;

/**
 * 分配候选人 DTO（纯数据载体）。
 *
 * <p>用于 task / tender 两种业务场景下返回可分配的用户列表。
 * 查询时已过滤 enabled=true，因此 enabled 字段始终为 true。
 */
public record AssignmentCandidateDTO(
        Long userId,
        String name,
        String employeeNumber,
        String roleCode,
        String roleName,
        String deptCode,
        String deptName,
        Boolean enabled
) {
}
