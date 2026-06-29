package com.xiyu.bid.personnel.infrastructure.controller;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.personnel.application.service.ImportPersonnelAppService;
import com.xiyu.bid.personnel.application.service.ImportPersonnelAppService.ImportProgressInfo;
import com.xiyu.bid.personnel.domain.model.importtask.PersonnelImportTask;
import com.xiyu.bid.personnel.infrastructure.excel.PersonnelImportTemplateGenerator;
import com.xiyu.bid.security.CurrentUserResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CO-391 regression gate：bid_admin 06234 郑蓉蓉 人员证书批量导入模板下载 403 修复回归。
 *
 * <p>根因：{@link PersonnelImportController} 4 处方法级 {@code @PreAuthorize} 漏写 {@code 'admin'}
 * 字面字符串兜底，且未覆盖 OSS roleCode 漂移（如 {@code bidAdmin} 无斜杠）导致的 authority
 * 形式 {@code ROLE_BIDADMIN} 兜底。CO-363/CO-369 修了 {@code PersonnelController} 但漏掉 ImportController。
 *
 * <p>修复后注解放宽为：
 * <ul>
 *   <li>POST /import、GET /import/{taskId}、GET /import/{taskId}/report：
 *       {@code hasAnyAuthority('admin', '/bidAdmin', 'bid-TeamLeader',
 *       'ROLE_BIDADMIN', 'ROLE_BID_TEAMLEADER')}</li>
 *   <li>GET /import/template：
 *       {@code hasAnyAuthority('admin', '/bidAdmin', 'bid-TeamLeader', 'bid-Team',
 *       'ROLE_BIDADMIN', 'ROLE_BID_TEAMLEADER', 'ROLE_BID_TEAM')}</li>
 * </ul>
 *
 * <p>测试以 {@code @WithMockUser(authorities=...)} 模拟不同 authority 组合，
 * 断言 4 个端点在规范 roleCode、漂移 roleCode、无权限 3 种场景下的鉴权结果。
 */
