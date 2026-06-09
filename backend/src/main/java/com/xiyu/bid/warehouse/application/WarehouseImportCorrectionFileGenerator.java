package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.warehouse.domain.WarehouseImportPolicy;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportExcelReader;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTemplateWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 仓库导入修正文件生成器：25 列（24 模板 + 导入结果），仅含失败行。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseImportCorrectionFileGenerator {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final WarehouseImportTemplateWriter templateWriter;

    @Value("${warehouse.import.root:/tmp/warehouse-imports}")
    private String importRoot;

    public String generate(Long taskId, List<WarehouseImportAppService.RowError> errors,
                           WarehouseImportExcelReader.SheetData sheet) {
        try {
            Path dir = Paths.get(importRoot);
            Files.createDirectories(dir);
            String ts = LocalDateTime.now().format(TS_FMT);
            String filename = "warehouse_import_correction_" + taskId + "_" + ts + ".xlsx";
            Path filePath = dir.resolve(filename);

            List<String[]> allRows = sheet.rows;
            List<String[]> errorRows = new ArrayList<>();
            String[] header = new String[WarehouseImportPolicy.TEMPLATE_HEADERS.length + 1];
            System.arraycopy(WarehouseImportPolicy.TEMPLATE_HEADERS, 0, header, 0,
                    WarehouseImportPolicy.TEMPLATE_HEADERS.length);
            header[header.length - 1] = "导入结果";
            errorRows.add(header);
            for (WarehouseImportAppService.RowError err : errors) {
                int idx = err.rowIndex() - 2;
                if (idx >= 0 && idx + 1 < allRows.size()) {
                    String[] orig = allRows.get(idx + 1);
                    String[] withResult = new String[orig.length + 1];
                    System.arraycopy(orig, 0, withResult, 0, orig.length);
                    withResult[orig.length] = err.message();
                    errorRows.add(withResult);
                }
            }
            byte[] bytes = templateWriter.writeWithExtraColumns(header, errorRows);
            Files.write(filePath, bytes);
            return filePath.toString();
        } catch (IOException e) {
            log.error("修正文件生成失败: taskId={}", taskId, e);
            return null;
        }
    }
}
