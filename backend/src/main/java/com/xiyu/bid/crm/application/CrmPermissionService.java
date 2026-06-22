package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OSS 用户权限服务。
 * <p>
 * 调用 GET /oauth/getUserPermission 获取用户系统权限。
 * <p>
 * 响应格式（泊冉标准）：
 * <pre>
 * {
 *   "code": 0,
 *   "message": "success",
 *   "data": {
 *     "OPC": ["sale_log", "sale_order"],
 *     "SMS": ["sys_org", "sys_user"]
 *   }
 * }
 * </pre>
 */
@Service
public class CrmPermissionService {

    private static final Logger log = LoggerFactory.getLogger(CrmPermissionService.class);

    private final CrmHttpClient httpClient;
    private final CrmProperties properties;
    private final OssPermissionCache permissionCache;

    public CrmPermissionService(CrmHttpClient httpClient, CrmProperties properties,
                                OssPermissionCache permissionCache) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.permissionCache = permissionCache;
    }

    public CrmUserPermission getUserPermission(String userAccessToken, String systemName) {
        String cacheKey = (userAccessToken != null ? userAccessToken.substring(0, Math.min(20, userAccessToken.length())) : "unknown")
                + "::" + (systemName != null ? systemName : "default");
        java.util.Optional<CrmUserPermission> cached = permissionCache.get(cacheKey);
        if (cached.isPresent()) {
            log.debug("OSS permission cache hit for key={}", cacheKey);
            return cached.get();
        }
        String baseUrl = properties.getEffectiveAuthBaseUrl();
        String path = properties.getAuth().getUserPermissionPath();
        if (systemName != null && !systemName.isBlank()) {
            path = path + "?systemName=" + systemName;
        }
        CrmResponseHandler.CrmApiResponse response = httpClient.get(baseUrl, path, userAccessToken);
        if (response == null || response.data() == null) {
            log.warn("OSS user permission response empty");
            return new CrmUserPermission(Collections.emptyMap());
        }
        CrmUserPermission permission = parsePermission(response.data());
        permissionCache.put(cacheKey, permission);
        log.info("OSS user permission cached for key={}, size={}", cacheKey,
                permission.systemPermissions().size());
        return permission;
    }

    public CrmUserPermission getUserPermission(String userAccessToken) {
        return getUserPermission(userAccessToken, properties.getAuth().getUserPermissionSystemName());
    }

    private CrmUserPermission parsePermission(JsonNode data) {
        Map<String, List<String>> systemPermissions = new LinkedHashMap<>();
        data.fields().forEachRemaining(entry -> {
            String systemName = entry.getKey();
            JsonNode menuList = entry.getValue();
            if (menuList != null && menuList.isArray()) {
                List<String> menus = new java.util.ArrayList<>();
                for (JsonNode item : menuList) {
                    if (item.isTextual()) {
                        menus.add(item.asText());
                    }
                }
                systemPermissions.put(systemName, menus);
            }
        });
        return new CrmUserPermission(systemPermissions);
    }
}
