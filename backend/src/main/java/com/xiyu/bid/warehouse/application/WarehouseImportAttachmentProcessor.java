package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import com.xiyu.bid.warehouse.domain.WarehouseImportPolicy;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仓库导入附件归档器：根据 WH_{仓库名称}_{附件类型}.{扩展名} 命名匹配上传文件，
 * 匹配成功后落盘并写入 attachment 实体。仅作副作用封装，不含业务规则。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseImportAttachmentProcessor {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final WarehouseAttachmentRepository attachmentRepo;

    @Value("${warehouse.attachment.root:/data/attachments/warehouse}")
    private String attachmentRoot;

    @Transactional
    public int attachFiles(Map<String, WarehouseEntity> createdByName,
                           List<WarehouseImportPolicy.ParsedRow> rows,
                           MultipartFile[] attachments,
                           Long uploaderId) {
        if (attachments == null || attachments.length == 0) return 0;

        Map<String, AttachmentBinding> byExpectedName = buildBindingMap(rows);
        int saved = 0;
        for (MultipartFile mf : attachments) {
            if (mf == null || mf.isEmpty()) continue;
            String origName = mf.getOriginalFilename();
            if (origName == null) continue;
            AttachmentBinding binding = byExpectedName.get(origName);
            if (binding == null) continue;
            WarehouseEntity wh = createdByName.get(binding.row.sanitizedName);
            if (wh == null) continue;

            try {
                saveOneAttachment(wh, mf, origName, binding.type, uploaderId);
                saved++;
            } catch (IOException e) {
                log.warn("附件归档失败: filename={}, error={}", origName, e.getMessage());
            }
        }
        return saved;
    }

    private Map<String, AttachmentBinding> buildBindingMap(List<WarehouseImportPolicy.ParsedRow> rows) {
        Map<String, AttachmentBinding> byName = new HashMap<>();
        for (WarehouseImportPolicy.ParsedRow row : rows) {
            bind(byName, row.propertyCertExpectedName, row, WarehouseAttachmentType.PROPERTY_CERTIFICATE);
            bind(byName, row.invoiceExpectedName, row, WarehouseAttachmentType.INVOICE);
            bind(byName, row.photosExpectedName, row, WarehouseAttachmentType.PHOTOS);
        }
        return byName;
    }

    private void bind(Map<String, AttachmentBinding> byName, String expectedName,
                      WarehouseImportPolicy.ParsedRow row, WarehouseAttachmentType type) {
        if (expectedName == null || expectedName.isEmpty()) return;
        byName.put(expectedName, new AttachmentBinding(row, type));
    }

    private void saveOneAttachment(WarehouseEntity wh, MultipartFile mf, String origName,
                                   WarehouseAttachmentType type, Long uploaderId) throws IOException {
        String ext = extractExt(origName);
        String storedFilename = String.format("WH_%d_%s_%s.%s",
                wh.getId(), type.name(), LocalDateTime.now().format(TS_FMT), ext);
        Path dir = Paths.get(attachmentRoot, String.valueOf(wh.getId()));
        Files.createDirectories(dir);
        Path target = dir.resolve(storedFilename);
        Files.copy(mf.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        WarehouseAttachmentEntity entity = WarehouseAttachmentEntity.builder()
                .warehouse(wh)
                .type(type)
                .originalFilename(origName)
                .storedFilename(storedFilename)
                .fileSize(mf.getSize())
                .contentType(mf.getContentType() != null ? mf.getContentType() : "application/octet-stream")
                .uploadedBy(uploaderId)
                .uploadedAt(LocalDateTime.now())
                .build();
        attachmentRepo.save(entity);
    }

    private static String extractExt(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1) ? filename.substring(dot + 1).toLowerCase() : "bin";
    }

    private record AttachmentBinding(WarehouseImportPolicy.ParsedRow row, WarehouseAttachmentType type) {}
}
