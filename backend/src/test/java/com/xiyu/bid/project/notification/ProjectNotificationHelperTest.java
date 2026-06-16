// Output: 每个通知方法的分支覆盖 — project=null / 成员空 / 正常路径
// Pos: project/notification/ - 纯编排层测试
package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectNotificationHelper — 通知辅助方法")
class ProjectNotificationHelperTest {

    @Mock
    private NotificationApplicationService notificationService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Captor
    private ArgumentCaptor<CreateNotificationRequest> requestCaptor;

    private ProjectNotificationHelper helper;

    private static final Long PID = 100L;
    private static final Long UID = 42L;
    private static final Long MANAGER_ID = 88L;

    @BeforeEach
    void setUp() {
        helper = new ProjectNotificationHelper(notificationService, projectRepository, userRepository, projectMemberRepository);
    }

    private Project project(String name) {
        Project p = new Project();
        p.setId(PID);
        p.setName(name);
        p.setManagerId(MANAGER_ID);
        return p;
    }

    private User user(Long id, String fullName) {
        User u = new User();
        u.setId(id);
        u.setFullName(fullName);
        return u;
    }

    private ProjectMember member(Long userId, String permissionLevel) {
        ProjectMember m = new ProjectMember();
        m.setUserId(userId);
        m.setPermissionLevel(permissionLevel);
        return m;
    }

    @Nested
    @DisplayName("notifyInitiationSubmitted")
    class InitiationSubmitted {

        @Test
        @DisplayName("project found → sends APPROVAL notification to admins")
        void sendsToAdmins() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findById(UID)).thenReturn(Optional.of(user(UID, "张三")));
            when(userRepository.findEnabledByRoleProfileCodes(List.of("admin", "bid_admin", "bid_lead", "bid_senior")))
                    .thenReturn(List.of(user(1L, "张三"), user(2L, "李四")));

            helper.notifyInitiationSubmitted(PID, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            CreateNotificationRequest req = requestCaptor.getValue();
            assertThat(req.type()).isEqualTo("APPROVAL");
            assertThat(req.title()).contains("立项审核：项目提交立项审核");
            assertThat(req.recipientUserIds()).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("project not found → no notification")
        void skipsWhenProjectNotFound() {
            when(projectRepository.findById(PID)).thenReturn(Optional.empty());

            helper.notifyInitiationSubmitted(PID, UID);

            verify(notificationService, never()).createNotification(any(), any());
        }

        @Test
        @DisplayName("exception during notification → caught silently")
        void catchesException() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findById(UID)).thenReturn(Optional.of(user(UID, "张三")));
            when(userRepository.findEnabledByRoleProfileCodes(any()))
                    .thenThrow(new RuntimeException("DB down"));

