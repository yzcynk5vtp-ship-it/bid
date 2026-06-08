package com.xiyu.bid.businessqualification.domain.service;

import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.model.QualificationAttachment;
import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualificationCreationPolicyTest {

    private final QualificationCreationPolicy policy = new QualificationCreationPolicy();

    @Test
    @DisplayName("全部字段有效时应返回成功")
    void should_return_success_when_all_fields_valid() {
        BusinessQualification q = sampleQualification("AAA", "代理机构", "13800138000", "范围", "CERT-001");

        QualificationValidationResult result = policy.validateForCreate(q);

        assertThat(result.valid()).isTrue();
        assertThat(result.message()).isNull();
    }

    @Test
    @DisplayName("等级为空时应返回错误")
    void should_return_error_when_level_blank() {
        BusinessQualification q = sampleQualification("", "代理机构", "13800138000", "范围", "CERT-001");

        QualificationValidationResult result = policy.validateForCreate(q);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("等级不能为空");
    }

    @Test
    @DisplayName("代理机构为空时应返回错误")
    void should_return_error_when_agency_blank() {
        BusinessQualification q = sampleQualification("AAA", "", "13800138000", "范围", "CERT-001");

        QualificationValidationResult result = policy.validateForCreate(q);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("代理机构不能为空");
    }

    @Test
    @DisplayName("代理联系方式为空时应返回错误")
    void should_return_error_when_agencyContact_blank() {
        BusinessQualification q = sampleQualification("AAA", "代理机构", "", "范围", "CERT-001");

        QualificationValidationResult result = policy.validateForCreate(q);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("代理联系方式不能为空");
    }

    @Test
    @DisplayName("认证范围为空时应返回错误")
    void should_return_error_when_certScope_blank() {
        BusinessQualification q = sampleQualification("AAA", "代理机构", "13800138000", "", "CERT-001");

        QualificationValidationResult result = policy.validateForCreate(q);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("认证范围不能为空");
    }

    @Test
    @DisplayName("证书编号为空时应返回错误")
    void should_return_error_when_certificateNo_blank() {
        BusinessQualification q = sampleQualification("AAA", "代理机构", "13800138000", "范围", "");

        QualificationValidationResult result = policy.validateForCreate(q);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("证书编号不能为空");
    }

    @Test
    @DisplayName("证书编号为null时应返回错误")
    void should_return_error_when_certificateNo_null() {
        BusinessQualification q = sampleQualification("AAA", "代理机构", "13800138000", "范围", null);

        QualificationValidationResult result = policy.validateForCreate(q);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("证书编号不能为空");
    }

    private BusinessQualification sampleQualification(String level, String agency,
                                                       String agencyContact, String certScope,
                                                       String certificateNo) {
        return BusinessQualification.create(
                null,
                "ISO 9001",
                level,
                QualificationSubject.of(QualificationSubjectType.COMPANY, "西域"),
                QualificationCategory.LICENSE,
                certificateNo,
                "认证机构",
                agency,
                agencyContact,
                certScope,
                "审核提醒",
                "持证人",
                new ValidityPeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)),
                new ReminderPolicy(true, 30, null),
                LoanStatus.AVAILABLE,
                null, null, null, null, null,
                "",
                "",
                List.of()
        );
    }
}
