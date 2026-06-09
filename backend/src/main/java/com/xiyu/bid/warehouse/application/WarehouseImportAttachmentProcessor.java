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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 仓库导入附件归档器：
 * 命名规范 WH_{仓库名称}_{附件类型}[_{序号}].{扩展名}。
 * 匹配成功后落盘并写入 attachment 实体。仅作副作用封装，不含业务规则。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseImportAttachmentProcessor {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final Pattern NAMING_PATTERN = Pattern.compile(
            "^WH_(.+?)_(产权证|发票|内外照片)(_\\d+)?\\.[A-Za-z0-9]+$");
    private static final Set<String> KNOWN_TYPE_LABELS = Set.of("产权证", "发票", "内外照片");

    private final WarehouseAttachmentRepository attachmentRepo;

    @Value("${warehouse.attachment.root:/data/attachments/warehouse}")
    private String attachmentRoot;

    @Transactional
    public AttachmentResult attachFiles(Map<String, WarehouseEntity> createdByName,
                                        List<WarehouseImportPolicy.ParsedRow> rows,
                                        MultipartFile[] attachments,
                                        Long uploaderId) {
        if (attachments == null || attachments.length == 0) return new AttachmentResult(0, List.of());

        Map<String, AttachmentBinding> byExpectedName = buildBindingMap(rows);
        int saved = 0;
        List<UnmatchedFile> unmatched = new ArrayList<>();
        for (MultipartFile mf : attachments) {
            if (mf == null || mf.isEmpty()) continue;
            String origName = mf.getOriginalFilename();
            if (origName == null) continue;

            // 1) 先按完整期望名直接匹配（按行/列填写提示生成的最常见命名）。
            AttachmentBinding binding = byExpectedName.get(origName);
            // 2) 直接匹配失败则按正则解析（支持 _N 序号）。
            if (binding == null) {
                binding = parseAndMatch(origName, byExpectedName);
            }
            // 3) 解析失败则归类未匹配并给出原因。
            if (binding == null) {
                unmatched.add(classifyUnmatched(origName, createdByName, rows));
                continue;
            }
            WarehouseEntity wh = createdByName.get(binding.row.sanitizedName);
            if (wh == null) {
                unmatched.add(new UnmatchedFile(origName, "仓库不存在"));
                continue;
            }

            try {
                saveOneAttachment(wh, mf, origName, binding.type, uploaderId);
                saved++;
            } catch (IOException e) {
                log.warn("附件归档失败: filename={}, error={}", origName, e.getMessage());
                unmatched.add(new UnmatchedFile(origName, "保存失败: " + e.getMessage()));
            }
        }
        return new AttachmentResult(saved, unmatched);
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

    /**
     * 使用正则解析文件名（带可选 _N 序号），找到对应的 warehouse row + attachment type。
     */
    private AttachmentBinding parseAndMatch(String filename, Map<String, AttachmentBinding> byExpectedName) {
        Matcher m = NAMING_PATTERN.matcher(filename);
        if (!m.matches()) return null;
        String name = m.group(1);
        String typeLabel = m.group(2);
        // 找第一个仓库名 == name 且未使用的 binding。
        for (Map.Entry<String, AttachmentBinding> e : byExpectedName.entrySet()) {
            if (e.getValue().used) continue;
            String key = e.getKey();
            int dot = key.lastIndexOf('.');
            String base = dot > 0 ? key.substring(0, dot) : key;
            if (base.startsWith("WH_" + name + "_") && base.contains("_" + typeLabel)) {
                e.getValue().used = true;
                return e.getValue();
            }
        }
        return null;
    }

    private UnmatchedFile classifyUnmatched(String filename,
                                           Map<String, WarehouseEntity> createdByName,
                                           List<WarehouseImportPolicy.ParsedRow> rows) {
        Matcher m = NAMING_PATTERN.matcher(filename);
        if (!m.matches()) {
            return new UnmatchedFile(filename, "命名格式不符");
        }
        String typeLabel = m.group(2);
        String name = m.group(1);
        if (!KNOWN_TYPE_LABELS.contains(typeLabel)) {
            return new UnmatchedFile(filename, "附件类型不识别: " + typeLabel);
        }
        boolean nameExists = createdByName.containsKey(name)
                || rows.stream().anyMatch(r -> r.sanitizedName.equals(name));
        if (!nameExists) {
            return new UnmatchedFile(filename, "仓库不存在");
        }
        return new UnmatchedFile(filename, "未匹配到对应行的附件槽位");
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

    private static class AttachmentBinding {
        final WarehouseImportPolicy.ParsedRow row;
        final WarehouseAttachmentType type;
        boolean used = false;

        AttachmentBinding(WarehouseImportPolicy.ParsedRow row, WarehouseAttachmentType type) {
            this.row = row;
            this.type = type;
        }
    }

    public record AttachmentResult(int matchedCount, List<UnmatchedFile> unmatched) {}
    public record UnmatchedFile(String filename, String reason) {}
}
