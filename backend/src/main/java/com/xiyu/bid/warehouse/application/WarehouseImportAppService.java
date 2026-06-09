package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.warehouse.domain.ImportTaskStatus;
import com.xiyu.bid.warehouse.domain.WarehouseActionType;
import com.xiyu.bid.warehouse.domain.WarehouseImportPolicy;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportExcelReader;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import com.xiyu.bid.warehouse.service.WarehouseLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仓库批量导入应用服务 — 编排 Excel 解析、校验、写入和附件归档。
 * 业务校验在 WarehouseImportPolicy 纯核心内；附件归档在 WarehouseImportAttachmentProcessor。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseImportAppService {

    private final WarehouseImportTaskRepository importTaskRepo;
    private final WarehouseRepository warehouseRepo;
    private final WarehouseImportExcelReader excelReader;
    private final WarehouseImportAttachmentProcessor attachmentProcessor;
    private final WarehouseLogService warehouseLogService;

    @Transactional
    public ImportTaskResult triggerImport(MultipartFile file, MultipartFile[] attachments, User operator) {
        WarehouseImportTaskEntity task = WarehouseImportTaskEntity.builder()
                .status(ImportTaskStatus.PENDING)
                .sourceFilename(file.getOriginalFilename())
                .createdBy(operator.getId())
                .createdByUsername(operator.getFullName() + "(" + operator.getUsername() + ")")
                .createdAt(LocalDateTime.now())
                .build();
        importTaskRepo.save(task);

        executeImportAsync(task.getId(), file, attachments, operator);

        return new ImportTaskResult(task.getId());
    }

    @Async("warehouseExportExecutor")
    public void executeImportAsync(Long taskId, MultipartFile file, MultipartFile[] attachments, User operator) {
        try {
            importTaskRepo.findById(taskId).ifPresent(task -> {
                task.setStatus(ImportTaskStatus.VALIDATING);
                importTaskRepo.save(task);
            });

            WarehouseImportExcelReader.SheetData sheet = excelReader.read(file);
            String[] header = sheet.header();
            List<String> headerErrors = WarehouseImportPolicy.validateHeader(header);
            if (!headerErrors.isEmpty()) {
                failTask(taskId, "表头校验失败: " + String.join("; ", headerErrors));
                return;
            }

            List<WarehouseImportPolicy.ParsedRow> rows = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            List<String[]> raw = sheet.dataRows();
            for (int i = 0; i < raw.size(); i++) {
                WarehouseImportPolicy.ParsedRow parsed = WarehouseImportPolicy.parseRow(i + 2, raw.get(i));
                if (parsed.valid()) {
                    rows.add(parsed);
                } else {
                    errors.add(new RowError(parsed.rowIndex, String.join("; ", parsed.errors)));
                }
            }

            updateCounts(taskId, raw.size(), rows.size(), errors.size());

            if (rows.isEmpty()) {
                completeTaskWithErrors(taskId, errors);
                return;
            }

            importTaskRepo.findById(taskId).ifPresent(task -> {
                task.setStatus(ImportTaskStatus.IMPORTING);
                importTaskRepo.save(task);
            });

            Map<String, WarehouseEntity> createdBySanitizedName = new HashMap<>();
            int imported = 0;
            for (WarehouseImportPolicy.ParsedRow row : rows) {
                try {
                    WarehouseEntity saved = persistRow(row, operator);
                    createdBySanitizedName.put(row.sanitizedName, saved);
                    imported++;
                } catch (RuntimeException ex) {
                    errors.add(new RowError(row.rowIndex, "保存失败: " + ex.getMessage()));
                }
            }

            int attached = attachmentProcessor.attachFiles(createdBySanitizedName, rows, attachments, operator.getId());
            completeTask(taskId, imported, errors, attached);
        } catch (IOException e) {
            log.error("仓库导入读取失败: taskId={}", taskId, e);
            failTask(taskId, "文件读取失败: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("仓库导入执行失败: taskId={}", taskId, e);
            failTask(taskId, truncate(e.getMessage(), 500));
        }
    }

    @Transactional
    protected WarehouseEntity persistRow(WarehouseImportPolicy.ParsedRow row, User operator) {
        WarehouseEntity entity = WarehouseEntity.builder()
                .name(row.sanitizedName)
                .type(row.type)
                .region(row.region)
                .province(row.province)
                .address(row.address)
                .area(row.area)
                .contactPerson(row.contactPerson)
                .remarks(row.remarks)
                .startDate(row.startDate)
                .endDate(row.endDate)
                .lessor(row.lessor)
                .lessee(row.lessee)
                .invoicePeriod(null)
                .invoicePeriodStart(row.invoicePeriodStart)
                .invoicePeriodEnd(row.invoicePeriodEnd)
                .closePlan(row.closePlan)
                .hasPropertyCert(row.hasPropertyCert)
                .hasInvoice(row.hasInvoice)
                .hasPhotos(row.hasPhotos)
                .certRemarks(row.certRemarks)
                .status(WarehouseStatus.IN_USE)
                .createdBy(operator.getId())
                .build();
        WarehouseEntity saved = warehouseRepo.save(entity);
        warehouseLogService.saveLog(saved, WarehouseActionType.CREATE,
                null, null, null,
                "批量导入新增 - " + saved.getName(),
                operator.getFullName() + "(" + operator.getUsername() + ")",
                operator.getId());
        return saved;
    }

    @Transactional
    protected void updateCounts(Long taskId, int total, int valid, int invalid) {
        importTaskRepo.findById(taskId).ifPresent(task -> {
            task.setTotalRows(total);
            task.setValidRows(valid);
            task.setInvalidRows(invalid);
            importTaskRepo.save(task);
        });
    }

    @Transactional
    protected void completeTaskWithErrors(Long taskId, List<RowError> errors) {
        importTaskRepo.findById(taskId).ifPresent(task -> {
            task.setStatus(ImportTaskStatus.COMPLETED);
            task.setErrorDetails(formatErrors(errors));
            task.setCompletedAt(LocalDateTime.now());
            importTaskRepo.save(task);
        });
    }

    @Transactional
    protected void completeTask(Long taskId, int imported, List<RowError> errors, int attached) {
        importTaskRepo.findById(taskId).ifPresent(task -> {
            task.setStatus(ImportTaskStatus.COMPLETED);
            task.setImportedRows(imported);
            task.setInvalidRows(errors.size());
            String errText = formatErrors(errors);
            if (attached > 0) {
                errText = "[已归档附件 " + attached + " 个]\n" + errText;
            }
            task.setErrorDetails(errText);
            task.setCompletedAt(LocalDateTime.now());
            importTaskRepo.save(task);
        });
    }

    @Transactional
    protected void failTask(Long taskId, String reason) {
        importTaskRepo.findById(taskId).ifPresent(task -> {
            task.setStatus(ImportTaskStatus.FAILED);
            task.setFailureReason(reason);
            task.setCompletedAt(LocalDateTime.now());
            importTaskRepo.save(task);
        });
    }

    private static String formatErrors(List<RowError> errors) {
        if (errors.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (RowError e : errors) {
            sb.append("第 ").append(e.rowIndex).append(" 行: ").append(e.message).append("\n");
        }
        return sb.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    public Page<WarehouseImportTaskEntity> listTasks(Long createdBy, Pageable pageable) {
        return importTaskRepo.findByCreatedByOrderByCreatedAtDesc(createdBy, pageable);
    }

    public WarehouseImportTaskEntity getTask(Long taskId, Long createdBy) {
        return importTaskRepo.findByIdAndCreatedBy(taskId, createdBy)
                .orElseThrow(() -> new IllegalArgumentException("导入任务不存在或无权限"));
    }

    private record RowError(int rowIndex, String message) {}

    public record ImportTaskResult(Long taskId) {}
}
