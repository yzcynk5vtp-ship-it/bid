package com.xiyu.bid.webhook.domain;

import com.xiyu.bid.entity.Tender;

import java.time.LocalDateTime;

/**
 * 标讯状态变更领域事件。触发 outbound webhook 推送。
 */
public record TenderStatusChangedEvent(
    Long tenderId,
    String externalId,
    Tender.Status oldStatus,
    Tender.Status newStatus,
    String title,
    LocalDateTime occurredAt,
    String abandonReason,
    Long operatorId,
    String operatorName,
    Boolean recommendationShouldBid,
    String recommendationReason
) {
    public static TenderStatusChangedEvent of(Long tenderId, String externalId,
                                               Tender.Status oldStatus, Tender.Status newStatus,
                                               String title) {
        return new TenderStatusChangedEvent(tenderId, externalId, oldStatus, newStatus, title, LocalDateTime.now(), null, null, null, null, null);
    }

    public static TenderStatusChangedEvent of(Long tenderId, String externalId,
                                               Tender.Status oldStatus, Tender.Status newStatus,
                                               String title, String abandonReason) {
        return new TenderStatusChangedEvent(tenderId, externalId, oldStatus, newStatus, title, LocalDateTime.now(), abandonReason, null, null, null, null);
    }

    public static TenderStatusChangedEvent of(Long tenderId, String externalId,
                                               Tender.Status oldStatus, Tender.Status newStatus,
                                               String title, String abandonReason,
                                               Long operatorId, String operatorName,
                                               Boolean recommendationShouldBid, String recommendationReason) {
        return new TenderStatusChangedEvent(tenderId, externalId, oldStatus, newStatus, title, LocalDateTime.now(),
                abandonReason, operatorId, operatorName, recommendationShouldBid, recommendationReason);
    }
}
