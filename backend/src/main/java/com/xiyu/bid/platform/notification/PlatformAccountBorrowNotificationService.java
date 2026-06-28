// Input: PlatformAccountBorrowService state changes
// Output: Notifications to custodian / applicant
// Pos: Notification/编排层 — 账号借用申请状态变更通知

package com.xiyu.bid.platform.notification;

import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.platform.dto.BorrowApplicationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Sends notifications for platform account borrow application lifecycle events. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountBorrowNotificationService {

    private final NotificationApplicationService notificationService;

    public void notifySubmitted(BorrowApplicationDTO app) {
        if (app.getCustodianId() == null) return;
        send(NotificationType.INFO, app.getCustodianId(), "账号借用申请待审批",
                format("申请人提交了账号借用申请，请尽快审批。使用目的：%s", app.getPurpose()),
                app.getId());
    }

    public void notifyApproved(BorrowApplicationDTO app) {
        if (app.getApplicantId() == null) return;
        send(NotificationType.INFO, app.getApplicantId(), "账号借用申请已通过",
                "您的账号借用申请已审批通过，请联系绑定联系人获取账号密码。",
                app.getId());
    }

    public void notifyRejected(BorrowApplicationDTO app, String reason) {
        if (app.getApplicantId() == null) return;
        send(NotificationType.INFO, app.getApplicantId(), "账号借用申请已拒绝",
                format("您的账号借用申请已被拒绝。原因：%s", reason),
                app.getId());
    }

    public void notifyCancelled(BorrowApplicationDTO app) {
        if (app.getCustodianId() == null) return;
        send(NotificationType.INFO, app.getCustodianId(), "账号借用申请已撤销",
                "申请人撤销了账号借用申请。",
                app.getId());
    }

    public void notifyReturned(BorrowApplicationDTO app) {
        if (app.getApplicantId() == null) return;
        send(NotificationType.INFO, app.getApplicantId(), "账号已归还",
                "您借用的账号已完成归还登记，密码已重置。",
                app.getId());
    }

    private void send(NotificationType type, Long recipientId, String title, String body, Long applicationId) {
        try {
            notificationService.createNotification(new CreateNotificationRequest(
                    type.name(),
                    "AccountBorrowApplication",
                    applicationId,
                    title,
                    body,
                    Map.of(),
                    List.of(recipientId)
            ), 0L);
        } catch (RuntimeException e) {
            log.warn("Failed to send borrow notification to user {}: {}", recipientId, e.getMessage());
        }
    }

    private String format(String template, Object... args) {
        return String.format(template, args);
    }
}
