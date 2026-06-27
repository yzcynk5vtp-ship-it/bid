package com.xiyu.bid.casework.dto;

/**
 * 档案台账负责人筛选选项，支持统一 UserPicker 的「姓名（工号）」显示格式。
 */
public record ArchiveManagerOption(
    Long id,
    String name,
    String employeeNumber
) {
}
