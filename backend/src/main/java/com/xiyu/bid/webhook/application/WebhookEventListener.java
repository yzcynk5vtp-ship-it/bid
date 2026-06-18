package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoInnerDTO;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoSyncDTO;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTask;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskRepository;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookEventListener {
    private static final DateTimeFormatter STATUS_EDIT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WebhookDeliveryTaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    @Value("${webhook.crm.url:}")
    private String crmWebhookUrl;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTenderStatusChanged(TenderStatusChangedEvent event) {
        if (crmWebhookUrl == null || crmWebhookUrl.isBlank()) {
            log.debug("CRM webhook URL not configured, skipping enqueue for tender {}", event.tenderId());
            return;
        }
        Integer crmStatus = mapToCrmStatus(event.newStatus());
        if (crmStatus == null) {
            log.debug("Tender status {} not a CRM terminal state, skip webhook for tender {}",
                    event.newStatus(), event.tenderId());
            return;
        }
        taskRepository.save(WebhookDeliveryTask.builder()
                .tenderId(event.tenderId())
                .externalId(event.externalId())
                .targetUrl(crmWebhookUrl)
                .eventType("tender.status_changed")
                .businessKey(buildBusinessKey(event))
                .payload(buildPayload(event, crmStatus))
                .status(WebhookDeliveryTaskStatus.PENDING)
                .build());
    }

    private String buildBusinessKey(TenderStatusChangedEvent event) {
        return "%s:%s:%s".formatted(event.tenderId(), event.newStatus().name(), event.occurredAt());
    }

    /**
     * 映射标讯终态到 CRM bidInfoSync 的 status 数字。
     * <p>CRM 契约：1-弃标 2-中标 3-丢标 4-流标。
     * 中间态（待分配/跟踪/评估/投标）不回传，返回 null。
     */
    private Integer mapToCrmStatus(Tender.Status status) {
        return switch (status) {
            case ABANDONED -> 1;
            case WON -> 2;
            case LOST -> 3;
            default -> null;
        };
    }

    /**
     * 构造符合 CRM POST /customer-chance/bidInfoSync 契约的请求体。
     * <p>name/code 用 sourceId（externalId 冒号后部分，即 CRM 商机 id）填充。
     */
    private String buildPayload(TenderStatusChangedEvent event, Integer crmStatus) {
        try {
            String sourceId = extractSourceId(event.externalId());
            String statusEditTime = event.occurredAt().format(STATUS_EDIT_TIME_FORMAT);
            String operator = event.operatorName() != null ? event.operatorName() : "";
            BidInfoInnerDTO inner = new BidInfoInnerDTO(
                    sourceId,
                    sourceId,
                    crmStatus,
                    operator,
                    statusEditTime,
                    buildFeedback(event, operator));
            BidInfoSyncDTO dto = new BidInfoSyncDTO(List.of(inner));
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize bidInfoSync payload", ex);
        }
    }

    /**
     * CRM 要求 feedback 为 JSON 字符串，包含原因+友商+账期+备注+操作人+操作时间。
     * 事件未携带友商/账期/备注，置空字符串。
     */
    private String buildFeedback(TenderStatusChangedEvent event, String operator) {
        Map<String, Object> fb = new LinkedHashMap<>();
        fb.put("reason", event.abandonReason() != null ? event.abandonReason() : "");
        fb.put("vendor", "");
        fb.put("paymentTerm", "");
        fb.put("remark", "");
        fb.put("operator", operator);
        fb.put("operateTime", event.occurredAt().format(STATUS_EDIT_TIME_FORMAT));
        try {
            return objectMapper.writeValueAsString(fb);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize feedback", ex);
        }
    }

    private String extractSourceId(String externalId) {
        if (externalId == null || externalId.isBlank()) return "";
        int idx = externalId.indexOf(':');
        return idx >= 0 ? externalId.substring(idx + 1) : externalId;
    }
}
