package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证 ProjectMapper.toDTO 对 customerType 的归一化：
 * 列表展示的 customerType 必须统一为后端 CustomerType 枚举名，
 * 避免历史中文/前端旧 value 数据导致筛选筛不出。
 */
class ProjectMapperTest {

    @Test
    void toDTO_normalizesChineseCustomerTypeToCanonical() {
        Project p = Project.builder().id(1L).customerType("央企").build();

        assertEquals("CENTRAL_SOE", ProjectMapper.toDTO(p).getCustomerType());
    }

    @Test
    void toDTO_normalizesLegacyFrontendValueToCanonical() {
        // 历史数据可能存的是前端旧 value GOVERNMENT_INSTITUTION
        Project p = Project.builder().id(1L).customerType("GOVERNMENT_INSTITUTION").build();

        assertEquals("GOVERNMENT", ProjectMapper.toDTO(p).getCustomerType());
    }

    @Test
    void toDTO_keepsCanonicalValueAsIs() {
        Project p = Project.builder().id(1L).customerType("CENTRAL_SOE").build();

        assertEquals("CENTRAL_SOE", ProjectMapper.toDTO(p).getCustomerType());
    }

    @Test
    void toDTO_keepsUnknownValueWhenMappingMisses() {
        // 无法识别的值保留原值，避免丢失数据
        Project p = Project.builder().id(1L).customerType("外星人企业").build();

        assertEquals("外星人企业", ProjectMapper.toDTO(p).getCustomerType());
    }

    @Test
    void toDTO_nullCustomerType_staysNull() {
        Project p = Project.builder().id(1L).customerType(null).build();

        assertNull(ProjectMapper.toDTO(p).getCustomerType());
    }

    @Test
    void toDTO_normalizesOtherEnum() {
        Project p = Project.builder().id(1L).customerType("其他").build();

        assertEquals("OTHER", ProjectMapper.toDTO(p).getCustomerType());
    }
}
