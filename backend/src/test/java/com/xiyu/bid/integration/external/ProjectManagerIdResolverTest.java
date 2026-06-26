package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * CO-333: {@link ProjectManagerIdResolver} 单元测试。
 *
 * <p>覆盖唯一匹配/无匹配/重名/null/空字符串 5 个场景，
 * 与 {@link TenderIntegrationServiceMapToEntityTest} 中 mock resolver 的用例互补：
 * mapper 侧验证"调用 resolver + 写入 id"，本类验证 resolver 内部匹配规则。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CO-333: ProjectManagerIdResolver 姓名匹配规则")
class ProjectManagerIdResolverTest {

    @Mock private UserRepository userRepository;
    private ProjectManagerIdResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ProjectManagerIdResolver(userRepository);
    }

    @Test
    @DisplayName("唯一匹配 → 返回 user_id")
    void resolveByFullName_uniqueMatch_returnsId() {
        when(userRepository.findByFullName("韩超"))
                .thenReturn(List.of(User.builder().id(25L).fullName("韩超").build()));

        assertThat(resolver.resolveByFullName("韩超")).isEqualTo(25L);
    }

    @Test
    @DisplayName("无匹配 → 返回 null（不阻断主流程）")
    void resolveByFullName_noMatch_returnsNull() {
        when(userRepository.findByFullName("不存在的人")).thenReturn(List.of());

        assertThat(resolver.resolveByFullName("不存在的人")).isNull();
    }

    @Test
    @DisplayName("重名 → 返回 null 避免误绑")
    void resolveByFullName_duplicateName_returnsNull() {
        when(userRepository.findByFullName("张伟")).thenReturn(List.of(
                User.builder().id(1L).fullName("张伟").build(),
                User.builder().id(2L).fullName("张伟").build()));

        assertThat(resolver.resolveByFullName("张伟")).isNull();
    }

    @Test
    @DisplayName("null 全名 → 返回 null（不查库）")
    void resolveByFullName_null_returnsNull() {
        assertThat(resolver.resolveByFullName(null)).isNull();
    }

    @Test
    @DisplayName("空白全名 → 返回 null（不查库）")
    void resolveByFullName_blank_returnsNull() {
        assertThat(resolver.resolveByFullName("   ")).isNull();
    }

    @Test
    @DisplayName("前后带空格的全名 → trim 后查库")
    void resolveByFullName_padded_trimsBeforeLookup() {
        when(userRepository.findByFullName("韩超"))
                .thenReturn(List.of(User.builder().id(25L).fullName("韩超").build()));

        assertThat(resolver.resolveByFullName("  韩超  ")).isEqualTo(25L);
    }
}
