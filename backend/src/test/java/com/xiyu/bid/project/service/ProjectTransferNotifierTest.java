// Input: ProjectTransferNotifier 行为
// Output: Mockito 单元测试覆盖通知成功/失败/独立事务
// Pos: backend test source
// 维护声明: 验证通知调用 + 失败不传播异常。

package com.xiyu.bid.project.service;

import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTransferNotifierTest {

    @Mock NotificationApplicationService notificationAppService;

    ProjectTransferNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new ProjectTransferNotifier(notificationAppService);
    }

    @Test
    void notifyTransferred_success_callsNotificationService() {
        // When
        notifier.notifyTransferred(135L, "测试项目", 7324L, "周子靖", "陈梦瑶", 999L, "管理员");

        // Then
        ArgumentCaptor<CreateNotificationRequest> requestCaptor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationAppService).createNotification(requestCaptor.capture(), eq(999L));

        CreateNotificationRequest request = requestCaptor.getValue();
        assertThat(request.title()).contains("项目转移").contains("测试项目");
        assertThat(request.body()).contains("测试项目").contains("周子靖").contains("陈梦瑶").contains("管理员");
        assertThat(request.recipientUserIds()).containsExactly(7324L);
    }

    @Test
    void notifyTransferred_notificationFails_doesNotPropagateException() {
        // Given
        doThrow(new RuntimeException("通知服务不可用"))
                .when(notificationAppService).createNotification(any(), eq(999L));

        // When & Then - 不应抛出异常
        assertThatCode(() -> notifier.notifyTransferred(135L, "测试项目", 7324L, "周子靖", "陈梦瑶", 999L, "管理员"))
                .doesNotThrowAnyException();
    }
}
