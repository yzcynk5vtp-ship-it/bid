package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import com.xiyu.bid.tender.service.TenderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link TenderIntegrationMapper#toEntity} 的单元测试。
 */
class TenderIntegrationServiceMapToEntityTest {

    private TenderIntegrationMapper mapper;

    @BeforeEach
    void setUp() {
        TenderEvaluationIntegrationMapper evaluationMapper = new TenderEvaluationIntegrationMapper(
                mock(TenderEvaluationRepository.class),
                mock(TenderEvaluationSubmissionMapper.class));
        mapper = new TenderIntegrationMapper(
                mock(TenderMapper.class),
                evaluationMapper);
    }

    private TenderPushRequest baseRequest() {
        TenderPushRequest r = new TenderPushRequest();
        r.setTitle("测试标讯");
        return r;
    }

    @Test
    @DisplayName("带 crmId → 来源=CRM 创建，状态=已评估")
    void toEntity_withCrmId_setsCrmSourceAndEvaluatedStatus() {
        TenderPushRequest r = baseRequest();
        r.setCrmId("CC001");

        Tender t = mapper.toEntity(r);

        assertThat(t.getSourceType()).isEqualTo(Tender.SourceType.CRM_OPPORTUNITY);
        assertThat(t.getSource()).isEqualTo("CRM 创建");
        assertThat(t.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("不带 crmId → 来源=第三方平台，状态=待分配")
    void toEntity_withoutCrmId_setsExternalSourceAndPendingStatus() {
        TenderPushRequest r = baseRequest();

        Tender t = mapper.toEntity(r);

        assertThat(t.getSourceType()).isEqualTo(Tender.SourceType.EXTERNAL_PLATFORM);
        assertThat(t.getSource()).isEqualTo("第三方平台");
        assertThat(t.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
    }

    @Test
    @DisplayName("crmId 为空字符串 → 视为非 CRM 转入")
    void toEntity_emptyCrmId_treatedAsNonCrm() {
        TenderPushRequest r = baseRequest();
        r.setCrmId("");

        Tender t = mapper.toEntity(r);

        assertThat(t.getSourceType()).isEqualTo(Tender.SourceType.EXTERNAL_PLATFORM);
        assertThat(t.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
    }

    @Test
    @DisplayName("crmId 为空白字符 → 视为非 CRM 转入")
    void toEntity_blankCrmId_treatedAsNonCrm() {
        TenderPushRequest r = baseRequest();
        r.setCrmId("   ");

        Tender t = mapper.toEntity(r);

        assertThat(t.getSourceType()).isEqualTo(Tender.SourceType.EXTERNAL_PLATFORM);
        assertThat(t.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
    }

    @Test
    @DisplayName("CO-283: toDownloadUrl 对 doc-insight:// URL 转换为下载地址")
    void toDownloadUrl_docInsightUrl_convertsToDownloadUrl() {
        String url = mapper.toDownloadUrl("doc-insight://TENDER_INTAKE/abc/file.pdf");

        assertThat(url).isEqualTo("/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2FTENDER_INTAKE%2Fabc%2Ffile.pdf");
    }

    @Test
    @DisplayName("CO-283: toDownloadUrl 对已转换的下载地址保持幂等，避免双重嵌套")
    void toDownloadUrl_alreadyDownloadUrl_returnsAsIs() {
        String existing = "/api/doc-insight/download?fileUrl=https%3A%2F%2Fcrm.ehsy.com%2Ffile.pdf";

        String url = mapper.toDownloadUrl(existing);

        assertThat(url).isEqualTo(existing);
    }

    @Test
    @DisplayName("CO-283: toDownloadUrl 对普通外部 URL 原样返回")
    void toDownloadUrl_externalUrl_returnsAsIs() {
        String external = "https://crm.ehsy.com/attachment/file.pdf";

        String url = mapper.toDownloadUrl(external);

        assertThat(url).isEqualTo(external);
    }

    @Test
    @DisplayName("CO-283: toDownloadUrl 对空值保持空值")
    void toDownloadUrl_nullOrBlank_returnsAsIs() {
        assertThat(mapper.toDownloadUrl(null)).isNull();
        assertThat(mapper.toDownloadUrl("")).isEmpty();
        assertThat(mapper.toDownloadUrl("   ")).isEqualTo("   ");
    }
}
