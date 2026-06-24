// Input: RoleProfile 实体（含 OSS 规范角色码）
// Output: 验证 User.getRoleCode() 保留原始大小写与斜杠
// Pos: Test/实体单元测试
package com.xiyu.bid.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User 实体单元测试。
 * <p>重点验证 {@link User#getRoleCode()} 保留 OSS 角色码的原始大小写与前导斜杠，
 * 不做 lower-case 归一化（OSS 角色码大小写敏感，如 /bidAdmin、bid-TeamLeader）。</p>
 */
class UserTest {

    @Test
    @DisplayName("getRoleCode 保留 /bidAdmin（带前导斜杠的 OSS 规范码）")
    void getRoleCode_preservesBidAdminWithSlash() {
        RoleProfile role = RoleProfile.builder()
                .code("/bidAdmin")
                .name("投标管理员")
                .build();
        User user = User.builder()
                .username("test")
                .password("pass")
                .email("test@example.com")
                .fullName("测试")
                .role(User.Role.MANAGER)
                .roleProfile(role)
                .build();

        assertThat(user.getRoleCode()).isEqualTo("/bidAdmin");
    }

    @Test
    @DisplayName("getRoleCode 保留 bid-TeamLeader（大小写敏感）")
    void getRoleCode_preservesBidTeamLeaderCase() {
        RoleProfile role = RoleProfile.builder()
                .code("bid-TeamLeader")
                .name("投标组长")
                .build();
        User user = User.builder()
                .username("test")
                .password("pass")
                .email("test@example.com")
                .fullName("测试")
                .role(User.Role.MANAGER)
                .roleProfile(role)
                .build();

        assertThat(user.getRoleCode()).isEqualTo("bid-TeamLeader");
    }

    @Test
    @DisplayName("getRoleCode 保留 bid-Team（大小写敏感）")
    void getRoleCode_preservesBidTeamCase() {
        RoleProfile role = RoleProfile.builder()
                .code("bid-Team")
                .name("投标专员")
                .build();
        User user = User.builder()
                .username("test")
                .password("pass")
                .email("test@example.com")
                .fullName("测试")
                .role(User.Role.MANAGER)
                .roleProfile(role)
                .build();

        assertThat(user.getRoleCode()).isEqualTo("bid-Team");
    }

    @Test
    @DisplayName("getRoleCode 在 roleProfile 为 null 时回退到 role 枚举小写名")
    void getRoleCode_nullRoleProfile_fallsBackToRoleEnum() {
        User user = User.builder()
                .username("test")
                .password("pass")
                .email("test@example.com")
                .fullName("测试")
                .role(User.Role.ADMIN)
                .roleProfile(null)
                .build();

        assertThat(user.getRoleCode()).isEqualTo("admin");
    }

    @Test
    @DisplayName("getRoleCode 在 roleProfile.code 为空白时回退到 role 枚举小写名")
    void getRoleCode_blankRoleProfileCode_fallsBackToRoleEnum() {
        RoleProfile role = RoleProfile.builder()
                .code("   ")
                .name("空角色码")
                .build();
        User user = User.builder()
                .username("test")
                .password("pass")
                .email("test@example.com")
                .fullName("测试")
                .role(User.Role.MANAGER)
                .roleProfile(role)
                .build();

        assertThat(user.getRoleCode()).isEqualTo("manager");
    }
}
