package com.xiyu.bid.tender.service;

import com.xiyu.bid.audit.dto.AuditLogItemDTO;
import com.xiyu.bid.audit.service.AuditLogItemMapper;
import com.xiyu.bid.entity.AuditLog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.AuditLogRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderAuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private UserRepository userRepository;

    private TenderAuditService tenderAuditService;

    @BeforeEach
    void setUp() {
        AuditLogItemMapper itemMapper = new AuditLogItemMapper();
        tenderAuditService = new TenderAuditService(auditLogRepository, itemMapper, userRepository);
    }

    @Test
    @DisplayName("getAuditLogs 返回 DTO，operator 为姓名（工号）格式")
    void getAuditLogs_ShouldReturnDtoWithOperatorNameAndWorkNo() {
        Long tenderId = 1L;
        AuditLog log = AuditLog.builder()
                .id(1L)
                .action("CREATE")
                .entityType("TENDER")
                .entityId(String.valueOf(tenderId))
                .description("标讯已创建")
                .userId("10")
                .username("06234")
                .timestamp(LocalDateTime.of(2026, 6, 24, 10, 0, 0))
                .success(true)
                .build();
        User user = User.builder()
                .id(10L)
                .username("06234")
                .fullName("郑蓉蓉")
                .departmentName("投标部")
                .build();

        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc("TENDER", String.valueOf(tenderId)))
                .thenReturn(List.of(log));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        List<AuditLogItemDTO> result = tenderAuditService.getAuditLogs(tenderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOperator()).isEqualTo("郑蓉蓉（06234）");
        assertThat(result.get(0).getTime()).isEqualTo("2026-06-24 10:00:00");
        assertThat(result.get(0).getDetail()).isEqualTo("标讯已创建");
        assertThat(result.get(0).getActionType()).isEqualTo("create");
    }

    @Test
    @DisplayName("getAuditLogs 过滤系统触发日志（userId 为 system）")
    void getAuditLogs_ShouldFilterSystemTriggeredLogs() {
        Long tenderId = 1L;
        AuditLog systemLog = AuditLog.builder()
                .id(1L).action("AUTO_SYNC").entityType("TENDER").entityId(String.valueOf(tenderId))
                .description("自动同步").userId("system").timestamp(LocalDateTime.now()).success(true).build();
        AuditLog userLog = AuditLog.builder()
                .id(2L).action("CREATE").entityType("TENDER").entityId(String.valueOf(tenderId))
                .description("标讯已创建").userId("10").username("06234").timestamp(LocalDateTime.now()).success(true).build();

        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc("TENDER", String.valueOf(tenderId)))
                .thenReturn(List.of(systemLog, userLog));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        List<AuditLogItemDTO> result = tenderAuditService.getAuditLogs(tenderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDetail()).isEqualTo("标讯已创建");
    }

    @Test
    @DisplayName("getAuditLogs 无日志时返回空列表")
    void getAuditLogs_ShouldReturnEmptyWhenNoLogs() {
        Long tenderId = 1L;
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc("TENDER", String.valueOf(tenderId)))
                .thenReturn(List.of());

        List<AuditLogItemDTO> result = tenderAuditService.getAuditLogs(tenderId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAuditLogs 用户不存在时 operator 显示 username")
    void getAuditLogs_ShouldFallbackToUsernameWhenUserNotFound() {
        Long tenderId = 1L;
        AuditLog log = AuditLog.builder()
                .id(1L).action("UPDATE").entityType("TENDER").entityId(String.valueOf(tenderId))
                .description("编辑标讯").userId("999").username("olduser").timestamp(LocalDateTime.now()).success(true).build();

        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc("TENDER", String.valueOf(tenderId)))
                .thenReturn(List.of(log));
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(userRepository.findByUsername("olduser")).thenReturn(Optional.empty());

        List<AuditLogItemDTO> result = tenderAuditService.getAuditLogs(tenderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOperator()).isEqualTo("olduser");
    }
}
