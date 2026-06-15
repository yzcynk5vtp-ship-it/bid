package com.xiyu.bid.docinsight.application;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentIntelligenceService {
    /**
     * 上传 + 解析一站式：先存储文件，再对存储文件执行 AI 分析。
     * 等价于 {@link #storeOnly} + {@link #processExisting}。
     */
    DocumentAnalysisResult process(String profileCode, String entityId, MultipartFile file);

    /**
     * 对已存储的文件执行 AI 解析（无需重新上传）。
     * 配合 {@link #storeOnly} 实现"上传即保存"两步流程。
     */
    DocumentAnalysisResult processExisting(String profileCode, String entityId, String storagePath, String fileName, String contentType);

    /**
     * 仅存储文件到后端，不执行 AI 解析。
     * 用于"上传即保存"流程 Step 1：先上传获取 fileUrl / storagePath，
     * AI 解析作为独立增强步骤通过 {@link #processExisting} 完成。
     */
    StoredDocument storeOnly(String profileCode, String entityId, MultipartFile file);
}
