package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceUpsertCommand;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.parseCustomerLevel;
import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.parseCustomerType;
import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.parseDockingMethod;
import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.parseProjectType;
import lombok.extern.slf4j.Slf4j;

/**
 * 业绩单行导入服务（独立事务，避免自调用代理陷阱）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceRowImporter {

    private final PerformanceRepository repository;
    private final CreatePerformanceAppService createService;
    private final UpdatePerformanceAppService updateService;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_LOCAL_DATE;

    // 附件文件名列索引（与 PerformanceExcelTemplateGenerator TEMPLATE_HEADERS 对齐）
    private static final int IDX_CONTRACT_AGREEMENT = 19;
    private static final int IDX_MALL_SCREENSHOT = 21;
    private static final int IDX_SOE_DIRECTORY = 22;
    private static final int IDX_CATEGORY_PAGE = 23;
    private static final int IDX_RELATIONSHIP_PROOF = 24;
    private static final int IDX_BID_NOTICE = 26;

    @Transactional
    public ImportRowResult importRow(Row row, int rowNum) {
        String contractName = getCellStr(row, 0);
        if (contractName == null || contractName.isBlank()) {
            throw new IllegalArgumentException("合同名称不能为空");
        }
        LocalDate signingDate = parseDate(getCellStr(row, 9));
        LocalDate expiryDate = parseDate(getCellStr(row, 10));
        if (signingDate != null && expiryDate != null && !expiryDate.isAfter(signingDate)) {
            throw new IllegalArgumentException("截止日期须晚于签约日期");
        }
        var attachments = collectAttachmentEntries(row);
        var cmd = new PerformanceUpsertCommand(
                contractName, getCellStr(row, 1), getCellStr(row, 2),
                parseCustomerType(getCellStr(row, 3)), getCellStr(row, 4),
                parseProjectType(getCellStr(row, 5)), parseDockingMethod(getCellStr(row, 6)),
                parseCustomerLevel(getCellStr(row, 7)),
                signingDate, expiryDate, parseDate(getCellStr(row, 11)),
                getCellStr(row, 14), getCellStr(row, 15), getCellStr(row, 16),
                getCellStr(row, 17), getCellStr(row, 18), getCellStr(row, 20),
                "是".equals(getCellStr(row, 25)), getCellStr(row, 27),
                attachments);
        var existing = repository.findByContractName(contractName);
        Long performanceId;
        if (existing.isPresent()) {
            performanceId = updateService.update(existing.get().id(), cmd).id();
        } else {
            performanceId = createService.create(cmd).id();
        }
        return new ImportRowResult(contractName, performanceId, attachmentFileNames(row));
    }

    /**
     * 收集本行中填写的附件文件名条目（fileUrl 暂留空，由附件包归档器回填）。
     */
    private List<PerformanceUpsertCommand.AttachmentEntry> collectAttachmentEntries(Row row) {
        List<PerformanceUpsertCommand.AttachmentEntry> list = new ArrayList<>();
        addAttachmentEntry(list, getCellStr(row, IDX_CONTRACT_AGREEMENT), "CONTRACT_AGREEMENT");
        addAttachmentEntry(list, getCellStr(row, IDX_MALL_SCREENSHOT), "MALL_SCREENSHOT");
        addAttachmentEntry(list, getCellStr(row, IDX_SOE_DIRECTORY), "SOE_DIRECTORY");
        addAttachmentEntry(list, getCellStr(row, IDX_CATEGORY_PAGE), "CATEGORY_PAGE");
        addAttachmentEntry(list, getCellStr(row, IDX_RELATIONSHIP_PROOF), "RELATIONSHIP_PROOF");
        addAttachmentEntry(list, getCellStr(row, IDX_BID_NOTICE), "BID_NOTICE");
        return list;
    }

    private void addAttachmentEntry(List<PerformanceUpsertCommand.AttachmentEntry> list,
                                     String fileName, String fileType) {
        if (fileName == null || fileName.isBlank()) return;
        list.add(new PerformanceUpsertCommand.AttachmentEntry(fileName.trim(), "", fileType));
    }

    /**
     * 收集本行中所有附件文件名（供附件包归档器匹配使用）。
     */
    private List<AttachmentFileName> attachmentFileNames(Row row) {
        List<AttachmentFileName> names = new ArrayList<>();
        addFileName(names, getCellStr(row, IDX_CONTRACT_AGREEMENT), "CONTRACT_AGREEMENT");
        addFileName(names, getCellStr(row, IDX_MALL_SCREENSHOT), "MALL_SCREENSHOT");
        addFileName(names, getCellStr(row, IDX_SOE_DIRECTORY), "SOE_DIRECTORY");
        addFileName(names, getCellStr(row, IDX_CATEGORY_PAGE), "CATEGORY_PAGE");
        addFileName(names, getCellStr(row, IDX_RELATIONSHIP_PROOF), "RELATIONSHIP_PROOF");
        addFileName(names, getCellStr(row, IDX_BID_NOTICE), "BID_NOTICE");
        return names;
    }

    private void addFileName(List<AttachmentFileName> names, String fileName, String fileType) {
        if (fileName == null || fileName.isBlank()) return;
        names.add(new AttachmentFileName(fileName.trim(), fileType));
    }

    private String getCellStr(Row row, int idx) {
        var cell = row.getCell(idx);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate().toString();
                } else {
                    double val = cell.getNumericCellValue();
                    yield val == Math.rint(val) ? String.valueOf((long) val) : String.valueOf(val);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s, DTF); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("日期格式错误: " + s + "，应为 YYYY-MM-DD"); }
    }

    /** 单行导入结果：合同名、业绩 ID、本行附件文件名列表 */
    public record ImportRowResult(String contractName, Long performanceId,
                                   List<AttachmentFileName> attachmentFileNames) {}

    /** 附件文件名 + 类型 */
    public record AttachmentFileName(String fileName, String fileType) {}
}
