package com.xiyu.bid.casework.domain.model;

import com.xiyu.bid.casework.infrastructure.KnowledgeCase;

import java.time.LocalDateTime;
import java.util.List;

public record CaseExportContext(
        List<KnowledgeCase> cases,
        List<CaseExportZipEntry> zipEntries,
        String zipFileName,
        String operatorName,
        LocalDateTime exportTime
) {
}
