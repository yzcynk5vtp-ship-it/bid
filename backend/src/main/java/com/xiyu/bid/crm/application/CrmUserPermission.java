package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * OSS 用户权限响应。
 * <p>
 * 对应 GET /oauth/getUserPermission 的 data 字段。
 * <p>
 * 格式示例：
 * <pre>
 * {
 *   "OPC": ["sale_log", "sale_order"],
 *   "SMS": ["sys_org", "sys_user"]
 * }
 * </pre>
 */
public record CrmUserPermission(
        Map<String, List<String>> systemPermissions
) {
    public CrmUserPermission {
        if (systemPermissions == null) {
            systemPermissions = Collections.emptyMap();
        }
    }

    public boolean hasPermission(String systemName, String menuKey) {
        List<String> menus = systemPermissions.get(systemName);
        if (menus == null) {
            return false;
        }
        return menus.contains(menuKey);
    }

    public List<String> getMenusForSystem(String systemName) {
        List<String> menus = systemPermissions.get(systemName);
        return menus == null ? Collections.emptyList() : Collections.unmodifiableList(menus);
    }

    /**
     * {@code @JsonIgnore} 防止 Jackson 把 {@code isEmpty()} 误认为 {@code empty} 属性的 getter
     * （{@code isXxx()} 命名约定），导致序列化出多余的 {@code "empty":false} 字段，
     * 反序列化时在非宽松 ObjectMapper 下报 "Unrecognized field empty"。
     */
    @JsonIgnore
    public boolean isEmpty() {
        return systemPermissions.isEmpty()
                || systemPermissions.values().stream().allMatch(list -> list == null || list.isEmpty());
    }
}
