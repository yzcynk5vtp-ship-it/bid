package com.xiyu.bid.task.controller;

import com.xiyu.bid.task.service.TaskExtendedFieldService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 权限测试：验证 GET /api/task-extended-fields 的 @PreAuthorize 行为。
 *
 * <p>该接口语义为"公开读取全局字段 schema"（类注释明确"公开读取"，Service 无身份维度），
 * 供前端 TaskForm 动态渲染扩展字段输入控件。因此所有已认证业务角色都应能访问（200），
 * 仅匿名请求应被拒（401）。</p>
 *
 * <p>必须用 {@code @SpringBootTest}（不能用 {@code @WebMvcTest}）：方法级
 * {@code @PreAuthorize} 依赖 {@code @EnableMethodSecurity} 激活的
 * {@code MethodSecurityInterceptor}，而 {@code @WebMvcTest} 切片不加载
 * {@link com.xiyu.bid.config.SecurityConfig}，会导致注解不生效、所有人 200，
 * 测不出真实权限行为。</p>
 *
 * <p>根因背景：2026-07-02 生产故障（traceId 50f8ae0e...），bid-otherDept 用户(09118)
 * 因方法级 {@code hasAnyRole('ADMIN','MANAGER',...)} 白名单不含 BID_OTHERDEPT 被 403。
 * 本测试是该修复的回归守卫。</p>
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskExtendedFieldSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskExtendedFieldService service;

    @Test
    @WithMockUser(roles = "BID_OTHERDEPT")
    void bidOtherDept_canListExtendedFields() throws Exception {
        when(service.listEnabled()).thenReturn(List.of());

        mockMvc.perform(get("/api/task-extended-fields"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "BID_ADMINISTRATION")
    void bidAdministration_canListExtendedFields() throws Exception {
        when(service.listEnabled()).thenReturn(List.of());

        mockMvc.perform(get("/api/task-extended-fields"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "BID_TEAM")
    void bidTeam_canListExtendedFields() throws Exception {
        when(service.listEnabled()).thenReturn(List.of());

        mockMvc.perform(get("/api/task-extended-fields"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canListExtendedFields() throws Exception {
        when(service.listEnabled()).thenReturn(List.of());

        mockMvc.perform(get("/api/task-extended-fields"))
                .andExpect(status().isOk());
    }
}

