package com.xiyu.bid.crm.application;

/**
 * CRM 公司模糊查询结果（CO-302 反查路径第一步）.
 *
 * @param id        公司 ID（用于下一步查负责人）
 * @param name      公司名称（与招标主体精确匹配）
 * @param groupName 集团名
 */
public record CompanySearchResult(
        Long id,
        String name,
        String groupName
) {}
