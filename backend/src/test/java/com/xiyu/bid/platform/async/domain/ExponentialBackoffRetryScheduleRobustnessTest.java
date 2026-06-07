package com.xiyu.bid.platform.async.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 鲁棒性测试：ExponentialBackoffRetrySchedule 边界场景。
 * 覆盖指数退避的边界条件、溢出保护和确定性验证。
 */
@DisplayName("ExponentialBackoffRetrySchedule — robustness")
class ExponentialBackoffRetryScheduleRobustnessTest {

    private static final int BASE = 2;
    private static final int MAX = 60;
    private static final int MAX_EXPONENT = 4;

    // ========== 构造函数边界测试 ==========

    @Nested
    @DisplayName("构造函数边界")
    class ConstructorBoundary {

        @Test
        @DisplayName("baseDelaySeconds <= 0 抛出 IllegalArgumentException")
        void baseDelayZeroOrNegative_throws() {
            assertThatThrownBy(() -> new ExponentialBackoffRetrySchedule(0, 60, 4))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("baseDelaySeconds must be positive");

            assertThatThrownBy(() -> new ExponentialBackoffRetrySchedule(-1, 60, 4))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("maxDelaySeconds < baseDelaySeconds 抛出 IllegalArgumentException")
        void maxDelayLessThanBase_throws() {
            assertThatThrownBy(() -> new ExponentialBackoffRetrySchedule(10, 5, 4))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxDelaySeconds must be >= baseDelaySeconds");
        }

        @Test
        @DisplayName("maxExponent < 0 抛出 IllegalArgumentException")
        void maxExponentNegative_throws() {
            assertThatThrownBy(() -> new ExponentialBackoffRetrySchedule(2, 60, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxExponent must be >= 0");
        }
    }

    // ========== nextDelaySeconds 边界测试 ==========

    @Nested
    @DisplayName("nextDelaySeconds 边界")
    class NextDelayBoundary {

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 3, 4, 5, 10, 100})
        @DisplayName("任意 attempt 值不抛异常")
        void anyAttemptValue_doesNotThrow(int attempt) {
            ExponentialBackoffRetrySchedule schedule =
                    new ExponentialBackoffRetrySchedule(BASE, MAX, MAX_EXPONENT);
            int delay = schedule.nextDelaySeconds(attempt);
            assertThat(delay).isPositive();
        }

        @Test
        @DisplayName("attempt < 0 被规范化为 0")
        void negativeAttempt_normalized() {
            ExponentialBackoffRetrySchedule schedule =
                    new ExponentialBackoffRetrySchedule(BASE, MAX, MAX_EXPONENT);
            int delay = schedule.nextDelaySeconds(-5);
            assertThat(delay).isEqualTo(schedule.nextDelaySeconds(0));
        }

        @Test
        @DisplayName("指数退避序列：2^0, 2^1, 2^2, 2^3, 2^4, 2^4, ...")
        void exponentialBackoffSequence() {
            ExponentialBackoffRetrySchedule schedule =
                    new ExponentialBackoffRetrySchedule(BASE, MAX, MAX_EXPONENT);
            // BASE=2, MAX_EXPONENT=4
            // attempt 0 → 2*1 = 2s
            assertThat(schedule.nextDelaySeconds(0)).isEqualTo(2);
            // attempt 1 → 2*2 = 4s
            assertThat(schedule.nextDelaySeconds(1)).isEqualTo(4);
            // attempt 2 → 2*4 = 8s
            assertThat(schedule.nextDelaySeconds(2)).isEqualTo(8);
            // attempt 3 → 2*8 = 16s
            assertThat(schedule.nextDelaySeconds(3)).isEqualTo(16);
            // attempt 4 → 2*16 = 32s
            assertThat(schedule.nextDelaySeconds(4)).isEqualTo(32);
        }

        @Test
        @DisplayName("达到 maxExponent 后延迟被 cap 到 maxDelaySeconds")
        void beyondMaxExponent_cappedAtMaxDelay() {
            ExponentialBackoffRetrySchedule schedule =
                    new ExponentialBackoffRetrySchedule(BASE, MAX, MAX_EXPONENT);
            // attempt=10 → exponent = min(10, 4) = 4
            // delay = 2 * 2^4 = 32, 小于 MAX=60
            assertThat(schedule.nextDelaySeconds(10)).isEqualTo(32);
        }

        @Test
        @DisplayName("极大 attempt 值不导致整数溢出")
        void largeAttempt_noIntegerOverflow() {
            ExponentialBackoffRetrySchedule schedule =
                    new ExponentialBackoffRetrySchedule(2, 60, 4);
            // attempt=1000 → exponent = min(1000, 4) = 4
            // 2 * 2^4 = 32, 不会溢出
            int delay = schedule.nextDelaySeconds(1000);
            assertThat(delay).isLessThanOrEqualTo(60);
        }

        @Test
        @DisplayName("maxDelaySeconds = baseDelaySeconds 时延迟恒定")
        void maxEqualsBase_delayConstant() {
            ExponentialBackoffRetrySchedule schedule =
                    new ExponentialBackoffRetrySchedule(10, 10, 4);
            for (int attempt = 0; attempt < 10; attempt++) {
                assertThat(schedule.nextDelaySeconds(attempt))
                        .as("attempt=%d", attempt)
                        .isEqualTo(10);
            }
        }
    }

    // ========== 确定性测试 ==========

    @Nested
    @DisplayName("确定性")
    class Determinism {

        @Test
        @DisplayName("同一 attempt 值永远返回相同 delay")
        void sameAttempt_sameDelay() {
            ExponentialBackoffRetrySchedule schedule =
                    new ExponentialBackoffRetrySchedule(5, 120, 6);
            for (int attempt = 0; attempt < 20; attempt++) {
                int first = schedule.nextDelaySeconds(attempt);
                for (int i = 0; i < 100; i++) {
                    assertThat(schedule.nextDelaySeconds(attempt))
                            .as("attempt=%d iteration=%d", attempt, i)
                            .isEqualTo(first);
                }
            }
        }
    }

    // ========== 边界值组合 ==========

    @Test
    @DisplayName("极端参数组合不抛异常")
    void extremeParameterCombinations_noThrow() {
        // 最大 base，最小 maxExponent
        assertThat(new ExponentialBackoffRetrySchedule(3600, 3600, 0).nextDelaySeconds(0))
                .isEqualTo(3600);

        // 最小 base，最大 maxExponent
        ExponentialBackoffRetrySchedule schedule =
                new ExponentialBackoffRetrySchedule(1, Integer.MAX_VALUE / 2, 10);
        for (int i = 0; i < 20; i++) {
            assertThat(schedule.nextDelaySeconds(i)).isPositive();
        }
    }
}
