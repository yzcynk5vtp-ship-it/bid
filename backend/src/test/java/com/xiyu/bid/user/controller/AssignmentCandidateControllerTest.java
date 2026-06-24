package com.xiyu.bid.user.controller;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.user.dto.AssignmentCandidateDTO;
import com.xiyu.bid.user.service.AssignmentCandidateAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AssignmentCandidateController HTTP 契约测试（TDD Red 阶段）。
 *
 * <p>被测类 {@link AssignmentCandidateController} 尚未实现，本测试编译失败属于预期行为。
 */
@WebMvcTest(controllers = AssignmentCandidateController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                        ApiKeyAuthenticationFilter.class}
        ))
@AutoConfigureMockMvc(addFilters = false)
class AssignmentCandidateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssignmentCandidateAppService appService;

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /api/users/assignable-candidates?context=task 返回 200")
    void findCandidates_TaskContext_Returns200() throws Exception {
        when(appService.findCandidates(any(), any())).thenReturn(List.of(
                new AssignmentCandidateDTO(2L, "张三", "E001", "bid_admin", "投标管理员", "D1", "一部", true)
        ));

        mockMvc.perform(get("/api/users/assignable-candidates").param("context", "task"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].userId").value(2))
                .andExpect(jsonPath("$.data[0].name").value("张三"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /api/users/assignable-candidates?context=tender 返回 200")
    void findCandidates_TenderContext_Returns200() throws Exception {
        when(appService.findCandidates(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/users/assignable-candidates").param("context", "tender"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /api/users/assignable-candidates（无 context）返回 400")
    void findCandidates_MissingContext_Returns400() throws Exception {
        mockMvc.perform(get("/api/users/assignable-candidates"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /api/users/assignable-candidates?context=invalid 返回 400")
    void findCandidates_InvalidContext_Returns400() throws Exception {
        mockMvc.perform(get("/api/users/assignable-candidates").param("context", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("未认证请求返回 401")
    void findCandidates_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/users/assignable-candidates").param("context", "task"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /api/users/assignable-candidates?context=task&deptCode=BID_DEPT 返回 200")
    void findCandidates_TaskContextWithDeptCode_Returns200() throws Exception {
        when(appService.findCandidates(any(), any())).thenReturn(List.of(
                new AssignmentCandidateDTO(2L, "张三", "E001", "bid_admin", "投标管理员", "BID_DEPT", "投标部", true)
        ));

        mockMvc.perform(get("/api/users/assignable-candidates")
                        .param("context", "task")
                        .param("deptCode", "BID_DEPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].deptCode").value("BID_DEPT"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /api/users/assignable-candidates?context=task&roleCode=bid_admin 返回 200")
    void findCandidates_TaskContextWithRoleCode_Returns200() throws Exception {
        when(appService.findCandidates(any(), any())).thenReturn(List.of(
                new AssignmentCandidateDTO(2L, "张三", "E001", "bid_admin", "投标管理员", "D1", "一部", true)
        ));

        mockMvc.perform(get("/api/users/assignable-candidates")
                        .param("context", "task")
                        .param("roleCode", "bid_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].roleCode").value("bid_admin"));
    }
}
