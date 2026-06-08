package com.xiyu.bid.casework.domain.model;

import java.util.List;

public record CaseExportCriteria(
        String keyword,
        String scoringCategory,
        String customerType,
        List<String> projectTypes,
        String uploadDateFrom,
        String uploadDateTo,
        String closeDateFrom,
        String closeDateTo,
        List<String> statuses
) {
    public CaseExportCriteria {
        if (projectTypes == null) projectTypes = List.of();
        if (statuses == null) statuses = List.of();
    }

    public static CaseExportCriteria fromQueryParams(
            String keyword,
            String scoringCategory,
            String customerType,
            List<String> projectTypes,
            String uploadDateFrom,
            String uploadDateTo,
            String closeDateFrom,
            String closeDateTo,
            List<String> statuses) {
        return new CaseExportCriteria(
                keyword,
                scoringCategory,
                customerType,
                projectTypes != null ? projectTypes : List.of(),
                uploadDateFrom,
                uploadDateTo,
                closeDateFrom,
                closeDateTo,
                statuses != null ? statuses : List.of()
        );
    }
}
