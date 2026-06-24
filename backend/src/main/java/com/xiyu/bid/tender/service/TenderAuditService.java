package com.xiyu.bid.tender.service;

import com.xiyu.bid.audit.dto.AuditLogItemDTO;
import com.xiyu.bid.audit.service.AuditLogItemMapper;
import com.xiyu.bid.entity.AuditLog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.AuditLogRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 标讯操作审计服务。
 * 记录标讯中心的 9 类操作日志。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderAuditService {

    private static final String ENTITY_TYPE = "TENDER";

    private final AuditLogRepository auditLogRepository;
    private final AuditLogItemMapper itemMapper;
    private final UserRepository userRepository;

    @Transactional
    public void logCreate(Long tenderId, String username, String userId, String ipAddress) {
        AuditLog log = buildAuditLog("CREATE", "TENDER", String.valueOf(tenderId),
                "标讯已创建", null, null, username, userId, ipAddress);
        auditLogRepository.save(log);
    }

    @Transactional
    public void logEdit(Long tenderId, String field, String oldValue, String newValue,
                        String username, String userId, String ipAddress) {
        AuditLog log = buildAuditLog("UPDATE", "TENDER", String.valueOf(tenderId),
                "编辑字段: " + field, oldValue, newValue, username, userId, ipAddress);
        auditLogRepository.save(log);
    }

    @Transactional
    public void logAssign(Long tenderId, String oldManager, String newManager,
                          String username, String userId, String ipAddress) {
        AuditLog log = buildAuditLog("ASSIGN", "TENDER", String.valueOf(tenderId),
                "标讯分配", oldManager, newManager, username, userId, ipAddress);
        auditLogRepository.save(log);
    }

    @Transactional
    public void logReassign(Long tenderId, String oldManager, String newManager,
                            String username, String userId, String ipAddress) {
        AuditLog log = buildAuditLog("REASSIGN", "TENDER", String.valueOf(tenderId),
                "标讯转派", oldManager, newManager, username, userId, ipAddress);
        auditLogRepository.save(log);
    }

    @Transactional
    public void logEvaluationSubmit(Long tenderId, String username, String userId, String ipAddress) {
        AuditLog log = buildAuditLog("EVALUATION_SUBMIT", "TENDER", String.valueOf(tenderId),
                "评估已提交", null, null, username, userId, ipAddress);
        auditLogRepository.save(log);
    }

    @Transactional
    public void logParticipate(Long tenderId, String username, String userId, String ipAddress) {
        AuditLog log = buildAuditLog("PARTICIPATE", "TENDER", String.valueOf(tenderId),
                "立即投标", null, null, username, userId, ipAddress);
        auditLogRepository.save(log);
    }

    @Transactional
    public void logAbandon(Long tenderId, String reason, String username, String userId, String ipAddress) {
        AuditLog log = buildAuditLog("ABANDON", "TENDER", String.valueOf(tenderId),
                "放弃投标, 原因: " + reason, null, null, username, userId, ipAddress);
        auditLogRepository.save(log);
    }

    @Transactional
    public void logStatusChange(Long tenderId, String oldStatus, String newStatus,
                                String username, String userId, String ipAddress) {
        AuditLog log = buildAuditLog("STATUS_CHANGE", "TENDER", String.valueOf(tenderId),
                "状态变更", oldStatus, newStatus, username, userId, ipAddress);
        auditLogRepository.save(log);
    }

    @Transactional
    public void logDelete(Long tenderId, String username, String userId, String ipAddress) {
        AuditLog log = buildAuditLog("DELETE", "TENDER", String.valueOf(tenderId),
                "标讯已删除", null, null, username, userId, ipAddress);
        auditLogRepository.save(log);
    }

    @Transactional
    public void logTransfer(Long tenderId, String oldManager, String newManager,
                            String username, String userId, String ipAddress) {
        AuditLog log = buildAuditLog("TRANSFER", "TENDER", String.valueOf(tenderId),
                "标讯转派", oldManager, newManager, username, userId, ipAddress);
        auditLogRepository.save(log);
    }

    public List<AuditLogItemDTO> getAuditLogs(Long tenderId) {
        List<AuditLog> logs = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByTimestampDesc(ENTITY_TYPE, String.valueOf(tenderId));
        List<AuditLog> userLogs = logs.stream()
                .filter(log -> !isSystemTriggered(log))
                .toList();
        Map<String, User> userCache = resolveUsers(userLogs);
        return userLogs.stream()
                .map(log -> itemMapper.toItemDto(log, userCache.get(userKey(log))))
                .toList();
    }

    private boolean isSystemTriggered(AuditLog log) {
        if (log == null) return false;
        String userId = log.getUserId();
        if (userId != null) {
            String uid = userId.trim().toLowerCase();
            if (uid.equals("system") || uid.equals("scheduler") || uid.equals("auto")) {
                return true;
            }
        }
        String action = log.getAction();
        if (action != null && action.trim().toUpperCase().startsWith("AUTO_")) {
            return true;
        }
        return false;
    }

    private Map<String, User> resolveUsers(List<AuditLog> logs) {
        Map<String, User> users = new LinkedHashMap<>();
        List<Long> ids = logs.stream()
                .map(AuditLog::getUserId)
                .filter(Objects::nonNull)
                .filter(this::isNumeric)
                .map(Long::parseLong)
                .distinct()
                .toList();
        if (!ids.isEmpty()) {
            userRepository.findAllById(ids).forEach(user -> users.put(String.valueOf(user.getId()), user));
        }
        logs.stream()
                .filter(log -> !users.containsKey(userKey(log)))
                .map(AuditLog::getUsername)
                .filter(Objects::nonNull)
                .filter(username -> !username.isBlank())
                .distinct()
                .forEach(username -> userRepository.findByUsername(username)
                        .ifPresent(user -> users.put(username, user)));
        return users;
    }

    private String userKey(AuditLog log) {
        if (log.getUserId() != null && !log.getUserId().isBlank()) {
            return log.getUserId();
        }
        return log.getUsername();
    }

    private boolean isNumeric(String value) {
        return value != null && !value.isBlank() && value.chars().allMatch(Character::isDigit);
    }

    private AuditLog buildAuditLog(String action, String entityType, String entityId,
                                    String description, String oldValue, String newValue,
                                    String username, String userId, String ipAddress) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDescription(description);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setUsername(username);
        log.setUserId(userId);
        log.setIpAddress(ipAddress);
        log.setTimestamp(LocalDateTime.now());
        log.setSuccess(true);
        return log;
    }
}
