package com.xiyu.bid.biddraftagent.infrastructure.tenderdocument;

import com.xiyu.bid.biddraftagent.application.LoadedTenderDocument;
import com.xiyu.bid.biddraftagent.application.StoredTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.docinsight.infrastructure.storage.LocalDocumentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocInsightTenderDocumentStorageTest {

    @TempDir
    private Path tempDir;

    @Test
    void storeAndLoadByFileUrl_shouldRoundTrip() {
        LocalDocumentStorage delegate = new LocalDocumentStorage(tempDir.toString());
        TenderDocumentStorage storage = new DocInsightTenderDocumentStorage(delegate, tempDir.toString());
        byte[] content = "招标正文内容".getBytes(StandardCharsets.UTF_8);

        StoredTenderDocument stored = storage.store(42L, "招标文件.docx", "application/octet-stream", content);
        LoadedTenderDocument loaded = storage.loadByFileUrl(stored.fileUrl()).orElseThrow();

        assertThat(loaded.content()).isEqualTo(content);
        assertThat(loaded.storedDocument().fileUrl()).isEqualTo(stored.fileUrl());
        assertThat(loaded.storedDocument().storagePath()).isEqualTo(stored.storagePath());
        assertThat(loaded.storedDocument().contentSha256()).isEqualTo(stored.contentSha256());
    }

    @Test
    void loadByFileUrl_shouldReturnEmptyForUnknownUrl() {
        LocalDocumentStorage delegate = new LocalDocumentStorage(tempDir.toString());
        TenderDocumentStorage storage = new DocInsightTenderDocumentStorage(delegate, tempDir.toString());

        assertThat(storage.loadByFileUrl("doc-insight://tender-file/99/not-exists.docx")).isEmpty();
    }

    @Test
    void loadByFileUrl_shouldRejectMalformedAndOutsidePaths() {
        LocalDocumentStorage delegate = new LocalDocumentStorage(tempDir.toString());
        TenderDocumentStorage storage = new DocInsightTenderDocumentStorage(delegate, tempDir.toString());

        assertThat(storage.loadByFileUrl(null)).isEmpty();
        assertThat(storage.loadByFileUrl("other://tender-file/1/file.docx")).isEmpty();
        assertThat(storage.loadByFileUrl("doc-insight://tender-file/1/../secret.docx")).isEmpty();
    }
}
