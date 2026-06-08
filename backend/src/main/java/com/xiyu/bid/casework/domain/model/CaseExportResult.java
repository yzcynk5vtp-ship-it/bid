package com.xiyu.bid.casework.domain.model;

public record CaseExportResult(
        byte[] zipBytes,
        String zipFileName,
        int caseCount,
        long totalSize
) {
}
