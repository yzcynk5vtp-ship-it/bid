package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.dto.ContactDTO;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationDTO;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 外部标讯同步核心服务（接口规范 v2.0）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderIntegrationService {

    private final TenderRepository tenderRepository;
    private final TenderMapper tenderMapper;
    private final TenderEvaluationRepository tenderEvaluationRepository;
    private final TenderEvaluationSubmissionMapper submissionMapper;

    /**
     * 幂等推送标讯。
     * 按 (sourceSystem, sourceId) 组合 externalId 进行幂等判断：
     * - 已存在：返回 DUPLICATE
     * - 不存在：创建并返回 CREATED
     */
    @Transactional
    public TenderPushResponse pushTender(TenderPushRequest request, Long userId) {
        String externalId = buildExternalId(request.getSourceSystem(), request.getSourceId());
        return tenderRepository.findByExternalId(externalId)
                .map(existing -> {
                    if (Boolean.TRUE.equals(request.getForceUpdate())) {
                        applyUpdate(existing, request);
                        if (userId != null) {
                            existing.setCreatorId(userId);
                        }
                        Tender saved = tenderRepository.save(existing);
                        log.info("Force-updated tender id={} externalId={}", saved.getId(), externalId);
                        return TenderPushResponse.builder()
                                .tenderId(saved.getId())
                                .status("UPDATED")
                                .message("标讯已覆盖更新")
                                .build();
                    }
                    return TenderPushResponse.builder()
                            .tenderId(existing.getId())
                            .status("DUPLICATE")
                            .message("标讯已存在")
                            .build();
                })
                .orElseGet(() -> {
                    Tender tender = mapToEntity(request);
                    tender.setExternalId(externalId);
                    if (userId != null) {
                        tender.setCreatorId(userId);
                    }
                    Tender saved = tenderRepository.save(tender);
                    log.info("Created tender id={} externalId={}", saved.getId(), externalId);
                    return TenderPushResponse.builder()
                            .tenderId(saved.getId())
                            .status("CREATED")
                            .message("标讯创建成功")
                            .build();
                });
    }

    private void applyUpdate(Tender tender, TenderPushRequest r) {
        if (r.getTitle() != null) tender.setTitle(InputSanitizer.sanitizeString(r.getTitle(), 500));
        if (r.getCustomerName() != null) tender.setPurchaserName(InputSanitizer.sanitizeString(r.getCustomerName(), 500));
        if (r.getPublishDate() != null) tender.setPublishDate(r.getPublishDate());
        if (r.getDueDate() != null) tender.setDeadline(parseDateTime(r.getDueDate()));
        if (r.getBudgetAmount() != null) tender.setBudget(r.getBudgetAmount());
        applyBasicInfo(tender, r.getRegion(), r.getIndustry(), r.getTenderAgency(),
                r.getBidOpeningTime(), r.getRegistrationDeadline(), r.getCustomerType(),
                r.getPriority(), r.getProjectType(), r.getSourcePlatform(), r.getSource(), r.getTags());
        applyContactInfo(tender, r.getContactInfo());
        if (r.getContentDesc() != null) tender.setDescription(InputSanitizer.sanitizeString(r.getContentDesc(), 5000));
    }

    /**
     * 按 externalId 查询标讯详情。
     */
    @Transactional(readOnly = true)
    public TenderDTO getByExternalId(String sourceSystem, String sourceId) {
        String externalId = buildExternalId(sourceSystem, sourceId);
        Tender tender = tenderRepository.findByExternalId(externalId)
                .orElseThrow(() -> new com.xiyu.bid.exception.ResourceNotFoundException(
                        "标讯不存在: " + externalId));
        TenderDTO dto = tenderMapper.toDTO(tender);
        dto.setContactInfo(tenderMapper.buildContacts(tender));
        dto.setEvaluation(buildEvaluationDTO(tender.getId(), tender));
        return dto;
    }

    /**
     * 按 externalId 或 tenderId 更新标讯字段（二选一必传）。
     * tenderId 优先级高于 sourceSystem/sourceId；两者都传时会交叉校验。
     */
    @Transactional
    public TenderDTO updateByExternalId(String sourceSystem, String sourceId, TenderUpdateRequest request) {
        Tender tender;
        if (request.getTenderId() != null) {
            tender = tenderRepository.findById(request.getTenderId())
                    .orElseThrow(() -> new com.xiyu.bid.exception.ResourceNotFoundException(
                            "标讯不存在: id=" + request.getTenderId()));
            // 若同时传了 sourceSystem/sourceId，做交叉校验
            if (sourceSystem != null && !sourceSystem.isBlank()
                    && sourceId != null && !sourceId.isBlank()) {
                String expectedExternalId = buildExternalId(sourceSystem, sourceId);
                if (tender.getExternalId() != null && !tender.getExternalId().equals(expectedExternalId)) {
                    throw new IllegalArgumentException(
                            "tenderId=" + request.getTenderId() + " 的 externalId="
                            + tender.getExternalId() + " 与路径 sourceSystem=" + sourceSystem
                            + " sourceId=" + sourceId + " 不匹配");
                }
            }
        } else if (sourceSystem != null && !sourceSystem.isBlank()
                && sourceId != null && !sourceId.isBlank()) {
            String externalId = buildExternalId(sourceSystem, sourceId);
            tender = tenderRepository.findByExternalId(externalId)
                    .orElseThrow(() -> new com.xiyu.bid.exception.ResourceNotFoundException(
                            "标讯不存在: " + externalId));
        } else {
            throw new IllegalArgumentException("tenderId 与 (sourceSystem, sourceId) 至少需要传一组");
        }

        String externalId = tender.getExternalId();

        if (request.getTitle() != null) {
            tender.setTitle(InputSanitizer.sanitizeString(request.getTitle(), 500));
        }
        if (request.getCustomerName() != null) {
            tender.setPurchaserName(InputSanitizer.sanitizeString(request.getCustomerName(), 500));
        }
        if (request.getPublishDate() != null) {
            tender.setPublishDate(request.getPublishDate());
        }
        if (request.getDueDate() != null) tender.setDeadline(parseDateTime(request.getDueDate()));
        if (request.getBudgetAmount() != null) {
            tender.setBudget(request.getBudgetAmount());
        }
        applyBasicInfo(tender, request.getRegion(), request.getIndustry(), request.getTenderAgency(),
                request.getBidOpeningTime(), request.getRegistrationDeadline(), request.getCustomerType(),
                request.getPriority(), request.getProjectType(), request.getSourcePlatform(), request.getSource(), request.getTags());
        applyContactInfo(tender, request.getContactInfo());
        if (request.getContentDesc() != null) {
            tender.setDescription(InputSanitizer.sanitizeString(request.getContentDesc(), 5000));
        }

        Tender saved = tenderRepository.save(tender);
        log.info("Updated tender id={} externalId={}", saved.getId(), externalId);
        TenderDTO dto = tenderMapper.toDTO(saved);
        dto.setContactInfo(tenderMapper.buildContacts(saved));
        dto.setEvaluation(buildEvaluationDTO(saved.getId(), saved));
        return dto;
    }

    private String buildExternalId(String sourceSystem, String sourceId) {
        return sourceSystem + ":" + sourceId;
    }

    /**
     * 将请求中的联系人数组映射到实体扁平字段（最多取前 2 个）。
     */
    private void applyContactInfo(Tender tender, List<ContactDTO> contactInfo) {
        if (contactInfo == null || contactInfo.isEmpty()) {
            return;
        }
        // 清空旧值
        tender.setContactName(null);
        tender.setContactPhone(null);
        tender.setContactTel(null);
        tender.setContactMail(null);
        tender.setContactName2(null);
        tender.setContactPhone2(null);
        tender.setContactTel2(null);
        tender.setContactMail2(null);

        ContactDTO c1 = contactInfo.get(0);
        if (c1.getName() != null) tender.setContactName(InputSanitizer.sanitizeString(c1.getName(), 100));
        if (c1.getPhone() != null) tender.setContactPhone(InputSanitizer.sanitizeString(c1.getPhone(), 50));
        if (c1.getTel() != null) tender.setContactTel(InputSanitizer.sanitizeString(c1.getTel(), 50));
        if (c1.getMail() != null) tender.setContactMail(InputSanitizer.sanitizeString(c1.getMail(), 100));

        if (contactInfo.size() > 1) {
            ContactDTO c2 = contactInfo.get(1);
            if (c2.getName() != null) tender.setContactName2(InputSanitizer.sanitizeString(c2.getName(), 100));
            if (c2.getPhone() != null) tender.setContactPhone2(InputSanitizer.sanitizeString(c2.getPhone(), 50));
            if (c2.getTel() != null) tender.setContactTel2(InputSanitizer.sanitizeString(c2.getTel(), 50));
            if (c2.getMail() != null) tender.setContactMail2(InputSanitizer.sanitizeString(c2.getMail(), 100));
        }
    }

    /** 将请求中的基本信息字段映射到实体（非空才覆盖）。 */
    private void applyBasicInfo(Tender t, String region, String industry, String tenderAgency,
                                String bidOpeningTime, String registrationDeadline, String customerType,
                                String priority, String projectType, String sourcePlatform,
                                String source, List<String> tags) {
        if (region != null) t.setRegion(InputSanitizer.sanitizeString(region, 100));
        if (industry != null) t.setIndustry(InputSanitizer.sanitizeString(industry, 100));
        if (tenderAgency != null) t.setTenderAgency(InputSanitizer.sanitizeString(tenderAgency, 255));
        if (bidOpeningTime != null) t.setBidOpeningTime(parseDateTime(bidOpeningTime));
        if (registrationDeadline != null) t.setRegistrationDeadline(parseDateTime(registrationDeadline));
        if (customerType != null) t.setCustomerType(InputSanitizer.sanitizeString(customerType, 100));
        if (priority != null) t.setPriority(InputSanitizer.sanitizeString(priority, 10));
        if (projectType != null) t.setProjectType(InputSanitizer.sanitizeString(projectType, 20));
        if (sourcePlatform != null) t.setSourcePlatform(InputSanitizer.sanitizeString(sourcePlatform, 100));
        if (source != null) t.setSource(InputSanitizer.sanitizeString(source, 200));
        if (tags != null) t.setTags(tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse(""));
    }

    /** 查询评估表并构建 DTO。 */
    private TenderEvaluationDTO buildEvaluationDTO(Long tenderId, Tender tender) {
        return tenderEvaluationRepository.findByTenderId(tenderId)
                .map(e -> submissionMapper.toDTO(e, tender, false, false))
                .orElse(null);
    }

    private Tender mapToEntity(TenderPushRequest r) {
        Tender t = new Tender();
        t.setTitle(InputSanitizer.sanitizeString(r.getTitle(), 500));
        if (r.getCustomerName() != null) {
            t.setPurchaserName(InputSanitizer.sanitizeString(r.getCustomerName(), 500));
        }
        if (r.getPublishDate() != null) {
            t.setPublishDate(r.getPublishDate());
        }
        if (r.getDueDate() != null) t.setDeadline(parseDateTime(r.getDueDate()));
        if (r.getBudgetAmount() != null) {
            t.setBudget(r.getBudgetAmount());
        }
        applyBasicInfo(t, r.getRegion(), r.getIndustry(), r.getTenderAgency(),
                r.getBidOpeningTime(), r.getRegistrationDeadline(), r.getCustomerType(),
                r.getPriority(), r.getProjectType(), r.getSourcePlatform(), r.getSource(), r.getTags());
        applyContactInfo(t, r.getContactInfo());
        if (r.getContentDesc() != null) {
            t.setDescription(InputSanitizer.sanitizeString(r.getContentDesc(), 5000));
        }
        t.setSourceType(com.xiyu.bid.entity.Tender.SourceType.EXTERNAL_PLATFORM);
        t.setSource(com.xiyu.bid.entity.Tender.SourceType.EXTERNAL_PLATFORM.getLabel());
        t.setStatus(com.xiyu.bid.entity.Tender.Status.PENDING_ASSIGNMENT);
        return t;
    }

    /** 解析 datetime 字符串，兼容 yyyy-MM-ddTHH:mm 和 yyyy-MM-ddTHH:mm:ss */
    private static java.time.LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.length() == 16 ? value + ":00" : value;
        return java.time.LocalDateTime.parse(normalized);
    }
}
