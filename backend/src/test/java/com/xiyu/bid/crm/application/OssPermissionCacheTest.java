// Input: OssPermissionCache with optional StringRedisTemplate + ObjectMapper
// Output: verifies Redis round-trip, in-memory fallback, invalidation
// Pos: crm/application - unit test for OSS permission cache dual-mode storage
package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssPermissionCacheTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("无 Redis 时纯内存模式 put/get/invalidate 正常工作")
    void inMemoryFallbackWorksWhenRedisAbsent() {
        OssPermissionCache cache = new OssPermissionCache();

        cache.put("user1", "bid-Team",
                List.of("dashboard", "project"),
                new CrmUserPermission(Map.of("OPC", List.of("sale_log"))));

        Optional<OssPermissionCache.CacheEntry> entry = cache.getEntry("user1");
        assertThat(entry).isPresent();
        assertThat(entry.get().roleCode()).isEqualTo("bid-Team");
        assertThat(entry.get().menuPermissions()).contains("dashboard", "project");

        cache.invalidate("user1");
        assertThat(cache.getEntry("user1")).isEmpty();
    }

    @Test
    @DisplayName("Redis 可用时 put 写 Redis、get 读 Redis，CacheEntry 含 Instant 正确往返")
    void redisWriteAndReadRoundTrip() throws Exception {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // 模拟 Redis 存储：put 写入的 JSON 在 get 时返回
        final String[] storedJson = {null};
        doAnswer(inv -> {
            storedJson[0] = inv.getArgument(1);
            return null;
        }).when(valueOps).set(anyString(), anyString(), any());
        when(valueOps.get(anyString())).thenAnswer(inv -> storedJson[0]);

        OssPermissionCache cache = new OssPermissionCache(Optional.of(redisTemplate), objectMapper);

        CrmUserPermission permission = new CrmUserPermission(Map.of("bid-platform", List.of("1001", "1002")));
        cache.put("06234", "admin", List.of("project", "resource"), permission);

        // 验证写入 Redis
        verify(valueOps).set(eq(OssPermissionCache.REDIS_KEY_PREFIX + "06234"), anyString(), any());
        assertThat(storedJson[0]).isNotNull();
        assertThat(storedJson[0]).contains("\"roleCode\":\"admin\"");
        assertThat(storedJson[0]).contains("project");
        assertThat(storedJson[0]).contains("bid-platform");

        // 验证从 Redis 读取
        Optional<OssPermissionCache.CacheEntry> entry = cache.getEntry("06234");
        assertThat(entry).isPresent();
        assertThat(entry.get().roleCode()).isEqualTo("admin");
        assertThat(entry.get().menuPermissions()).contains("project", "resource");
        assertThat(entry.get().permission().systemPermissions())
                .containsEntry("bid-platform", List.of("1001", "1002"));
        assertThat(entry.get().expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Redis miss 时回退内存读取")
    void redisMissFallsBackToMemory() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        OssPermissionCache cache = new OssPermissionCache(Optional.of(redisTemplate), objectMapper);

        // 仅写内存（直接操作内部 cache，模拟 Redis 写失败后内存兜底仍有数据）
        cache.put("fallback-user", "bid-TeamLeader", List.of("dashboard"), null);
        // getEntry 时 Redis miss → 回退内存
        Optional<OssPermissionCache.CacheEntry> entry = cache.getEntry("fallback-user");
        assertThat(entry).isPresent();
        assertThat(entry.get().roleCode()).isEqualTo("bid-TeamLeader");
    }

    @Test
    @DisplayName("invalidate 同时删除 Redis key 和内存条目")
    void redisInvalidateDeletesKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        OssPermissionCache cache = new OssPermissionCache(Optional.of(redisTemplate), objectMapper);
        cache.put("to-delete", "admin", List.of("all"), null);

        cache.invalidate("to-delete");

        verify(redisTemplate).delete(OssPermissionCache.REDIS_KEY_PREFIX + "to-delete");
        assertThat(cache.getEntry("to-delete")).isEmpty();
    }

    @Test
    @DisplayName("legacy put(key, CrmUserPermission) 写 Redis 并可读回")
    void legacyPutAndGetRoundTrip() throws Exception {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        final String[] storedJson = {null};
        doAnswer(inv -> {
            storedJson[0] = inv.getArgument(1);
            return null;
        }).when(valueOps).set(anyString(), anyString(), any());
        when(valueOps.get(anyString())).thenAnswer(inv -> storedJson[0]);

        OssPermissionCache cache = new OssPermissionCache(Optional.of(redisTemplate), objectMapper);

        CrmUserPermission permission = new CrmUserPermission(Map.of("OPC", List.of("sale_log")));
        cache.put("tokenhash::default", permission);

        Optional<CrmUserPermission> retrieved = cache.get("tokenhash::default");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().systemPermissions()).containsEntry("OPC", List.of("sale_log"));
    }
}
