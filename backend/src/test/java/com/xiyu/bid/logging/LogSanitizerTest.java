package com.xiyu.bid.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.annotation.Sensitive;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    private final LogSanitizer sanitizer = new LogSanitizer(new ObjectMapper());

    @Test
    void masksDefaultSensitiveKeys() {
        Map<String, Object> input = Map.of(
                "username", "alice",
                "password", "super-secret",
                "token", "jwt-token-xyz"
        );
        String json = sanitizer.sanitize(input, 1024);
        assertThat(json).contains("\"username\":\"alice\"");
        assertThat(json).contains("\"password\":\"***\"");
        assertThat(json).contains("\"token\":\"***\"");
    }

    @Test
    void masksAnnotatedFields() {
        UserDto dto = new UserDto();
        dto.setUsername("alice");
        dto.setSecretKey("shhh");
        dto.setPassword("pwd");

        String json = sanitizer.sanitize(dto, 1024);
        assertThat(json).contains("\"username\":\"alice\"");
        assertThat(json).contains("\"secretKey\":\"***\"");
        assertThat(json).contains("\"password\":\"***\"");
    }

    @Test
    void masksNestedAnnotatedFields() {
        ParentDto parent = new ParentDto();
        parent.setName("parent");
        UserDto child = new UserDto();
        child.setUsername("bob");
        child.setSecretKey("child-secret");
        parent.setChild(child);

        String json = sanitizer.sanitize(parent, 1024);
        assertThat(json).contains("\"name\":\"parent\"");
        assertThat(json).contains("\"username\":\"bob\"");
        assertThat(json).contains("\"secretKey\":\"***\"");
    }

    @Test
    void truncatesLongOutput() {
        String longValue = "x".repeat(500);
        String json = sanitizer.sanitize(Map.of("data", longValue), 100);
        assertThat(json).hasSizeLessThanOrEqualTo(100);
        assertThat(json).endsWith("...[truncated]");
    }

    @Test
    void handlesCollectionsAndMaps() {
        UserDto dto = new UserDto();
        dto.setUsername("alice");
        dto.setSecretKey("key1");

        List<UserDto> list = List.of(dto);
        String json = sanitizer.sanitize(list, 1024);
        assertThat(json).contains("\"username\":\"alice\"");
        assertThat(json).contains("\"secretKey\":\"***\"");
    }

    @Test
    void handlesNullValue() {
        assertThat(sanitizer.sanitize(null, 1024)).isEqualTo("null");
    }

    @Test
    void handlesCircularReferencesWithoutStackOverflow() {
        NodeDto a = new NodeDto();
        a.setName("a");
        NodeDto b = new NodeDto();
        b.setName("b");
        a.setNext(b);
        b.setNext(a);

        String json = sanitizer.sanitize(a, 1024);
        assertThat(json).contains("\"error\":\"无法序列化日志参数\"");
        assertThat(json).contains("\"type\":\"NodeDto\"");
    }

    @Data
    static class UserDto {
        private String username;
        @Sensitive
        private String secretKey;
        private String password;
    }

    @Data
    static class ParentDto {
        private String name;
        private UserDto child;
    }

    @Data
    static class NodeDto {
        private String name;
        private NodeDto next;
    }
}