@WebMvcTest(controllers = PersonnelImportController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                ApiKeyAuthenticationFilter.class}
))
@Import(PersonnelImportControllerSecurityTest.TestSecurityConfig.class)
class PersonnelImportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ImportPersonnelAppService importAppService;

    @MockBean
    private PersonnelImportTemplateGenerator templateGenerator;

    // CO-373 回归修复：CurrentUserResolver 依赖链在 @WebMvcTest 切片不实例化，
    // TraceFilter(@Component) 强依赖它，需 mock 以避免上下文加载失败。
    @MockBean
    private CurrentUserResolver currentUserResolver;

    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            // permitAll 让鉴权完全由 @PreAuthorize 决定，与生产方法安全语义一致
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    // ==================== GET /api/knowledge/personnel/import/template ====================

    @Test
    @DisplayName("规范roleCode(/bidAdmin) GET /import/template → 200")
    @WithMockUser(authorities = {"/bidAdmin"})
    void downloadTemplate_shouldSucceed_forCanonicalBidAdmin() throws Exception {
        when(templateGenerator.generate()).thenReturn(new byte[]{0x50, 0x4B});
        mockMvc.perform(get("/api/knowledge/personnel/import/template"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("漂移roleCode(ROLE_BIDADMIN) GET /import/template → 200（CO-391 修复后通过，修复前 403）")
    @WithMockUser(authorities = {"ROLE_BIDADMIN"})
    void downloadTemplate_shouldSucceed_forDriftedBidAdminAuthority() throws Exception {
        when(templateGenerator.generate()).thenReturn(new byte[]{0x50, 0x4B});
        mockMvc.perform(get("/api/knowledge/personnel/import/template"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("admin字面字符串 GET /import/template → 200")
    @WithMockUser(authorities = {"admin"})
    void downloadTemplate_shouldSucceed_forAdminLiteral() throws Exception {
        when(templateGenerator.generate()).thenReturn(new byte[]{0x50, 0x4B});
        mockMvc.perform(get("/api/knowledge/personnel/import/template"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("投标专员(bid-Team) GET /import/template → 200")
    @WithMockUser(authorities = {"bid-Team"})
    void downloadTemplate_shouldSucceed_forBidTeam() throws Exception {
        when(templateGenerator.generate()).thenReturn(new byte[]{0x50, 0x4B});
        mockMvc.perform(get("/api/knowledge/personnel/import/template"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无权限用户 GET /import/template → 403")
    @WithMockUser(authorities = {})
    void downloadTemplate_shouldReturn403_forNoPermission() throws Exception {
        mockMvc.perform(get("/api/knowledge/personnel/import/template"))
                .andExpect(status().isForbidden());
    }

    // ==================== POST /api/knowledge/personnel/import ====================

    @Test
    @DisplayName("规范roleCode(/bidAdmin) POST /import → 202")
    @WithMockUser(authorities = {"/bidAdmin"})
    void startImport_shouldSucceed_forCanonicalBidAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4B});
        PersonnelImportTask task = PersonnelImportTask.createNew("IMP-PER-TEST", 1L);
        when(importAppService.initiateImportTask(anyLong(), anyString())).thenReturn(task);
        mockMvc.perform(multipart("/api/knowledge/personnel/import").file(file))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("漂移roleCode(ROLE_BIDADMIN) POST /import → 202（CO-391 修复后通过）")
    @WithMockUser(authorities = {"ROLE_BIDADMIN"})
    void startImport_shouldSucceed_forDriftedBidAdminAuthority() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4B});
        PersonnelImportTask task = PersonnelImportTask.createNew("IMP-PER-TEST", 1L);
        when(importAppService.initiateImportTask(anyLong(), anyString())).thenReturn(task);
        mockMvc.perform(multipart("/api/knowledge/personnel/import").file(file))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("无权限用户 POST /import → 403")
    @WithMockUser(authorities = {})
    void startImport_shouldReturn403_forNoPermission() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4B});
        mockMvc.perform(multipart("/api/knowledge/personnel/import").file(file))
                .andExpect(status().isForbidden());
    }

    // ==================== GET /api/knowledge/personnel/import/{taskId} ====================

    @Test
    @DisplayName("规范roleCode(/bidAdmin) GET /import/{taskId} → 200")
    @WithMockUser(authorities = {"/bidAdmin"})
    void getImportProgress_shouldSucceed_forCanonicalBidAdmin() throws Exception {
        when(importAppService.getProgress(anyLong())).thenReturn(
                new ImportProgressInfo("PROCESSING", 0, "处理中", 0, 0, 0));
        mockMvc.perform(get("/api/knowledge/personnel/import/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("漂移roleCode(ROLE_BIDADMIN) GET /import/{taskId} → 200（CO-391 修复后通过）")
    @WithMockUser(authorities = {"ROLE_BIDADMIN"})
    void getImportProgress_shouldSucceed_forDriftedBidAdminAuthority() throws Exception {
        when(importAppService.getProgress(anyLong())).thenReturn(
                new ImportProgressInfo("PROCESSING", 0, "处理中", 0, 0, 0));
        mockMvc.perform(get("/api/knowledge/personnel/import/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无权限用户 GET /import/{taskId} → 403")
    @WithMockUser(authorities = {})
    void getImportProgress_shouldReturn403_forNoPermission() throws Exception {
        mockMvc.perform(get("/api/knowledge/personnel/import/1"))
                .andExpect(status().isForbidden());
    }

    // ==================== GET /api/knowledge/personnel/import/{taskId}/report ====================

    @Test
    @DisplayName("规范roleCode(/bidAdmin) GET /import/{taskId}/report → 200")
    @WithMockUser(authorities = {"/bidAdmin"})
    void downloadErrorReport_shouldSucceed_forCanonicalBidAdmin() throws Exception {
        when(importAppService.getErrorReport(anyLong())).thenReturn(new byte[]{0x50, 0x4B});
        mockMvc.perform(get("/api/knowledge/personnel/import/1/report"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("漂移roleCode(ROLE_BIDADMIN) GET /import/{taskId}/report → 200（CO-391 修复后通过）")
    @WithMockUser(authorities = {"ROLE_BIDADMIN"})
    void downloadErrorReport_shouldSucceed_forDriftedBidAdminAuthority() throws Exception {
        when(importAppService.getErrorReport(anyLong())).thenReturn(new byte[]{0x50, 0x4B});
        mockMvc.perform(get("/api/knowledge/personnel/import/1/report"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无权限用户 GET /import/{taskId}/report → 403")
    @WithMockUser(authorities = {})
    void downloadErrorReport_shouldReturn403_forNoPermission() throws Exception {
        mockMvc.perform(get("/api/knowledge/personnel/import/1/report"))
                .andExpect(status().isForbidden());
    }
}
