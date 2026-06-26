package com.xiyu.bid.audit.service;

import com.xiyu.bid.audit.service.AuditLogService.AuditLogEntry;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.domain.ProjectStageTransitionedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * CO-324：阶段推进审计 listener —— 验证 description 含源+目标阶段中文名、操作人取当前登录用户。
 */
class ProjectStageTransitionedAuditListenerTest {

    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final ProjectStageTransitionedAuditListener listener =
            new ProjectStageTransitionedAuditListener(auditLogService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void description_containsFromAndToStageDisplayNames() {
        loginAs("04083");
        listener.onStageTransitioned(
                new ProjectStageTransitionedEvent(437L, ProjectStage.EVALUATING, ProjectStage.RESULT_PENDING));

        AuditLogEntry entry = captureEntry();
        assertThat(entry.getDescription()).isEqualTo("从 评标 推进至 结果确认");
        assertThat(entry.getAction()).isEqualTo("PROJECT_STAGE_TRANSITIONED");
        assertThat(entry.getEntityType()).isEqualTo("Project");
        assertThat(entry.getProjectId()).isEqualTo(437L);
        assertThat(entry.getEntityId()).isEqualTo("437");
    }

    @Test
    void operator_takenFromSecurityContext_notProjectManagerOrName() {
        loginAs("user04083");
        listener.onStageTransitioned(
                new ProjectStageTransitionedEvent(1L, ProjectStage.DRAFTING, ProjectStage.EVALUATING));

        AuditLogEntry entry = captureEntry();
        assertThat(entry.getUserId()).isEqualTo("user04083");
        assertThat(entry.getUsername()).isEqualTo("user04083");
    }

    @Test
    void operator_fallsBackToSystem_whenNoAuthentication() {
        SecurityContextHolder.clearContext();
        listener.onStageTransitioned(
                new ProjectStageTransitionedEvent(1L, ProjectStage.INITIATED, ProjectStage.DRAFTING));

        AuditLogEntry entry = captureEntry();
        assertThat(entry.getUserId()).isEqualTo("system");
    }

    private AuditLogEntry captureEntry() {
        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogService).log(captor.capture());
        return captor.getValue();
    }

    private static void loginAs(String user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
