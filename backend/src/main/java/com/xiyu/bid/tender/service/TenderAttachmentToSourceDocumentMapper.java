package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.tender.dto.TenderAttachmentDTO;
import com.xiyu.bid.tender.dto.TenderDTO;

import java.util.List;
import java.util.Optional;

/**
 * 把 TenderDTO.attachments 数组的第一个有效附件同步到 Tender.sourceDocument* 字段。
 * 用于兼容仍读取单文件字段的下游（如标书生成 Agent 复用招标文件）。
 */
final class TenderAttachmentToSourceDocumentMapper {

    private TenderAttachmentToSourceDocumentMapper() {
    }

    static Tender apply(Tender target, TenderDTO dto) {
        Optional.ofNullable(dto).map(TenderDTO::getAttachments).filter(a -> !a.isEmpty()).map(a -> a.iterator().next())
                .filter(a -> a.getFileUrl() != null && !a.getFileUrl().isBlank()).ifPresent(a -> {
                    target.setSourceDocumentName(a.getFileName());
                    target.setSourceDocumentFileType(a.getFileType());
                    target.setSourceDocumentFileUrl(a.getFileUrl());
                });
        return target;
    }
}
