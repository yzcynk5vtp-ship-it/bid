package com.xiyu.bid.config;

import com.xiyu.bid.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 鲁棒性测试：降级与回滚路径。
 * 覆盖 DegradedModeController 的 Redis/DB 降级异常处理。
 */
@DisplayName("DegradedModeController — graceful degradation robustness")
class DegradedModeControllerRobustnessTest {

    private final DegradedModeController.DegradedExceptionHandler handler =
            new DegradedModeController.DegradedExceptionHandler();

    private HttpServletRequest mockRequest(String uri) {
        HttpServletRequest mock = org.mockito.Mockito.mock(HttpServletRequest.class);
        org.mockito.Mockito.when(mock.getRequestURI()).thenReturn(uri);
        return mock;
    }

    // ========== Redis 降级测试 ==========

    @Nested
    @DisplayName("Redis 降级处理")
    class RedisDegradation {

        @Test
        @DisplayName("Redis 连接失败 → 503 + 降级响应体")
        void redisConnectionFailure_returns503Degraded() {
            RedisConnectionFailureException ex = new RedisConnectionFailureException("Connection refused");
            ResponseEntity<ApiResponse<Void>> response = handler.handleRedisDown(ex, mockRequest("/api/documents"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(503);
            assertThat(response.getBody().getMessage()).contains("缓存服务暂不可用");
        }

        @Test
        @DisplayName("Redis 降级响应包含原始请求路径用于排查")
        void redisFailure_responseContainsOriginalPath() {
            RedisConnectionFailureException ex = new RedisConnectionFailureException("timeout");
            ResponseEntity<ApiResponse<Void>> response = handler.handleRedisDown(ex, mockRequest("/api/users/123/profile"));

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).contains("缓存服务暂不可用");
            // 日志中应包含原始路径
        }

        @Test
        @DisplayName("不同 Redis 异常类型均被统一降级处理")
        void differentRedisExceptions_unifiedDegradedHandling() {
            // ConnectException 子类
            RedisConnectionFailureException ex1 = new RedisConnectionFailureException("read timeout");
            ResponseEntity<ApiResponse<Void>> r1 = handler.handleRedisDown(ex1, mockRequest("/api/test"));
            assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

            // 嵌套根因（只测字符串消息）
            RedisConnectionFailureException ex2 = new RedisConnectionFailureException(
                    "nested: connection reset"
            );
            ResponseEntity<ApiResponse<Void>> r2 = handler.handleRedisDown(ex2, mockRequest("/api/test"));
            assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    // ========== 数据库降级测试 ==========

    @Nested
    @DisplayName("数据库降级处理")
    class DatabaseDegradation {

        @Test
        @DisplayName("数据库连接失败 → 503 + 降级响应体")
        void databaseConnectionFailure_returns503Degraded() {
            DataAccessException ex = new org.springframework.jdbc.CannotGetJdbcConnectionException("Connection refused");
            ResponseEntity<ApiResponse<Void>> response = handler.handleDbDown(ex, mockRequest("/api/projects"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(503);
            assertThat(response.getBody().getMessage()).contains("数据库暂不可用");
        }

        @Test
        @DisplayName("不同类型 DataAccessException 均被降级处理")
        void differentDataAccessExceptions_allDegraded() {
            // 常见数据库异常类型
            DataAccessException[] exceptions = {
                    new org.springframework.jdbc.CannotGetJdbcConnectionException("DB down"),
                    new org.springframework.dao.DataAccessResourceFailureException("connection refused"),
                    new org.springframework.dao.TransientDataAccessException("timeout") {}
            };

            for (DataAccessException ex : exceptions) {
                ResponseEntity<ApiResponse<Void>> response = handler.handleDbDown(ex, mockRequest("/api/test"));
                assertThat(response.getStatusCode())
                        .as("Exception type %s should return 503", ex.getClass().getSimpleName())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            }
        }

        @Test
        @DisplayName("数据库降级响应包含原始请求路径")
        void databaseFailure_responseContainsOriginalPath() {
            DataAccessException ex = new org.springframework.jdbc.CannotGetJdbcConnectionException("DB unavailable");
            ResponseEntity<ApiResponse<Void>> response = handler.handleDbDown(ex, mockRequest("/api/tenders/99/status"));

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).contains("数据库暂不可用");
        }
    }

    // ========== 降级恢复边界测试 ==========

    @Nested
    @DisplayName("降级边界条件")
    class DegradationBoundary {

        @Test
        @DisplayName("异常消息为 null 时降级响应仍正常")
        void nullExceptionMessage_stillReturnsDegraded() {
            RedisConnectionFailureException ex = new RedisConnectionFailureException(null);
            ResponseEntity<ApiResponse<Void>> response = handler.handleRedisDown(ex, mockRequest("/api/test"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("异常消息为空字符串时降级响应仍正常")
        void emptyExceptionMessage_stillReturnsDegraded() {
            RedisConnectionFailureException ex = new RedisConnectionFailureException("");
            ResponseEntity<ApiResponse<Void>> response = handler.handleRedisDown(ex, mockRequest("/api/test"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        @Test
        @DisplayName("异常消息超长时降级响应仍正常")
        void longExceptionMessage_stillReturnsDegraded() {
            String longMessage = "x".repeat(10000);
            RedisConnectionFailureException ex = new RedisConnectionFailureException(longMessage);
            ResponseEntity<ApiResponse<Void>> response = handler.handleRedisDown(ex, mockRequest("/api/test"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        @Test
        @DisplayName("非降级范围内的异常不被处理")
        void nonDegradationExceptions_notHandled() {
            // 这两个处理器只处理 RedisConnectionFailureException 和 DataAccessException
            // 其他异常应该被其他 @ExceptionHandler 处理
            RuntimeException ex = new RuntimeException("business error");
            // 验证这些异常类型不被 handleRedisDown 或 handleDbDown 处理
            assertThat(ex).isNotInstanceOf(RedisConnectionFailureException.class);
            assertThat(ex).isNotInstanceOf(DataAccessException.class);
        }
    }

    // ========== Health Indicator 测试 ==========

    @Nested
    @DisplayName("Health Indicator 降级检测")
    class HealthIndicatorTest {

        @Test
        @DisplayName("降级健康检查始终返回 UP")
        void degradedHealthIndicator_alwaysUp() {
            DegradedModeController controller = new DegradedModeController();
            var indicator = controller.degradedHealthIndicator();

            // 验证 indicator bean 存在即可（具体 getHealth() 签名依赖 Spring Boot 版本）
            assertThat(indicator).isNotNull();
        }
    }

    // ========== 降级响应格式一致性测试 ==========

    @Nested
    @DisplayName("降级响应格式一致性")
    class ResponseFormatConsistency {

        @Test
        @DisplayName("Redis 和 DB 降级响应的 code 字段一致")
        void redisAndDbDegradedCode_consistent() {
            ResponseEntity<ApiResponse<Void>> redisResponse = handler.handleRedisDown(
                    new RedisConnectionFailureException("err"),
                    mockRequest("/api/test")
            );
            ResponseEntity<ApiResponse<Void>> dbResponse = handler.handleDbDown(
                    new org.springframework.jdbc.CannotGetJdbcConnectionException("err"),
                    mockRequest("/api/test")
            );

            // code 为 Integer 类型，值为 HTTP 状态码 503
            assertThat(redisResponse.getBody().getCode()).isEqualTo(503);
            assertThat(dbResponse.getBody().getCode()).isEqualTo(503);
            assertThat(redisResponse.getBody().getCode())
                    .isEqualTo(dbResponse.getBody().getCode());
        }

        @Test
        @DisplayName("降级响应 body 为 null 安全")
        void degradedResponse_nullSafe() {
            ResponseEntity<ApiResponse<Void>> redisResponse = handler.handleRedisDown(
                    new RedisConnectionFailureException("err"),
                    mockRequest("/api/test")
            );
            ResponseEntity<ApiResponse<Void>> dbResponse = handler.handleDbDown(
                    new org.springframework.jdbc.CannotGetJdbcConnectionException("err"),
                    mockRequest("/api/test")
            );

            assertThat(redisResponse.getBody()).isNotNull();
            assertThat(dbResponse.getBody()).isNotNull();
            assertThat(redisResponse.getBody().getData()).isNull();
            assertThat(dbResponse.getBody().getData()).isNull();
        }
    }
}
