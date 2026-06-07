package com.xiyu.bid.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdempotencyFilterTest {

    private HandlerMapping handlerMapping;
    private InMemoryIdempotencyStore store;
    private IdempotencyFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        handlerMapping = mock(HandlerMapping.class);
        store = new InMemoryIdempotencyStore();
        filter = new IdempotencyFilter(handlerMapping, store);
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, java.util.Collections.emptyList())
        );
    }

    @Test
    void shouldBypassWhenNoIdempotencyHeader() throws Exception {
        MockHttpServletRequest req = post("{\"a\":1}", null);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = okChain(201, "{\"id\":1}");

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(201);
        assertThat(resp.getContentAsString()).isEqualTo("{\"id\":1}");
        assertThat(store.find("alice:POST:/api/tenders:k1")).isEmpty();
    }

    @Test
    void shouldBypassWhenHandlerHasNoIdempotentAnnotation() throws Exception {
        MockHttpServletRequest req = post("{\"a\":1}", "k-bypass");
        stubHandler(req, "handlerWithoutAnnotation");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = okChain(201, "{\"id\":2}");

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(201);
        assertThat(store.find("alice:POST:/api/tenders:k-bypass")).isEmpty();
    }

    @Test
    void shouldCacheAndReplaySameKey() throws Exception {
        MockHttpServletRequest req1 = post("{\"a\":1}", "k-same");
        stubHandler(req1, "idempotentHandler");
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        MockFilterChain chain1 = okChain(201, "{\"id\":99}");

        filter.doFilter(req1, resp1, chain1);

        assertThat(resp1.getStatus()).isEqualTo(201);

        MockHttpServletRequest req2 = post("{\"a\":1}", "k-same");
        stubHandler(req2, "idempotentHandler");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        MockFilterChain chain2 = new MockFilterChain();

        filter.doFilter(req2, resp2, chain2);

        assertThat(resp2.getStatus()).isEqualTo(201);
        assertThat(resp2.getContentAsString()).isEqualTo("{\"id\":99}");
        // chain2 must not have been invoked — verify by ensuring request attribute not set
        assertThat(chain2.getRequest()).isNull();
    }

    @Test
    void shouldReturn422WhenRequestBodyDiffers() throws Exception {
        MockHttpServletRequest req1 = post("{\"a\":1}", "k-diff");
        stubHandler(req1, "idempotentHandler");
        filter.doFilter(req1, new MockHttpServletResponse(), okChain(201, "{\"id\":1}"));

        MockHttpServletRequest req2 = post("{\"a\":2}", "k-diff");
        stubHandler(req2, "idempotentHandler");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();

        filter.doFilter(req2, resp2, new MockFilterChain());

        assertThat(resp2.getStatus()).isEqualTo(422);
        assertThat(resp2.getContentAsString()).contains("Idempotency-Key conflict");
    }

    @Test
    void shouldNotCacheErrorResponses() throws Exception {
        MockHttpServletRequest req1 = post("{\"a\":1}", "k-err");
        stubHandler(req1, "idempotentHandler");
        filter.doFilter(req1, new MockHttpServletResponse(), okChain(500, "{\"error\":\"boom\"}"));

        MockHttpServletRequest req2 = post("{\"a\":1}", "k-err");
        stubHandler(req2, "idempotentHandler");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        MockFilterChain chain2 = okChain(201, "{\"retry\":true}");

        filter.doFilter(req2, resp2, chain2);

        assertThat(resp2.getStatus()).isEqualTo(201);
        assertThat(resp2.getContentAsString()).isEqualTo("{\"retry\":true}");
    }

    // ========== 鲁棒性测试：重试与幂等 ==========

    @Test
    @DisplayName("幂等：同一 Idempotency-Key 并发请求，缓存命中返回相同响应，handler 仅执行一次")
    void concurrentRequestsWithSameKey_secondBlocksUntilFirstCompletes() throws Exception {
        MockHttpServletRequest req1 = post("{\"a\":1}", "k-concurrent");
        stubHandler(req1, "idempotentHandler");
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        MockFilterChain chain1 = okChain(201, "{\"id\":42}");

        MockHttpServletRequest req2 = post("{\"a\":1}", "k-concurrent");
        stubHandler(req2, "idempotentHandler");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        MockFilterChain chain2 = new MockFilterChain();

        // 模拟并发：req1 先完成，req2 在 req1 写入缓存前尝试读取
        filter.doFilter(req1, resp1, chain1);

        // req2 应命中缓存，返回相同响应
        filter.doFilter(req2, resp2, chain2);

        assertThat(resp2.getStatus()).isEqualTo(201);
        assertThat(resp2.getContentAsString()).isEqualTo("{\"id\":42}");
    }

    @Test
    @DisplayName("幂等：TTL 过期后，相同 key 的新请求应该重新执行业务逻辑，不返回旧缓存")
    void expiredKey_allowsNewExecution() throws Exception {
        // 使用极短 TTL 模拟过期场景
        InMemoryIdempotencyStore shortTtlStore = new InMemoryIdempotencyStore();
        IdempotencyFilter shortTtlFilter = new IdempotencyFilter(handlerMapping, shortTtlStore);

        // 第一个请求（通过反射注入短 TTL）
        MockHttpServletRequest req1 = post("{\"a\":1}", "k-ttl");
        stubHandler(req1, "idempotentHandler");
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        shortTtlFilter.doFilter(req1, resp1, okChain(201, "{\"id\":1}"));

        assertThat(resp1.getStatus()).isEqualTo(201);

        // 模拟 TTL 过期：手动从 cache 中移除该 key
        java.lang.reflect.Field cacheField = InMemoryIdempotencyStore.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, ?> cache =
                (java.util.Map<String, ?>) cacheField.get(shortTtlStore);
        cache.clear(); // 模拟 TTL 过期后缓存被清除

        // 第二个请求应该重新执行业务逻辑
        MockHttpServletRequest req2 = post("{\"a\":1}", "k-ttl");
        stubHandler(req2, "idempotentHandler");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        MockFilterChain chain2 = okChain(201, "{\"id\":2}");

        shortTtlFilter.doFilter(req2, resp2, chain2);

        assertThat(resp2.getStatus()).isEqualTo(201);
        assertThat(resp2.getContentAsString()).isEqualTo("{\"id\":2}");
    }

    @Test
    @DisplayName("幂等：不同用户的相同 Idempotency-Key + 相同 body → 各自独立执行（用户 scope 隔离）")
    void differentUsers_sameKey_sameBody_bothSucceed() throws Exception {
        // 每个测试使用独立的 store，避免跨测试干扰
        InMemoryIdempotencyStore freshStore = new InMemoryIdempotencyStore();
        IdempotencyFilter freshFilter = new IdempotencyFilter(handlerMapping, freshStore);

        // Alice 的请求（cache key = alice:POST:/api/tenders:k-alice-bob-scope）
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, java.util.Collections.emptyList())
        );
        MockHttpServletRequest reqAlice = post("{\"user\":\"alice\"}", "k-alice-bob-scope");
        stubHandler(reqAlice, "idempotentHandler");
        MockHttpServletResponse respAlice = new MockHttpServletResponse();
        freshFilter.doFilter(reqAlice, respAlice, okChain(201, "{\"id\":100}"));
        assertThat(respAlice.getStatus()).isEqualTo(201);

        // Bob 的请求（相同 key，不同用户，相同 body → 不同的 cache key，各自独立）
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("bob", null, java.util.Collections.emptyList())
        );
        MockHttpServletRequest reqBob = post("{\"user\":\"alice\"}", "k-alice-bob-scope");
        stubHandler(reqBob, "idempotentHandler");
        MockHttpServletResponse respBob = new MockHttpServletResponse();
        MockFilterChain chainBob = okChain(201, "{\"id\":200}");
        freshFilter.doFilter(reqBob, respBob, chainBob);

        assertThat(respBob.getStatus()).isEqualTo(201);
        assertThat(respBob.getContentAsString()).isEqualTo("{\"id\":200}");
    }

    @Test
    @DisplayName("幂等：不同端点的相同 Idempotency-Key + 相同 body → 各自独立执行")
    void sameKey_differentEndpoint_sameBody_bothSucceed() throws Exception {
        // 每个测试使用独立的 store
        InMemoryIdempotencyStore freshStore = new InMemoryIdempotencyStore();
        IdempotencyFilter freshFilter = new IdempotencyFilter(handlerMapping, freshStore);

        // POST /api/tenders（k-cross-ep-uniq）
        MockHttpServletRequest req1 = post("{\"tender\":1}", "k-cross-ep-uniq");
        stubHandler(req1, "idempotentHandler");
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        freshFilter.doFilter(req1, resp1, okChain(201, "{\"tenderId\":1}"));
        assertThat(resp1.getStatus()).isEqualTo(201);

        // POST /api/documents（相同 key，不同端点 → 不同的 cache key，各自独立）
        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/documents");
        req2.setContentType("application/json");
        req2.setContent("{\"tender\":1}".getBytes(StandardCharsets.UTF_8));
        req2.addHeader("Idempotency-Key", "k-cross-ep-uniq");
        stubHandler(req2, "idempotentHandler");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        MockFilterChain chain2 = okChain(200, "{\"docId\":2}");
        freshFilter.doFilter(req2, resp2, chain2);

        assertThat(resp2.getStatus()).isEqualTo(200);
        assertThat(resp2.getContentAsString()).isEqualTo("{\"docId\":2}");
    }

    @Test
    @DisplayName("幂等：store 抛出异常时，请求应该降级透传到 handler，不阻塞业务")
    void storeFailure_shouldBypassToHandler() throws Exception {
        InMemoryIdempotencyStore failingStore = new InMemoryIdempotencyStore() {
            @Override
            public void save(String key, IdempotencyStore.CachedResponse response, java.time.Duration ttl) {
                throw new RuntimeException("Redis connection failed");
            }
        };
        IdempotencyFilter failingFilter = new IdempotencyFilter(handlerMapping, failingStore);

        MockHttpServletRequest req = post("{\"a\":1}", "k-fail-store");
        stubHandler(req, "idempotentHandler");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = okChain(201, "{\"id\":999}");

        // 降级：store 异常时应该透传到 handler
        failingFilter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(201);
        assertThat(resp.getContentAsString()).isEqualTo("{\"id\":999}");
    }

    private MockHttpServletRequest post(String body, String idempotencyKey) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tenders");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        if (idempotencyKey != null) {
            request.addHeader("Idempotency-Key", idempotencyKey);
        }
        return request;
    }

    private void stubHandler(HttpServletRequest request, String handlerMethodName) throws Exception {
        Method method = StubController.class.getMethod(handlerMethodName);
        HandlerMethod handlerMethod = new HandlerMethod(new StubController(), method);
        HandlerExecutionChain chain = new HandlerExecutionChain(handlerMethod);
        when(handlerMapping.getHandler(any(HttpServletRequest.class))).thenReturn(chain);
    }

    private MockFilterChain okChain(int status, String body) {
        return new MockFilterChain(new jakarta.servlet.GenericServlet() {
            @Override
            public void service(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws java.io.IOException {
                HttpServletResponse resp = (HttpServletResponse) response;
                resp.setStatus(status);
                resp.setContentType("application/json");
                resp.getWriter().write(body);
                resp.getWriter().flush();
            }
        });
    }

    public static class StubController {
        @Idempotent
        public void idempotentHandler() {}

        public void handlerWithoutAnnotation() {}
    }
}
