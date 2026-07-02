package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.dto.ProjectDTO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证 ProjectListEnrichmentSupport.populateFromTender 对 customerType 的归一化：
 * 从 Tender 拷贝 customerType 时，必须经 InitiationFieldPolicy.normalizeCustomerType
 * 转换为后端枚举名，避免列表筛选时前端 value 与后端值不一致导致筛不出数据。
 */
class ProjectListEnrichmentSupportTest {

    @Test
    void populateFromTender_normalizesCentralSoeChineseTextToCanonical() {
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .customerType(null)
                .build();
        Tender tender = Tender.builder().id(1L).customerType("央企").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("CENTRAL_SOE", dto.getCustomerType());
    }

    @Test
    void populateFromTender_normalizesGovernmentLegacyFrontendValue() {
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .customerType(null)
                .build();
        // Tender 存的是前端旧 value GOVERNMENT_INSTITUTION（CRM 推送或历史数据）
        Tender tender = Tender.builder().id(1L).customerType("GOVERNMENT_INSTITUTION").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("GOVERNMENT", dto.getCustomerType());
    }

    @Test
    void populateFromTender_keepsUnknownValueWhenMappingMisses() {
        // 无法识别的值应保留原值，避免丢失数据
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .customerType(null)
                .build();
        Tender tender = Tender.builder().id(1L).customerType("外星人企业").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("外星人企业", dto.getCustomerType());
    }

    @Test
    void populateFromTender_doesNotOverwriteExistingCustomerType() {
        // DTO 已有 customerType 时不覆盖（populateFromTender 仅做 fallback）
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .customerType("PRIVATE")
                .build();
        Tender tender = Tender.builder().id(1L).customerType("央企").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("PRIVATE", dto.getCustomerType());
    }

    @Test
    void populateFromTender_tenderNotFound_leavesCustomerTypeNull() {
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(999L)
                .customerType(null)
                .build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of());

        assertNull(dto.getCustomerType());
    }

    @Test
    void populateFromTender_normalizesOtherEnum() {
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .customerType(null)
                .build();
        Tender tender = Tender.builder().id(1L).customerType("其他").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("OTHER", dto.getCustomerType());
    }
}
