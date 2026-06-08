package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.businessqualification.application.command.QualificationUpsertCommand;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.businessqualification.domain.service.QualificationCreationPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.exception.InvalidArgumentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateQualificationAppServiceTest {

    @Mock
    private BusinessQualificationRepository repository;

    @Spy
    private QualificationCreationPolicy creationPolicy = new QualificationCreationPolicy();

    @InjectMocks
    private CreateQualificationAppService appService;

    @Test
    @DisplayName("创建资质 - 证书编号重复时应抛异常")
    void create_WhenCertificateNoAlreadyExists_ShouldThrowIllegalArgumentException() {
        when(repository.existsByCertificateNo("DUP-001")).thenReturn(true);

        QualificationUpsertCommand command = sampleCommand("DUP-001");

        assertThatThrownBy(() -> appService.create(command))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("证书编号已存在");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("创建资质 - 证书编号唯一时应保存成功")
    void create_WhenCertificateNoIsUnique_ShouldDelegateToRepositorySave() {
        when(repository.existsByCertificateNo("UNIQUE-001")).thenReturn(false);
        when(repository.save(any(BusinessQualification.class))).thenAnswer(inv -> inv.getArgument(0));

        QualificationUpsertCommand command = sampleCommand("UNIQUE-001");

        BusinessQualification result = appService.create(command);

        assertThat(result).isNotNull();
        assertThat(result.certificateNo()).isEqualTo("UNIQUE-001");
        verify(repository).save(any(BusinessQualification.class));
    }

    @Test
    @DisplayName("创建资质 - 必填字段缺失时应抛异常")
    void create_WhenRequiredFieldMissing_ShouldThrowIllegalArgumentException() {
        QualificationUpsertCommand command = QualificationUpsertCommand.builder()
                .name("ISO 9001")
                .level("")
                .subjectType(QualificationSubjectType.COMPANY)
                .subjectName("西域")
                .category(QualificationCategory.LICENSE)
                .certificateNo("CERT-001")
                .issuer("认证机构")
                .agency("代理机构")
                .agencyContact("13800138000")
                .certScope("范围")
                .issueDate(LocalDate.of(2024, 1, 1))
                .expiryDate(LocalDate.of(2025, 1, 1))
                .build();

        assertThatThrownBy(() -> appService.create(command))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("等级不能为空");
        verify(repository, never()).save(any());
    }

    private QualificationUpsertCommand sampleCommand(String certificateNo) {
        return QualificationUpsertCommand.builder()
                .name("ISO 9001")
                .level("AAA")
                .subjectType(QualificationSubjectType.COMPANY)
                .subjectName("西域")
                .category(QualificationCategory.LICENSE)
                .certificateNo(certificateNo)
                .issuer("认证机构")
                .agency("代理机构")
                .agencyContact("13800138000")
                .certScope("范围")
                .issueDate(LocalDate.of(2024, 1, 1))
                .expiryDate(LocalDate.of(2025, 1, 1))
                .build();
    }
}
