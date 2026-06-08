package com.xiyu.bid.qualification.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 批量关联附件结果 DTO
 */
@Data
@Builder
public class BatchAttachResultDTO {

    private int total;
    private int success;
    private int failed;
    private List<MatchedItem> matched;
    private List<UnmatchedItem> unmatched;

    @Data
    @Builder
    public static class MatchedItem {
        private String fileName;
        private String certificateNo;
        private Long qualificationId;
        private String qualificationName;
    }

    @Data
    @Builder
    public static class UnmatchedItem {
        private String fileName;
        private String reason;
    }
}
