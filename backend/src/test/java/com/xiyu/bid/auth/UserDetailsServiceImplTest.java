package com.xiyu.bid.auth;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void unregisteredCustomRoleShouldNotInheritLegacyStaffAuthority() {
        // 未注册 roleCode（legal-reviewer）不应继承 STAFF 兼容，避免误入 hasAnyRole(... 'STAFF' ...) 白名单
        User user = userWithRoleProfile("legal", User.Role.STAFF, "legal-reviewer");
        when(userRepository.findByUsername("legal")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("legal");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("legal-reviewer", "ROLE_LEGAL-REVIEWER")
                .doesNotContain("ROLE_STAFF", "bidding", "project", "knowledge");
    }

    @Test
    void bidOtherDeptShouldNotInheritStaffButKeepOwnCodeAndTaskPermissions() {
        // bid_other_dept（跨部门协同人员）按蓝图不应访问标讯/项目/知识库 → 不继承 ROLE_STAFF；
        // 但保留 ROLE_BID_OTHER_DEPT + catalog 的 task 权限（任务 API 用 isAuthenticated，仍可用）
        User user = userWithRoleProfile("hanhui", User.Role.STAFF, RoleProfileCatalog.BID_OTHER_DEPT_CODE);
        when(userRepository.findByUsername("hanhui")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("hanhui");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains(RoleProfileCatalog.BID_OTHER_DEPT_CODE, "ROLE_BID_OTHER_DEPT",
                        "task.view.own", "task.handle.own")
                .doesNotContain("ROLE_STAFF", "bidding", "project", "knowledge", "resource");
    }

    @Test
    void legacyUserWithoutRoleProfileShouldStillGetStaffAuthority() {
        // roleCode 为 null 的纯 Legacy 用户（仅 users.role=STAFF）不受影响，保留 ROLE_STAFF
        User user = User.builder()
                .username("legacy")
                .password("{noop}password")
                .email("legacy@example.com")
                .fullName("legacy")
                .role(User.Role.STAFF)
                .enabled(true)
                .build();
        when(userRepository.findByUsername("legacy")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("legacy");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("ROLE_STAFF");
    }

    @Test
    void unregisteredRoleWithDbMenuPermissionsShouldRespectDbConfig() {
        // 未注册角色保留 DB 显式 menu_permissions（管理员授权），但不继承 STAFF，也不 fallback staff 全套
        RoleProfile roleProfile = RoleProfile.builder()
                .code("vendor-user")
                .name("vendor-user")
                .build();
        roleProfile.setMenuPermissions(java.util.List.of("custom.perm"));
        User user = User.builder()
                .username("vendor")
                .password("{noop}password")
                .email("vendor@example.com")
                .fullName("vendor")
                .role(User.Role.STAFF)
                .roleProfile(roleProfile)
                .enabled(true)
                .build();
        when(userRepository.findByUsername("vendor")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("vendor");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("custom.perm", "vendor-user", "ROLE_VENDOR-USER")
                .doesNotContain("ROLE_STAFF", "bidding");
    }

    @Test
    void auditorRoleProfileShouldAddAuditorAuthorityWithoutChangingLegacyRole() {
        User user = userWithRoleProfile("auditor", User.Role.STAFF, RoleProfileCatalog.AUDITOR_CODE);
        when(userRepository.findByUsername("auditor")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("auditor");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("ROLE_STAFF", "ROLE_AUDITOR", "auditor", "ROLE_AUDITOR");
    }


    @Test
    void bidAdminShouldHaveRoleAdminCompatibility() {
        User user = userWithRoleProfile("bid_admin", User.Role.STAFF, "bid_admin");
        when(userRepository.findByUsername("bid_admin")).thenReturn(Optional.of(user));
        UserDetails details = userDetailsService.loadUserByUsername("bid_admin");
        assertThat(details.getAuthorities()).extracting("authority").contains("ROLE_ADMIN", "ROLE_BID_ADMIN");
    }

    @Test
    void salesShouldHaveRoleManagerCompatibility() {
        User user = userWithRoleProfile("sales_user", User.Role.STAFF, "sales");
        when(userRepository.findByUsername("sales_user")).thenReturn(Optional.of(user));
        UserDetails details = userDetailsService.loadUserByUsername("sales_user");
        assertThat(details.getAuthorities()).extracting("authority").contains("ROLE_MANAGER");
    }

    @Test
    void bidSpecialistShouldHaveRoleStaffCompatibility() {
        User user = userWithRoleProfile("spec_user", User.Role.STAFF, "bid_specialist");
        when(userRepository.findByUsername("spec_user")).thenReturn(Optional.of(user));
        UserDetails details = userDetailsService.loadUserByUsername("spec_user");
        assertThat(details.getAuthorities()).extracting("authority").contains("ROLE_STAFF");
    }

    private User userWithRoleProfile(String username, User.Role role, String roleCode) {
        RoleProfile roleProfile = RoleProfile.builder()
                .code(roleCode)
                .name(roleCode)
                .build();
        return User.builder()
                .username(username)
                .password("{noop}password")
                .email(username + "@example.com")
                .fullName(username)
                .role(role)
                .roleProfile(roleProfile)
                .enabled(true)
                .build();
    }
}
