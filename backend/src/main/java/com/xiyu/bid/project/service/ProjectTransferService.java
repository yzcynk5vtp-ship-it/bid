// Input: ProjectRepository, UserRepository, TenderRepository, ProjectInitiationDetailsRepository, TenderAssignmentRecordRepository, ProjectTransferNotifier, EffectiveRoleResolver
// Output: transfer(projectId, newOwnerId, operatorId, reason) — 项目转移编排
// Pos: project/service/ - 应用服务/命令编排
// 维护声明: 仅维护转移编排；角色校验下沉到 ProjectTransferRolePolicy；通知下沉到 ProjectTransferNotifier。

package com.xiyu.bid.project.service;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.core.ProjectTransferRolePolicy;
import com.xiyu.bid.project.dto.ProjectTransferResponse;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 项目转移服务。
 * <p>
 * 投标管理员/组长可将任何状态的项目转移给新负责人。同步更新：
 * <ul>
 *   <li>{@code projects.manager_id}</li>
 *   <li>{@code project_initiation_details.owner_user_id} + {@code project_leader_name}（若存在）</li>
 *   <li>{@code tenders.project_manager_id} + {@code project_manager_name}（若 project.tender_id 存在）</li>
 * </ul>
 * 写入审计日志（{@link TenderAssignmentRecord}，type=TRANSFER）。
 * 给新负责人发站内通知（独立事务，失败不影响主转移）。
 * </p>
 * <p>
 * 旧负责人通过 {@code ProjectAccessScopeService} 实时计算自然失去访问权限
 * （无应用层缓存，下次请求即生效）。
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectTransferService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TenderRepository tenderRepository;
    private final ProjectInitiationDetailsRepository initiationDetailsRepository;
    private final TenderAssignmentRecordRepository assignmentRecordRepository;
    private final ProjectTransferNotifier notifier;
    private final EffectiveRoleResolver effectiveRoleResolver;

    /**
     * 执行项目转移。
     *
     * @param projectId     项目 ID
     * @param newOwnerId    新负责人用户 ID
     * @param operatorId    操作人用户 ID
     * @param reason        转移原因（可选）
     * @return 转移结果
     * @throws ResourceNotFoundException   如果项目或新负责人不存在
     * @throws IllegalArgumentException    如果新=旧、新负责人停用、角色不允许
     */
    public ProjectTransferResponse transfer(Long projectId, Long newOwnerId, Long operatorId, String reason) {
        // 1. 加载项目
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));

        Long oldOwnerId = project.getManagerId();
        String projectName = project.getName();

        // 2. 加载新负责人并校验
        User newOwner = userRepository.findById(newOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", newOwnerId.toString()));

        if (Boolean.FALSE.equals(newOwner.getEnabled())) {
            throw new IllegalArgumentException("新负责人账号已停用");
        }

        // 3. FR-008: 校验新负责人 != 当前负责人
        if (oldOwnerId != null && oldOwnerId.equals(newOwnerId)) {
            throw new IllegalArgumentException("新负责人与当前负责人相同，无需转移");
        }

        // 4. FR-004: 校验新负责人角色（CO-373: 走 EffectiveRoleResolver，禁止直调 User.getRoleCode()）
        String newOwnerRoleCode = effectiveRoleResolver.resolveRoleCode(newOwner);
        if (!ProjectTransferRolePolicy.isValidNewOwnerRole(newOwnerRoleCode)) {
            throw new IllegalArgumentException(
                    "新负责人必须是投标项目负责人/组长/管理员，当前角色：" + newOwnerRoleCode);
        }

        // 5. 获取旧负责人姓名（用于审计和通知）
        String oldOwnerName = resolveUserName(oldOwnerId);

        // 6. 更新 project.manager_id
        project.setManagerId(newOwnerId);
        projectRepository.save(project);

        // 7. 更新 initiationDetails（若存在）
        Optional<ProjectInitiationDetails> detailsOpt = initiationDetailsRepository.findByProjectId(projectId);
        detailsOpt.ifPresent(details -> {
            details.setOwnerUserId(newOwnerId);
            details.setProjectLeaderName(newOwner.getFullName());
            initiationDetailsRepository.save(details);
        });

        // 8. 更新 tender（若 project.tender_id 存在）
        boolean tenderSynced = false;
        Long tenderId = project.getTenderId();
        if (tenderId != null) {
            Optional<Tender> tenderOpt = tenderRepository.findById(tenderId);
            if (tenderOpt.isPresent()) {
                Tender tender = tenderOpt.get();
                tender.setProjectManagerId(newOwnerId);
                tender.setProjectManagerName(newOwner.getFullName());
                tenderRepository.save(tender);
                tenderSynced = true;

                // 9. 写审计日志（复用 TenderAssignmentRecord，type=TRANSFER）
                writeAuditRecord(tenderId, newOwnerId, newOwner.getFullName(),
                        oldOwnerId, oldOwnerName, operatorId, reason);
            }
        }

        // 10. 通知新负责人（独立事务，失败不影响主转移）
        String operatorName = resolveUserName(operatorId);
        try {
            notifier.notifyTransferred(projectId, projectName, newOwnerId, newOwner.getFullName(),
                    oldOwnerName, operatorId, operatorName);
        } catch (RuntimeException e) {
            log.warn("Project transfer notifier threw (should have been caught inside): project {}: {}",
                    projectId, e.getMessage());
        }

        log.info("Project {} transferred from {} (id={}) to {} (id={}) by operator {} (reason: {})",
                projectId, oldOwnerName, oldOwnerId, newOwner.getFullName(), newOwnerId, operatorId, reason);

        // 11. 返回响应
        return ProjectTransferResponse.builder()
                .projectId(projectId)
                .projectName(projectName)
                .oldOwnerUserId(oldOwnerId)
                .oldOwnerName(oldOwnerName)
                .newOwnerUserId(newOwnerId)
                .newOwnerName(newOwner.getFullName())
                .transferredAt(LocalDateTime.now())
                .tenderSynced(tenderSynced)
                .tenderId(tenderId)
                .build();
    }

    private void writeAuditRecord(Long tenderId, Long newOwnerId, String newOwnerName,
                                   Long oldOwnerId, String oldOwnerName,
                                   Long operatorId, String reason) {
        String remark = "项目转移: " + (oldOwnerName != null ? oldOwnerName : "无")
                + " → " + newOwnerName
                + (reason != null && !reason.isBlank() ? "（原因：" + reason + "）" : "");
        TenderAssignmentRecord record = TenderAssignmentRecord.builder()
                .tenderId(tenderId)
                .assigneeId(newOwnerId)
                .assigneeName(newOwnerName)
                .assignedById(operatorId)
                .assignedByName(resolveUserName(operatorId))
                .type(TenderAssignmentRecord.AssignmentType.TRANSFER)
                .remark(remark)
                .assignedAt(LocalDateTime.now())
                .build();
        assignmentRecordRepository.save(record);
    }

    private String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse(null);
    }
}
