package com.xiyu.bid.integration.external;

import com.xiyu.bid.tender.dto.TenderAttachmentDTO;
import com.xiyu.bid.tender.dto.TenderDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TenderIntegrationMapper.toDownloadUrl / toFullUrl 单元测试。
 * 覆盖 CO-280：CRM 跨域下载需要完整 URL。
 */
@DisplayName("TenderIntegrationMapper URL 转换")
class TenderIntegrationMapperToDownloadUrlTest {

    @BeforeEach
    void resetPublicBaseUrl() throws Exception {
        setPublicBaseUrl(null);
    }

    @AfterEach
    void cleanup() throws Exception {
        setPublicBaseUrl(null);
    }

    @Test
    @DisplayName("未配置 publicBaseUrl 时返回相对路径（同源部署）")
    void toDownloadUrl_noPublicBaseUrl_returnsRelativePath() {
        String result = TenderIntegrationMapper.toDownloadUrl("doc-insight://TENDER_INTAKE/test.pdf");
        assertThat(result).startsWith("/api/doc-insight/download?fileUrl=");
    }

    @Test
    @DisplayName("配置 publicBaseUrl 时返回完整 URL（跨域访问）")
    void toDownloadUrl_withPublicBaseUrl_returnsFullUrl() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String result = TenderIntegrationMapper.toDownloadUrl("doc-insight://TENDER_INTAKE/test.pdf");
        assertThat(result).startsWith("https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=");
    }

    @Test
    @DisplayName("doc-insight:// URL 被正确 URL 编码")
    void toDownloadUrl_docInsightUrl_isUrlEncoded() {
        String result = TenderIntegrationMapper.toDownloadUrl("doc-insight://TENDER_INTAKE/create-tender/test.docx");
        assertThat(result).contains("doc-insight%3A%2F%2F");
    }

    @Test
    @DisplayName("已是 /api/... 下载地址的不再二次包装（CO-283 幂等）")
    void toDownloadUrl_alreadyDownloadUrl_isIdempotent() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String input = "/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf";
        String result = TenderIntegrationMapper.toDownloadUrl(input);
        // 应只补全域名，不二次编码
        assertThat(result).isEqualTo("https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest.pdf");
    }

    @Test
    @DisplayName("http(s):// 外部 URL 原样返回，不包装")
    void toDownloadUrl_httpUrl_returnsAsIs() {
        String input = "https://image-c.ehsy.com/files/test.pdf";
        String result = TenderIntegrationMapper.toDownloadUrl(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("null 或空字符串原样返回")
    void toDownloadUrl_nullOrBlank_returnsAsIs() {
        assertThat(TenderIntegrationMapper.toDownloadUrl(null)).isNull();
        assertThat(TenderIntegrationMapper.toDownloadUrl("")).isEmpty();
        assertThat(TenderIntegrationMapper.toDownloadUrl("   ")).isEqualTo("   ");
    }

    @Test
    @DisplayName("toFullUrl 处理 doc-insight:// 前缀")
    void toFullUrl_docInsightUrl_returnsDownloadUrl() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String result = TenderIntegrationMapper.toFullUrl("doc-insight://TENDER_INTAKE/test.pdf");
        assertThat(result).startsWith("https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=");
    }

    @Test
    @DisplayName("toFullUrl 处理 /api/... 相对路径，补全域名")
    void toFullUrl_relativeApiPath_prependsPublicBaseUrl() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String result = TenderIntegrationMapper.toFullUrl("/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest");
        assertThat(result).isEqualTo("https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Ftest");
    }

    @Test
    @DisplayName("toFullUrl 处理 /api/... 相对路径，未配置 publicBaseUrl 时原样返回")
    void toFullUrl_relativeApiPath_noPublicBaseUrl_returnsAsIs() {
        String result = TenderIntegrationMapper.toFullUrl("/api/doc-insight/download?fileUrl=test");
        assertThat(result).isEqualTo("/api/doc-insight/download?fileUrl=test");
    }

    @Test
    @DisplayName("toFullUrl 处理 http(s):// 完整 URL，原样返回")
    void toFullUrl_httpUrl_returnsAsIs() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        String result = TenderIntegrationMapper.toFullUrl("https://image-c.ehsy.com/files/test.pdf");
        assertThat(result).isEqualTo("https://image-c.ehsy.com/files/test.pdf");
    }

    @Test
    @DisplayName("toFullUrl 处理 null 值")
    void toFullUrl_null_returnsNull() {
        String result = TenderIntegrationMapper.toFullUrl(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("normalizeFileUrls 同时处理 doc-insight:// 和 /api/... 格式")
    void normalizeFileUrls_handlesBothFormats() throws Exception {
        setPublicBaseUrl("https://winbid-test.ehsy.com");
        TenderDTO dto = new TenderDTO();
        dto.setSourceDocumentFileUrl("doc-insight://TENDER_INTAKE/source.docx");
        dto.setBidNoticeFileUrl("/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2Fnotice.docx");
        dto.setAttachments(java.util.List.of(
                TenderAttachmentDTO.builder()
                        .fileName("test.pdf")
                        .fileType("pdf")
                        .fileUrl("doc-insight://TENDER_INTAKE/test.pdf")
                        .build()
        ));

        // 使用反射调用 normalizeFileUrls（包私有方法）
        TenderIntegrationMapper mapper = new TenderIntegrationMapper(null, null);
        java.lang.reflect.Method method = TenderIntegrationMapper.class
                .getDeclaredMethod("normalizeFileUrls", TenderDTO.class);
        method.setAccessible(true);
        method.invoke(mapper, dto);

        assertThat(dto.getSourceDocumentFileUrl())
                .startsWith("https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=");
        assertThat(dto.getBidNoticeFileUrl())
                .startsWith("https://winbid-test.ehsy.com/api/doc-insight/download?");
        assertThat(dto.getAttachments().get(0).getFileUrl())
                .startsWith("https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=");
    }

    /**
     * 通过反射设置静态字段 publicBaseUrl，模拟 @Value 注入。
     */
    private static void setPublicBaseUrl(String value) throws Exception {
        Field field = TenderIntegrationMapper.class.getDeclaredField("publicBaseUrl");
        field.setAccessible(true);
        field.set(null, value);
    }
}
