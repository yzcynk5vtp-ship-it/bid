package com.xiyu.bid.casework.dto;

import java.util.List;

public record CaseExportQuery(
        String keyword,
        String scoringCategory,
        String customerType,
        List<String> projectTypes,
        List<String> statuses,
        String uploadDateFrom,
        String uploadDateTo,
        String closeDateFrom,
        String closeDateTo,
        String sortBy
) {
}
