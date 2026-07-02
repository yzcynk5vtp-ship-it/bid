// Input: ProjectTransferNotifier 调用
// Output: 发送站内通知给新负责人（失败不影响调用方事务）
// Pos: project/service/ - 通知外壳
// 维护声明: 仅维护通知发送；@Transactional(REQUIRES_NEW) 隔离失败；catch RuntimeException 兜底。

package com.xiyu.bid.project.service;

import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 项目转移通知服务。
 * <p>
 * 转移成功后给新负责人发站内通知。通知失败不影响调用方事务
 * （{@link Propagation#REQUIRES_NEW} 独立事务 + catch RuntimeException 兜底）。
 * </p>
 * <p>
 * 对齐 lessons-learned.md "通知派发独立事务" 约束，避免重蹈
 * TenderAssignmentNotifier 未隔离事务的隐患。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectTransferNotifier {

    private final NotificationApplicationService notificationAppService;

    /**
     * 通知新负责人被指派为项目负责人。
     *
     * @param projectId      项目 ID
     * @param projectName    项目名称
     * @param newOwnerId     新负责人用户 ID
     * @param newOwnerName   新负责人姓名
     * @param oldOwnerName   原负责人姓名
     * @param operatorId     操作人用户 ID
     * @param operatorName   操作人姓名
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyTransferred(Long projectId, String projectName, Long newOwnerId,
                                   String newOwnerName, String oldOwnerName,
                                   Long operatorId, String operatorName) {
        try {
            String title = "【项目转移】" + projectName;
            String body = "您好，" + newOwnerName + "：项目「" + projectName + "」已转移给您负责，原负责人："
                    + (oldOwnerName != null ? oldOwnerName : "无")
                    + "。操作人：" + operatorName + "。";
            CreateNotificationRequest request = new CreateNotificationRequest(
                    "APPROVAL", "PROJECT", projectId,
                    title, body, null, List.of(newOwnerId));
            notificationAppService.createNotification(request, operatorId);
        } catch (RuntimeException e) {
            log.warn("Failed to send project transfer notification for project {}: {}",
                    projectId, e.getMessage());
        }
    }
}
