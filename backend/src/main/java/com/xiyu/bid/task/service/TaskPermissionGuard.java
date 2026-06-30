package com.xiyu.bid.task.service;

import com.xiyu.bid.common.domain.AuthorizationDecision;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.security.CurrentUserResolver;
import com.xiyu.bid.task.core.TaskOperationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class TaskPermissionGuard {

    private final CurrentUserResolver currentUserResolver;
    private final ProjectLeadAssignmentRepository projectLeadAssignmentRepository;
    private final ProjectInitiationDetailsRepository projectInitiationDetailsRepository;

    void assertCanManageTask(Long projectId) {
        User currentUser = currentUserResolver.requireCurrentUser();

        // CO-361: 项目立项负责人（owner_user_id）享有与 primaryLead 同等的任务管理权限
        if (isProjectOwner(projectId, currentUser.getId())) {
            return;
        }

        Long[] leadIds = resolveProjectLeadIds(projectId);
        AuthorizationDecision decision = TaskOperationPolicy.canManageTask(
                currentUserResolver.resolveEffectiveRoleCode(currentUser),
                currentUser.getId(),
                leadIds[0],
                leadIds[1]
        );
        if (!decision.allowed()) {
            throw new AccessDeniedException(decision.reason());
        }
    }

    void assertCanForceReassign(Long projectId) {
        User currentUser = currentUserResolver.requireCurrentUser();
        Long[] leadIds = resolveProjectLeadIds(projectId);
        AuthorizationDecision decision = TaskOperationPolicy.canForceReassign(
                currentUserResolver.resolveEffectiveRoleCode(currentUser),
                currentUser.getId(),
                leadIds[0],
                leadIds[1]
        );
        if (!decision.allowed()) {
            throw new AccessDeniedException(decision.reason());
        }
    }

    void assertCanAssignTask(Long projectId, boolean forceReassign) {
        if (forceReassign) {
            assertCanForceReassign(projectId);
        } else {
            assertCanManageTask(projectId);
        }
    }

    void assertCanManageOrSubmitTask(Task task) {
        User currentUser = currentUserResolver.requireCurrentUser();

        // CO-361: 项目立项负责人（owner_user_id）享有与 primaryLead 同等的任务管理权限
        if (isProjectOwner(task.getProjectId(), currentUser.getId())) {
            return;
        }

        Long[] leadIds = resolveProjectLeadIds(task.getProjectId());
        AuthorizationDecision manageDecision = TaskOperationPolicy.canManageTask(
                currentUserResolver.resolveEffectiveRoleCode(currentUser),
                currentUser.getId(),
                leadIds[0],
                leadIds[1]
        );
        if (manageDecision.allowed()) {
            return;
        }
        AuthorizationDecision submitDecision = TaskOperationPolicy.canActAsAssignee(
                task.getAssigneeId(),
                currentUser.getId()
        );
        if (!submitDecision.allowed()) {
            throw new AccessDeniedException(submitDecision.reason());
        }
    }

    void assertCanTransitionTaskStatus(Task task, Task.Status targetStatus) {
        User currentUser = currentUserResolver.requireCurrentUser();
        if (targetStatus == Task.Status.REVIEW) {
            AuthorizationDecision decision = TaskOperationPolicy.canActAsAssignee(
                    task.getAssigneeId(),
                    currentUser.getId()
            );
            if (!decision.allowed()) {
                throw new AccessDeniedException(decision.reason());
            }
        } else if (targetStatus == Task.Status.COMPLETED || targetStatus == Task.Status.TODO) {
            // 职责分离：不能审核自己提交的任务
            if (currentUser.getId().equals(task.getAssigneeId())) {
                throw new AccessDeniedException("不能审核自己提交的任务");
            }

            // CO-361: 项目立项负责人（owner_user_id）享有与 primaryLead 同等的任务审核权限
            if (isProjectOwner(task.getProjectId(), currentUser.getId())) {
                return;
            }

            Long[] leadIds = resolveProjectLeadIds(task.getProjectId());
            AuthorizationDecision decision = TaskOperationPolicy.canReviewTask(
                    currentUserResolver.resolveEffectiveRoleCode(currentUser),
                    currentUser.getId(),
                    leadIds[0],
                    leadIds[1],
                    task.getAssigneeId()
            );
            if (!decision.allowed()) {
                throw new AccessDeniedException(decision.reason());
            }
        }
    }

    private Long[] resolveProjectLeadIds(Long projectId) {
        return projectLeadAssignmentRepository.resolveLeadIdsByProjectId(projectId);
    }

    /**
     * CO-361: 检查当前用户是否为项目立项负责人（owner_user_id）。
     * 项目立项负责人享有与 primaryLead 同等的任务管理权限。
     */
    private boolean isProjectOwner(Long projectId, Long userId) {
        if (projectId == null || userId == null) {
            return false;
        }
        return projectInitiationDetailsRepository.findByProjectId(projectId)
                .map(details -> userId.equals(details.getOwnerUserId()))
                .orElse(false);
    }
}
