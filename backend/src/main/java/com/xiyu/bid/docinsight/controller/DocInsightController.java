// Input: HTTP multipart/form-data 上传请求（profile, entityId, file）；parse-existing 请求（profile, entityId, storagePath, fileName, contentType）
// Output: DocumentAnalysisResult / StoredDocument 包装在 ApiResponse；边界校验（大小、类型、参数格式、项目访问范围）
// Pos: docinsight/controller — 文档智能分析 REST 入口
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.docinsight.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.docinsight.application.DocumentAnalysisResult;
import com.xiyu.bid.docinsight.application.DocumentIntelligenceService;
import com.xiyu.bid.docinsight.application.StoredDocument;
import com.xiyu.bid.docinsight.domain.DocInsightProfiles;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/doc-insight")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DocInsightController {

    /** 上传文件大小上限（MB），通过 app.docinsight.max-upload-mb 配置，默认 50 MB。 */
    @Value("${app.docinsight.max-upload-mb:50}")
    private int maxUploadMb;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private static final String TEXT_PLAIN = "text/plain";

    /** profileCode: 字母、数字、下划线、短横线，1-64 字符。 */
    private static final Pattern PROFILE_CODE_PATTERN = Pattern.compile("[A-Za-z0-9_\\-]{1,64}");

    /** entityId: 字母、数字、下划线、短横线，1-128 字符。 */
    private static final Pattern ENTITY_ID_PATTERN = Pattern.compile("[A-Za-z0-9_\\-]{1,128}");

    private final DocumentIntelligenceService docInsightService;

    /**
     * 上传 + 解析一站式端点（向后兼容）。
     * 内部先存储再解析，等价于 /store → /parse-existing 两步调用。
     */
    @PostMapping("/parse")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DocumentAnalysisResult>> parse(
            @RequestParam("profile") String profileCode,
            @RequestParam("entityId") String entityId,
            @RequestParam("file") MultipartFile file) {

        validateUploadFile(file);
        validateProfileCode(profileCode);
        validateContentType(profileCode, file.getContentType());
        validateEntityId(entityId);

        // Access scope + analysis (service layer guards project access)
        DocumentAnalysisResult result = docInsightService.process(profileCode, entityId, file);
        return ResponseEntity.ok(ApiResponse.success("文档解析完成", result));
    }

    /**
     * 仅存储文件，不执行 AI 解析。
     * 用于"上传即保存"流程 Step 1：文件选择后立即保存，获取 fileUrl / storagePath；
     * AI 解析作为独立增强步骤通过 /parse-existing 完成。
     */
    @PostMapping("/store")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<StoredDocument>> store(
            @RequestParam("profile") String profileCode,
            @RequestParam("entityId") String entityId,
            @RequestParam("file") MultipartFile file) {

        validateUploadFile(file);
        validateProfileCode(profileCode);
        validateContentType(profileCode, file.getContentType());
        validateEntityId(entityId);

        StoredDocument stored = docInsightService.storeOnly(profileCode, entityId, file);
        return ResponseEntity.ok(ApiResponse.success("文件存储成功", stored));
    }

    /**
     * 对已存储的文件执行 AI 解析（无需重新上传）。
     * 用于"上传即保存"流程 Step 2：先 /store 保存文件，再用此接口基于
     * storagePath 触发解析，避免重复上传。
     */
    @PostMapping("/parse-existing")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DocumentAnalysisResult>> parseExisting(
            @RequestParam("profile") String profileCode,
            @RequestParam("entityId") String entityId,
            @RequestParam("storagePath") String storagePath,
            @RequestParam("fileName") String fileName,
            @RequestParam(value = "contentType", required = false) String contentType) {

        validateProfileCode(profileCode);
        validateEntityId(entityId);
        validateStoragePath(storagePath);
        validateFileName(fileName);

        DocumentAnalysisResult result = docInsightService.processExisting(
                profileCode, entityId, storagePath, fileName, contentType);
        return ResponseEntity.ok(ApiResponse.success("文档解析完成", result));
    }

    // ── Shared validation helpers ──────────────────────────────────────────────

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件为空或未正确传输");
        }
        long maxBytes = (long) maxUploadMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "上传文件过大");
        }
    }

    private void validateProfileCode(String profileCode) {
        if (profileCode == null || profileCode.isBlank()
                || !PROFILE_CODE_PATTERN.matcher(profileCode).matches()) {
            throw new IllegalArgumentException("无效的解析配置标识");
        }
    }

    private void validateContentType(String profileCode, String contentType) {
        if (contentType == null || !isAllowedContentType(profileCode, contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的文件类型");
        }
    }

    private void validateEntityId(String entityId) {
        if (entityId == null || entityId.isBlank()
                || !ENTITY_ID_PATTERN.matcher(entityId).matches()) {
            throw new IllegalArgumentException("无效的实体标识");
        }
    }

    /**
     * storagePath 必须非空且不包含路径遍历字符（.. ），防止目录穿越攻击。
     */
    private void validateStoragePath(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("存储路径不能为空");
        }
        if (storagePath.contains("..")) {
            throw new IllegalArgumentException("存储路径不合法");
        }
    }

    /**
     * fileName 必须非空，防止后端解析时无法确定文件类型。
     */
    private void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
    }

    private boolean isAllowedContentType(String profileCode, String contentType) {
        String normalizedContentType = contentType.toLowerCase();
        return ALLOWED_CONTENT_TYPES.contains(normalizedContentType)
                || (TEXT_PLAIN.equals(normalizedContentType) && DocInsightProfiles.isTenderIntake(profileCode));
    }
}
