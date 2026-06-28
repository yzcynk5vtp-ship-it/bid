// Input: 任意 Java 对象
// Output: 脱敏 + 截断后的 JSON 字符串
// Pos: logging/ 纯核心工具类，无 Spring 依赖
package com.xiyu.bid.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiyu.bid.annotation.Sensitive;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 日志序列化脱敏器。
 * <p>将对象序列化为 JSON 字符串，并对以下字段做掩码处理：</p>
 * <ul>
 *   <li>默认敏感键名（password、token、secret、apiKey 等，大小写不敏感）</li>
 *   <li>被 {@link Sensitive} 注解标注的字段</li>
 * </ul>
 * <p>同时支持按最大长度截断，避免超长对象撑爆日志。</p>
 */
public class LogSanitizer {

    private static final String MASK = "***";
    private static final String TRUNCATED_SUFFIX = "...[truncated]";
    private static final Set<String> DEFAULT_SENSITIVE_KEYS = Set.of(
            "password", "oldpassword", "newpassword", "confirmpassword",
            "token", "accesstoken", "refreshtoken",
            "secret", "apisecret", "apikey", "apikeysecret",
            "authorization", "cookie"
    );

    private final ObjectMapper objectMapper;

    public LogSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false);
    }

    /**
     * 将对象序列化为脱敏后的 JSON 字符串，并按最大长度截断。
     *
     * @param value     待序列化的对象，可为 null
     * @param maxLength 最大字符长度，超过则截断
     * @return 脱敏后的 JSON 字符串
     */
    public String sanitize(Object value, int maxLength) {
        if (value == null) {
            return "null";
        }
        try {
            Set<String> sensitiveKeys = new HashSet<>(DEFAULT_SENSITIVE_KEYS);
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            collectAnnotatedFieldNames(value, sensitiveKeys, visited);
            JsonNode tree = objectMapper.valueToTree(value);
            JsonNode masked = mask(tree, sensitiveKeys);
            String json = objectMapper.writeValueAsString(masked);
            return truncate(json, maxLength);
        } catch (Throwable e) {
            return "{\"error\":\"无法序列化日志参数\",\"type\":\"" + value.getClass().getSimpleName() + "\"}";
        }
    }

    private JsonNode mask(JsonNode node, Set<String> sensitiveKeys) {
        if (node.isObject()) {
            ObjectNode copy = node.deepCopy();
            copy.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (isSensitiveKey(key, sensitiveKeys)) {
                    entry.setValue(objectMapper.valueToTree(MASK));
                } else {
                    entry.setValue(mask(entry.getValue(), sensitiveKeys));
                }
            });
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = node.deepCopy();
            for (int i = 0; i < copy.size(); i++) {
                copy.set(i, mask(copy.get(i), sensitiveKeys));
            }
            return copy;
        }
        return node;
    }

    private boolean isSensitiveKey(String key, Set<String> sensitiveKeys) {
        if (key == null) {
            return false;
        }
        return sensitiveKeys.contains(key.toLowerCase());
    }

    private void collectAnnotatedFieldNames(Object value, Set<String> sensitiveKeys, Set<Object> visited) {
        if (value == null) {
            return;
        }
        if (!visited.add(value)) {
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> collectAnnotatedFieldNames(item, sensitiveKeys, visited));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> collectAnnotatedFieldNames(item, sensitiveKeys, visited));
            return;
        }
        Class<?> clazz = value.getClass();
        if (shouldStopRecursion(clazz)) {
            return;
        }
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Sensitive.class)) {
                sensitiveKeys.add(field.getName().toLowerCase());
            }
            Class<?> fieldType = field.getType();
            if (shouldStopRecursion(fieldType)) {
                continue;
            }
            if (Collection.class.isAssignableFrom(fieldType) || Map.class.isAssignableFrom(fieldType)) {
                field.setAccessible(true);
                Object nested;
                try {
                    nested = field.get(value);
                } catch (IllegalAccessException e) {
                    continue;
                }
                collectAnnotatedFieldNames(nested, sensitiveKeys, visited);
                continue;
            }
            field.setAccessible(true);
            Object nested;
            try {
                nested = field.get(value);
            } catch (IllegalAccessException e) {
                continue;
            }
            collectAnnotatedFieldNames(nested, sensitiveKeys, visited);
        }
    }

    private boolean shouldStopRecursion(Class<?> clazz) {
        return clazz == null
                || clazz.isPrimitive()
                || clazz.isArray()
                || clazz.getName().startsWith("java.")
                || clazz.getName().startsWith("javax.")
                || clazz.getName().startsWith("jakarta.")
                || clazz.getName().startsWith("sun.")
                || clazz.getName().startsWith("com.sun.");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - TRUNCATED_SUFFIX.length())) + TRUNCATED_SUFFIX;
    }
}
