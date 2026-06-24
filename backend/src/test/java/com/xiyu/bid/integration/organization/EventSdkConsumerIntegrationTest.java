package com.xiyu.bid.integration.organization;

import com.ehsy.eventlibrary.clientsdk.systeminteraction.result.EventResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.organization.application.OrganizationDepartmentSyncWriter;
import com.xiyu.bid.integration.organization.application.OrganizationDirectoryGateway;
import com.xiyu.bid.integration.organization.application.OrganizationDirectorySyncAppService;
import com.xiyu.bid.integration.organization.application.OrganizationEventAppService;
import com.xiyu.bid.integration.organization.application.OrganizationEventInboxService;
import com.xiyu.bid.integration.organization.application.OrganizationEventNoticeJsonReader;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationSettingsResolver;
import com.xiyu.bid.integration.organization.application.OrganizationUserSyncWriter;
import com.xiyu.bid.integration.organization.domain.OrganizationDepartmentSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.infrastructure.sdk.OrganizationEventSdkConsumerAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 3 User Story 2 - 事件库 SDK 消费链路集成测试。
 *
 * <p>验证 {@link OrganizationEventSdkConsumerAdapter} → {@link OrganizationEventAppService}
 * → {@link OrganizationDirectorySyncAppService} 完整调用链。Mock 外部依赖
 * （OSS HTTP 网关 + 写入器 + 收件箱），验证内部编排逻辑。
 */
@DisplayName("EventSdkConsumerIntegrationTest - SDK 消费链路集成测试")
@ExtendWith(MockitoExtension.class)
class EventSdkConsumerIntegrationTest {

    @Mock
    private OrganizationDirectoryGateway gateway;
    @Mock
    private OrganizationUserSyncWriter userWriter;
    @Mock
    private OrganizationDepartmentSyncWriter departmentWriter;
    @Mock
    private OrganizationEventInboxService inboxService;
    @Mock
    private ObjectProvider<OrganizationDirectoryGateway> gatewayProvider;

    private OrganizationEventSdkConsumerAdapter adapter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setEnabled(true);
        properties.setAllowedSourceApps(List.of("oss"));
        OrganizationIntegrationSettingsResolver settingsResolver =
                new OrganizationIntegrationSettingsResolver(null, properties);

        OrganizationEventNoticeJsonReader noticeReader = new OrganizationEventNoticeJsonReader(objectMapper);
        OrganizationDirectorySyncAppService syncAppService = new OrganizationDirectorySyncAppService(
                inboxService, noticeReader, gatewayProvider, departmentWriter, userWriter, settingsResolver, objectMapper
        );
        OrganizationEventAppService eventAppService = new OrganizationEventAppService(syncAppService);
        adapter = new OrganizationEventSdkConsumerAdapter(eventAppService, objectMapper);
    }

    @Test
    @DisplayName("BaseOssUser 事件触发 userWriter.upsert")
    void onUserChanged_triggersUserUpsert() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(inboxService.eventKey(any())).thenReturn("evt-user-1");
        when(inboxService.reserve(any(), any())).thenReturn(true);
        doCallRealMethod().when(gateway).fetchUserByUserId(any(), any());
        OrganizationUserSnapshot snapshot = new OrganizationUserSnapshot(
                "10001", "u10001", "张三", "u10001@example.com", "13800000000",
                "sales", "销售部", "", "", true
        );
        when(gateway.fetchUserByUserId("10001")).thenReturn(Optional.of(snapshot));

        EventResult result = adapter.onUserChanged(sdkUserMessage("575338", "10001"));

        assertThat(result.getCode()).isEqualTo("200");
        verify(userWriter).upsert(eq("oss"), eq("evt-user-1"), eq(snapshot));
        verify(inboxService).markProcessed("evt-user-1");
    }

    @Test
    @DisplayName("BaseOssDept 事件触发 departmentWriter.upsert")
    void onDeptChanged_triggersDeptUpsert() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(inboxService.eventKey(any())).thenReturn("evt-dept-1");
        when(inboxService.reserve(any(), any())).thenReturn(true);
        doCallRealMethod().when(gateway).fetchDepartmentByDeptId(any(), any());
        OrganizationDepartmentSnapshot snapshot = new OrganizationDepartmentSnapshot(
                "D001", "sales", "销售部", "", "", true
        );
        when(gateway.fetchDepartmentByDeptId("D001")).thenReturn(Optional.of(snapshot));

        EventResult result = adapter.onDeptChanged(sdkDeptMessage("575339", "D001"));

        assertThat(result.getCode()).isEqualTo("200");
        verify(departmentWriter).upsert(eq("oss"), eq("evt-dept-1"), eq(snapshot));
        verify(inboxService).markProcessed("evt-dept-1");
    }

    @Test
    @DisplayName("重复 eventKey 幂等不重复写入（inboxService 去重）")
    void duplicateEventKey_idempotent() {
        when(inboxService.eventKey(any())).thenReturn("evt-dup");
        when(inboxService.reserve(any(), any())).thenReturn(false);

        EventResult result = adapter.onUserChanged(sdkUserMessage("575338", "10001"));

        assertThat(result.getCode()).isEqualTo("200");
        verify(gateway, never()).fetchUserByUserId(any(), any());
        verify(userWriter, never()).upsert(any(), any(), any());
        verify(inboxService, never()).markProcessed(any());
    }

    @Test
    @DisplayName("离职用户（OSS 远程查无）触发 disableByExternalId")
    void missingUser_triggersDisable() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(inboxService.eventKey(any())).thenReturn("evt-missing");
        when(inboxService.reserve(any(), any())).thenReturn(true);
        doCallRealMethod().when(gateway).fetchUserByUserId(any(), any());
        when(gateway.fetchUserByUserId("99999")).thenReturn(Optional.empty());

        EventResult result = adapter.onUserChanged(sdkUserMessage("575340", "99999"));

        assertThat(result.getCode()).isEqualTo("200");
        verify(userWriter).disableByExternalId(eq("oss"), eq("evt-missing"), eq("99999"));
        verify(inboxService).markProcessed("evt-missing");
    }

    /** SDK 原始消息格式：eventTrackInfo 包裹追踪字段，数据字段在根层级。 */
    private String sdkUserMessage(String eventId, String userId) {
        return """
                {"eventTrackInfo":{"traceId":"trace-1","spanId":"span-1","parentId":"0"},
                 "id":"%s","userId":"%s"}
                """.formatted(eventId, userId);
    }

    private String sdkDeptMessage(String eventId, String deptId) {
        return """
                {"eventTrackInfo":{"traceId":"trace-2","spanId":"span-2","parentId":"0"},
                 "id":"%s","deptId":"%s"}
                """.formatted(eventId, deptId);
    }
}
