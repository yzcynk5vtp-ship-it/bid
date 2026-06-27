package com.xiyu.bid.qualification.service;

import com.xiyu.bid.businessqualification.application.command.QualificationUpsertCommand;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-368: 验证 QualificationDTO → QualificationUpsertCommand 的字段透传契约。
 * 重点保证前端传入的 fileUrlExplicitlySet 标记能完整到达 AppService。
 */
class QualificationDtoMapperTest {

    private final QualificationDtoMapper mapper = new QualificationDtoMapper();

    @Test
    @DisplayName("CO-368: DTO 显式传 fileUrlExplicitlySet=true 时，应透传到 Command（触发清空分支）")
    void toUpsertCommand_WithFileUrlExplicitlySetTrue_ShouldPropagateFlag() {
        QualificationDTO dto = QualificationDTO.builder()
                .name("ISO 9001")
                .subjectType(QualificationSubjectType.COMPANY)
                .subjectName("西域")
                .category(QualificationCategory.LICENSE)
                .fileUrl(null)
                .fileUrlExplicitlySet(true)
                .build();

        QualificationUpsertCommand command = mapper.toUpsertCommand(dto);

        assertThat(command.getFileUrlExplicitlySet()).isTrue();
        assertThat(command.getFileUrl()).isNull();
    }

    @Test
    @DisplayName("CO-368: DTO 不传 fileUrlExplicitlySet (null) 时，Command 字段应为 null（触发保留分支）")
    void toUpsertCommand_WithoutFileUrlExplicitlySet_ShouldHaveNullFlag() {
        QualificationDTO dto = QualificationDTO.builder()
                .name("ISO 9001")
                .subjectType(QualificationSubjectType.COMPANY)
                .subjectName("西域")
                .category(QualificationCategory.LICENSE)
                .fileUrl(null)
                // 不设 fileUrlExplicitlySet
                .build();

        QualificationUpsertCommand command = mapper.toUpsertCommand(dto);

        assertThat(command.getFileUrlExplicitlySet()).isNull();
        assertThat(command.getFileUrl()).isNull();
    }
}
