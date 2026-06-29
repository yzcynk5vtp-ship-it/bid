package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.core.ClosureReviewAuthorizationPolicy;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.security.CurrentUserResolver;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 结项审核权限守卫（CO-403 纠偏后的职责分离 + 项目级投标辅助校验）。
 * <p>编排层：项目访问守卫 → 取当前用户 → 取项目级负责人分配 → 委托纯核心 {@link ClosureReviewAuthorizationPolicy} 判定 →
 * 失败抛 {@link ResponseStatusException}(403)。</p>
 *
 * <p>与 {@link com.xiyu.bid.task.service.TaskPermissionGuard} 同范式：
 * Controller {@code @PreAuthorize} 做粗粒度角色白名单，本 Guard 做细粒度身份校验。</p>
 */
@Service
@RequiredArgsConstructor
class ProjectClosurePermissionGuard {

    private final CurrentUserResolver currentUserResolver;
    private final ProjectLeadAssignmentRepository projectLeadAssignmentRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    /**
     * 断言当前用户可审核该项目结项（通过/驳回）。
     * <p>顺带补齐 approve/reject 缺失的项目访问校验（preview 原有，approve/reject 此前只有 mustGetProject）。</p>
     *
     * @param projectId 项目 ID
     * @throws ResponseStatusException 403 当当前用户无权访问该项目，或无审核权（非管理员/组长，且非该项目的
     *                                  投标负责人/辅助，或为结项提交人触发职责分离拦截）
     */
    void assertCanReviewClosure(Long projectId) {
        // 项目访问守卫：先确认当前用户有权访问该项目（架构守卫要求 + 防越权）
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        User currentUser = currentUserResolver.requireCurrentUser();
        ProjectLeadAssignment lead = projectLeadAssignmentRepository.findByProjectId(projectId).orElse(null);
        ClosureReviewAuthorizationPolicy.Decision decision = ClosureReviewAuthorizationPolicy.canReviewClosure(
                currentUserResolver.resolveEffectiveRoleCode(currentUser),
                currentUser.getId(),
                lead
        );
        if (!decision.allowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, decision.reason());
        }
    }
}
