package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * OSS 用户权限缓存。
 * <p>
 * 缓存策略：
 * - 按 username 缓存
 * - 默认过期时间：30 分钟
 * - 登出时主动清除
 */
@Component
public class OssPermissionCache {

    private static final Logger log = LoggerFactory.getLogger(OssPermissionCache.class);

    private static final long DEFAULT_TTL_SECONDS = 1800; // 30 分钟

    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public void put(String username, CrmUserPermission permission) {
        cache.put(username, new CacheEntry(permission, Instant.now().plusSeconds(DEFAULT_TTL_SECONDS)));
        log.debug("OSS permission cached for user={}", username);
    }

    public java.util.Optional<CrmUserPermission> get(String username) {
        CacheEntry entry = cache.get(username);
        if (entry == null) {
            return java.util.Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            cache.remove(username);
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(entry.permission());
    }

    public void invalidate(String username) {
        cache.remove(username);
        log.debug("OSS permission cache invalidated for user={}", username);
    }

    public void clear() {
        cache.clear();
        log.info("OSS permission cache cleared");
    }

    public int size() {
        return cache.size();
    }

    public record CacheEntry(CrmUserPermission permission, Instant expiresAt) {}
}
