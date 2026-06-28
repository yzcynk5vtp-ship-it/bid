package com.xiyu.bid.crm.application;

/**
 * CRM 客户负责人查询结果（CO-302 反查路径第二步）.
 *
 * <p>对应接口 25259 {@code /customerManager/getCustomerManagerListByCompanyId}
 * 返回 dataList 中的单条记录。本接口只返回工号，不返回姓名。
 *
 * @param saleNo        负责人工号（用于标讯的 projectManagerId）
 * @param saleType      负责人类型
 * @param saleTypeText  负责人类型描述
 */
public record CustomerManagerResult(
        String saleNo,
        Integer saleType,
        String saleTypeText
) {}
