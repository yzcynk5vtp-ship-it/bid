package com.xiyu.bid.casework.domain.model;

public record CaseExportZipEntry(
        String entryPath,
        byte[] content,
        long contentLength
) {
}
