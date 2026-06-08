package com.xiyu.bid.qualification.service;

import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.qualification.dto.BatchAttachResultDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BatchAttachmentService {

    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("^QUAL_([^_]+)_(\\d+)_(.+)\\.(.+)$");

    private final BusinessQualificationJpaRepository jpaRepository;

    @Transactional
    public BatchAttachResultDTO process(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return BatchAttachResultDTO.builder()
                    .total(0).success(0).failed(0)
                    .matched(List.of()).unmatched(List.of())
                    .build();
        }

        List<BatchAttachResultDTO.MatchedItem> matched = new ArrayList<>();
        List<BatchAttachResultDTO.UnmatchedItem> unmatched = new ArrayList<>();
        int success = 0;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) {
                unmatched.add(BatchAttachResultDTO.UnmatchedItem.builder()
                        .fileName("未知文件")
                        .reason("文件名为空")
                        .build());
                continue;
            }

            Matcher matcher = FILE_NAME_PATTERN.matcher(originalName);
            if (!matcher.matches()) {
                unmatched.add(BatchAttachResultDTO.UnmatchedItem.builder()
                        .fileName(originalName)
                        .reason("文件名格式不符，期望：QUAL_{证书编号}_{序号}_{文件名}.{扩展名}")
                        .build());
                continue;
            }

            String certificateNo = matcher.group(1);
            List<BusinessQualificationEntity> entities = jpaRepository.findByCertificateNo(certificateNo);
            if (entities.isEmpty()) {
                unmatched.add(BatchAttachResultDTO.UnmatchedItem.builder()
                        .fileName(originalName)
                        .reason("证书编号不存在：" + certificateNo)
                        .build());
                continue;
            }

            BusinessQualificationEntity entity = entities.get(0);
            entity.setFileUrl(originalName);
            jpaRepository.save(entity);

            matched.add(BatchAttachResultDTO.MatchedItem.builder()
                    .fileName(originalName)
                    .certificateNo(certificateNo)
                    .qualificationId(entity.getId())
                    .qualificationName(entity.getName())
                    .build());
            success++;
        }

        return BatchAttachResultDTO.builder()
                .total(files.size())
                .success(success)
                .failed(unmatched.size())
                .matched(matched)
                .unmatched(unmatched)
                .build();
    }
}
