package com.xiyu.bid.warehouse.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.warehouse.application.WarehouseImportAppService;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTemplateWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 仓库批量导入控制器：模板下载、文件上传、任务状态、错误明细查询。
 */
@RestController
@RequestMapping("/api/knowledge/warehouses/import")
@RequiredArgsConstructor
public class WarehouseImportController {

    private static final String PERM = RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WarehouseImportAppService importAppService;
    private final WarehouseImportTemplateWriter templateWriter;
    private final UserResolver userResolver;

    @GetMapping("/template")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] bytes = templateWriter.write();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"warehouse_import_template.xlsx\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(bytes);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "attachments", required = false) MultipartFile[] attachments) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("请选择要导入的 Excel 文件"));
        }
        User user = userResolver.resolveCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登录"));
        }
        WarehouseImportAppService.ImportTaskResult result = importAppService.triggerImport(file, attachments, user);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("导入任务已创建", Map.of("taskId", result.taskId())));
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        User user = userResolver.resolveCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登录"));
        }
        Page<WarehouseImportTaskEntity> tasks = importAppService.listTasks(
                user.getId(), PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "content", tasks.getContent().stream().map(this::toTaskMap).toList(),
                "totalElements", tasks.getTotalElements(),
                "totalPages", tasks.getTotalPages(),
                "number", tasks.getNumber(),
                "size", tasks.getSize()
        )));
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTask(@PathVariable Long taskId) {
        User user = userResolver.resolveCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登录"));
        }
        try {
            WarehouseImportTaskEntity task = importAppService.getTask(taskId, user.getId());
            return ResponseEntity.ok(ApiResponse.success(toTaskMap(task)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/tasks/{taskId}/correction")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<byte[]> downloadCorrectionFile(@PathVariable Long taskId) {
        User user = userResolver.resolveCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            byte[] bytes = importAppService.getCorrectionFile(taskId, user.getId());
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String filename = "仓库信息导入_" + ts + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private Map<String, Object> toTaskMap(WarehouseImportTaskEntity t) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", t.getId());
        map.put("status", t.getStatus().name());
        map.put("totalRows", t.getTotalRows() != null ? t.getTotalRows() : 0);
        map.put("validRows", t.getValidRows() != null ? t.getValidRows() : 0);
        map.put("invalidRows", t.getInvalidRows() != null ? t.getInvalidRows() : 0);
        map.put("importedRows", t.getImportedRows() != null ? t.getImportedRows() : 0);
        map.put("errorDetails", t.getErrorDetails() != null ? t.getErrorDetails() : "");
        map.put("failureReason", t.getFailureReason() != null ? t.getFailureReason() : "");
        map.put("sourceFilename", t.getSourceFilename() != null ? t.getSourceFilename() : "");
        map.put("createdByUsername", t.getCreatedByUsername() != null ? t.getCreatedByUsername() : "");
        map.put("createdAt", formatDt(t.getCreatedAt()));
        map.put("completedAt", formatDt(t.getCompletedAt()));

        // 解析附件结果
        String details = t.getErrorDetails() == null ? "" : t.getErrorDetails();
        if (details.contains("[ATTACH_RESULT]")) {
            for (String line : details.split("\n")) {
                if (line.startsWith("[ATTACH_RESULT]")) {
                    String body = line.substring("[ATTACH_RESULT]".length()).trim();
                    for (String part : body.split("\\s+")) {
                        if (part.startsWith("matched=")) {
                            map.put("attachedCount", Integer.parseInt(part.substring("matched=".length())));
                        } else if (part.startsWith("unmatched=")) {
                            map.put("unmatchedCount", Integer.parseInt(part.substring("unmatched=".length())));
                        }
                    }
                }
            }
        }
        if (details.contains("[CORRECTION_FILE]")) {
            map.put("hasCorrectionFile", true);
            map.put("correctionFileUrl", "/api/knowledge/warehouses/import/tasks/" + t.getId() + "/correction");
        } else {
            map.put("hasCorrectionFile", false);
        }
        java.util.List<Map<String, String>> unmatched = new java.util.ArrayList<>();
        for (String line : details.split("\n")) {
            if (line.startsWith("[UNMATCHED] ")) {
                String body = line.substring("[UNMATCHED] ".length());
                int sep = body.indexOf(" | ");
                if (sep > 0) {
                    unmatched.add(Map.of("filename", body.substring(0, sep), "reason", body.substring(sep + 3)));
                }
            }
        }
        map.put("unmatchedFiles", unmatched);
        return map;
    }

    private String formatDt(LocalDateTime dt) {
        return dt != null ? dt.format(DT_FMT) : null;
    }
}
