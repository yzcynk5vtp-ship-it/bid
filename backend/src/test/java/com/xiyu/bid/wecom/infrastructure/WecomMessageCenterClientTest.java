package com.xiyu.bid.wecom.infrastructure;

import com.xiyu.bid.wecom.config.WecomMessageCenterProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WecomMessageCenterClient — 调用 /qywx/sendMSG")
class WecomMessageCenterClientTest {

    @Mock private RestTemplate restTemplate;

    private WecomMessageCenterClient client;
    private WecomMessageCenterProperties properties;

    @BeforeEach
    void setUp() {
        properties = new WecomMessageCenterProperties();
        properties.setBaseUrl("https://message-center.example.com");
        properties.setSendPath("/qywx/sendMSG");
        properties.setApplicationCode("test-app");

        // 构造一个使用 mock RestTemplate 的 client；这里通过反射替换内部 restTemplate
        client = newTestClientWith(restTemplate, properties);
    }

    @Test
    @DisplayName("成功响应 code=0 -> MessageCenterResponse")
    void successResponse_parsed() {
        when(restTemplate.exchange(
                eq("https://message-center.example.com/qywx/sendMSG"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{\"code\":0,\"message\":\"ok\",\"trace\":\"t1\"}"));

        WecomMessageCenterClient.MessageCenterResponse r = client.sendMessage("E007", "hello");

        assertThat(r.code()).isEqualTo(0);
        assertThat(r.message()).isEqualTo("ok");
        assertThat(r.trace()).isEqualTo("t1");
    }

    @Test
    @DisplayName("失败响应 code!=0 -> MessageCenterResponse 保留原 code")
    void failureResponse_parsed() {
        when(restTemplate.exchange(
                eq("https://message-center.example.com/qywx/sendMSG"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{\"code\":500,\"message\":\"busy\",\"trace\":\"t2\"}"));

        WecomMessageCenterClient.MessageCenterResponse r = client.sendMessage("E007", "hello");

        assertThat(r.code()).isEqualTo(500);
        assertThat(r.message()).isEqualTo("busy");
    }

    @Test
    @DisplayName("空响应体 -> 抛 WecomMessageCenterException")
    void emptyResponse_throws() {
        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(""));

        assertThatThrownBy(() -> client.sendMessage("E007", "hello"))
            .isInstanceOf(WecomMessageCenterException.class)
            .hasMessageContaining("empty body");
    }

    /**
     * 通过反射构造一个内部 RestTemplate 被替换为 mock 的 client。
     * 避免测试真实发网络请求。
     */
    private static WecomMessageCenterClient newTestClientWith(RestTemplate restTemplate,
                                                               WecomMessageCenterProperties properties) {
        try {
            WecomMessageCenterClient client = new WecomMessageCenterClient(new org.springframework.boot.web.client.RestTemplateBuilder(), properties);
            java.lang.reflect.Field field = WecomMessageCenterClient.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(client, restTemplate);
            return client;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
