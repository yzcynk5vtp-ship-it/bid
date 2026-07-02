// Input: 角色码字符串
// Output: boolean — 是否为合法的项目转移新负责人角色
// Pos: project/core/ - 纯核心校验
// 维护声明: 纯函数，无副作用；角色集合来源于 RoleProfileCatalog.GLOBAL_ACCESS_ROLES + SALES_CODE。

package com.xiyu.bid.project.core;

import com.xiyu.bid.entity.RoleProfileCatalog;

import java.util.Set;
import java.util.TreeSet;

/**
 * 项目转移新负责人角色校验策略。
 * <p>
 * 合法角色 = {@link RoleProfileCatalog#GLOBAL_ACCESS_ROLES}（admin/bidAdmin/bidTeamLeader）
 * + {@link RoleProfileCatalog#SALES_CODE}（bid-projectLeader）。
 * </p>
 * <p>
 * 使用大小写不敏感集合以兼容 OSS 同步可能传入的不同大小写角色码
 * （对齐 Constitution V. OSS Integration 大小写安全规则）。
 * </p>
 */
public final class ProjectTransferRolePolicy {

    private static final Set<String> VALID_NEW_OWNER_ROLES;

    static {
        TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.addAll(RoleProfileCatalog.GLOBAL_ACCESS_ROLES);
        set.add(RoleProfileCatalog.SALES_CODE);
        VALID_NEW_OWNER_ROLES = java.util.Collections.unmodifiableSet(set);
    }

    private ProjectTransferRolePolicy() {
    }

    /**
     * 校验角色码是否为合法的项目转移新负责人角色。
     *
     * @param roleCode 角色码（来自 EffectiveRoleResolver.resolveRoleCode）
     * @return true 如果角色为 admin//bidAdmin/bid-TeamLeader/bid-projectLeader 之一
     */
    public static boolean isValidNewOwnerRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        return VALID_NEW_OWNER_ROLES.contains(roleCode);
    }
}
