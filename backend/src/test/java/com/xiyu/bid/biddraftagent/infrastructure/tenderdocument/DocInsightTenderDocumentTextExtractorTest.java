package com.xiyu.bid.biddraftagent.infrastructure.tenderdocument;

import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.docinsight.application.DocumentTextExtractor;
import com.xiyu.bid.docinsight.application.ExtractedDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocInsightTenderDocumentTextExtractorTest {

    @Mock
    private DocumentTextExtractor delegate;

    @Test
    void extract_shouldMapFieldsFromDocInsightResult() {
        TenderDocumentTextExtractor extractor = new DocInsightTenderDocumentTextExtractor(delegate);
        byte[] content = "招标内容".getBytes();
        when(delegate.extract("file.docx", "application/octet-stream", content))
                .thenReturn(new ExtractedDocument(
                        "正文",
                        2,
                        "{\"markdown\":\"正文\"}",
                        "markitdown-sidecar",
                        Map.of("key", "value")
                ));


        ExtractedTenderDocument result = extractor.extract("file.docx", "application/octet-stream", content);

        assertThat(result.fileName()).isEqualTo("file.docx");
        assertThat(result.contentType()).isEqualTo("application/octet-stream");
        assertThat(result.text()).isEqualTo("正文");
        assertThat(result.textLength()).isEqualTo(2);
        assertThat(result.extractorKey()).isEqualTo("markitdown-sidecar");
        assertThat(result.structuredMetadata()).isEqualTo("{\"markdown\":\"正文\"}");
    }
}
