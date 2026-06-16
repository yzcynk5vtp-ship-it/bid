package com.xiyu.bid.crm.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 按标讯信息查询 CRM 商机请求。
 * <p>对应前端标讯详情页「关联 CRM 商机」选择器的初始查询，
 * 按产品蓝图要求使用招标主体、报名截止时间、开标时间组合匹配。
 */
public record CustomerChanceSearchByTenderRequest(
    @JsonProperty("tenderer") String tenderer,
    @JsonProperty("registrationDeadline") String registrationDeadline,
    @JsonProperty("bidOpeningTime") String bidOpeningTime,
    @JsonProperty("pageIndex") int pageIndex,
    @JsonProperty("pageSize") int pageSize
) {}
