package com.xiyu.bid.tender.service;

import com.xiyu.bid.ai.service.AiService;
import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TenderAiAnalysisService {

    private final TenderRepository tenderRepository;
    private final TenderMapper tenderMapper;
    private final TenderProjectAccessGuard accessGuard;
    private final AiService aiService;

    @Auditable(action = "AI_ANALYZE", entityType = "TENDER", description = "AI分析标讯")
    public TenderDTO analyzeTender(Long id) {
        log.debug("Analyzing tender with id: {}", id);
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));
        accessGuard.assertCanAccessTender(tender);

        CompletableFuture<Void> analysisFuture = aiService.analyzeTender(id, buildAiContext(tender));
        try {
            analysisFuture.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("AI analysis wait interrupted for tender id: {}", id, e);
            throw new RuntimeException("AI analysis wait interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            log.error("Error waiting for AI analysis completion for tender id: {}", id, e);
            throw new RuntimeException("Failed to complete AI analysis", e);
        }

        Tender analyzedTender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));
        log.info("Analyzed tender with id: {}, AI Score: {}", id, analyzedTender.getAiScore());
        return tenderMapper.toDTO(analyzedTender);
    }

    private Map<String, Object> buildAiContext(Tender tender) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (tender.getBudget() != null) context.put("budget", tender.getBudget());
        if (tender.getDeadline() != null) context.put("deadline", tender.getDeadline());
        if (tender.getBidOpeningTime() != null) context.put("bidOpeningTime", tender.getBidOpeningTime());
        if (tender.getSource() != null) context.put("source", tender.getSource());
        if (tender.getRegion() != null) context.put("region", tender.getRegion());
        if (tender.getIndustry() != null) context.put("industry", tender.getIndustry());
        if (tender.getTenderAgency() != null) context.put("tenderAgency", tender.getTenderAgency());
        if (tender.getPurchaserName() != null) context.put("purchaserName", tender.getPurchaserName());
        if (tender.getCustomerType() != null) context.put("customerType", tender.getCustomerType());
        if (tender.getPriority() != null) context.put("priority", tender.getPriority());
        if (tender.getPublishDate() != null) context.put("publishDate", tender.getPublishDate());
        if (tender.getDescription() != null) context.put("description", tender.getDescription());
        if (tender.getTags() != null) context.put("tags", tender.getTags());
        return context;
    }
}