            helper.notifyInitiationSubmitted(PID, UID);
            // no exception propagated
        }
    }

    @Nested
    @DisplayName("notifyInitiationApproved")
    class InitiationApproved {

        @Test
        @DisplayName("project found → sends INFO to manager + leads")
        void sendsToManagerAndLeads() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findById(UID)).thenReturn(Optional.of(user(UID, "审核人")));
            when(projectMemberRepository.findByProjectId(PID))
                    .thenReturn(List.of(member(MANAGER_ID, "LEAD"), member(99L, "ADMIN"), member(77L, "VIEWER")));

            helper.notifyInitiationApproved(PID, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            CreateNotificationRequest req = requestCaptor.getValue();
            assertThat(req.type()).isEqualTo("INFO");
            assertThat(req.title()).contains("立项审核通过");
            // managerId(88) + LEAD(88) + ADMIN(99), dedup: 88,99
            assertThat(req.recipientUserIds()).containsExactlyInAnyOrder(88L, 99L);
        }

        @Test
        @DisplayName("no manager and no leads → empty recipient list sent")
        void sendsEmptyWhenNoManagerOrLeads() {
            Project p = project("测试项目");
            p.setManagerId(null);
            when(projectRepository.findById(PID)).thenReturn(Optional.of(p));
            when(userRepository.findById(UID)).thenReturn(Optional.of(user(UID, "审核人")));
            when(projectMemberRepository.findByProjectId(PID)).thenReturn(List.of());

            helper.notifyInitiationApproved(PID, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().recipientUserIds()).isEmpty();
        }

        @Test
        @DisplayName("project not found → no notification")
        void skipsWhenProjectNotFound() {
            when(projectRepository.findById(PID)).thenReturn(Optional.empty());

            helper.notifyInitiationApproved(PID, UID);

            verify(notificationService, never()).createNotification(any(), any());
        }
    }

    @Nested
    @DisplayName("notifyInitiationRejected")
    class InitiationRejected {

        @Test
        @DisplayName("project found → sends INFO with reason to manager")
        void sendsToManagerWithReason() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findById(UID)).thenReturn(Optional.of(user(UID, "审核人")));

            helper.notifyInitiationRejected(PID, UID, "缺少预算");

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            CreateNotificationRequest req = requestCaptor.getValue();
            assertThat(req.type()).isEqualTo("INFO");
            assertThat(req.title()).contains("立项审核驳回");
            assertThat(req.body()).contains("缺少预算");
            assertThat(req.recipientUserIds()).containsExactly(MANAGER_ID);
        }

        @Test
        @DisplayName("no manager → empty recipient list")
        void sendsEmptyWhenNoManager() {
            Project p = project("测试项目");
            p.setManagerId(null);
            when(projectRepository.findById(PID)).thenReturn(Optional.of(p));
            when(userRepository.findById(UID)).thenReturn(Optional.of(user(UID, "审核人")));

            helper.notifyInitiationRejected(PID, UID, "资料不全");

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().recipientUserIds()).isEmpty();
        }

        @Test
        @DisplayName("project not found → no notification")
        void skipsWhenProjectNotFound() {
            when(projectRepository.findById(PID)).thenReturn(Optional.empty());

            helper.notifyInitiationRejected(PID, UID, "理由");

            verify(notificationService, never()).createNotification(any(), any());
        }
    }

    @Nested
    @DisplayName("notifyStageTransition")
    class StageTransition {

        @Test
        @DisplayName("project found with team members → sends INFO to all members")
        void sendsToAllTeamMembers() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(projectMemberRepository.findByProjectId(PID))
                    .thenReturn(List.of(member(1L, "VIEWER"), member(2L, "EDITOR")));

            helper.notifyStageTransition(PID, ProjectStage.DRAFTING, ProjectStage.EVALUATING);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(null));
            CreateNotificationRequest req = requestCaptor.getValue();
            assertThat(req.type()).isEqualTo("INFO");
            assertThat(req.title()).contains("项目阶段变更");
            assertThat(req.recipientUserIds()).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("no team members → no notification")
        void skipsWhenNoTeamMembers() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(projectMemberRepository.findByProjectId(PID)).thenReturn(List.of());

            helper.notifyStageTransition(PID, ProjectStage.DRAFTING, ProjectStage.EVALUATING);

            verify(notificationService, never()).createNotification(any(), any());
        }

        @Test
        @DisplayName("project not found → no notification")
        void skipsWhenProjectNotFound() {
            when(projectRepository.findById(PID)).thenReturn(Optional.empty());

            helper.notifyStageTransition(PID, ProjectStage.DRAFTING, ProjectStage.EVALUATING);

            verify(notificationService, never()).createNotification(any(), any());
        }

        @Test
        @DisplayName("exception during transition notification → caught silently")
        void catchesException() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(projectMemberRepository.findByProjectId(PID))
                    .thenThrow(new RuntimeException("DB error"));

            helper.notifyStageTransition(PID, ProjectStage.DRAFTING, ProjectStage.EVALUATING);
            // no exception propagated
        }
    }
}
