package com.xiyu.bid.businessqualification.domain.service;

import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;

/**
 * 资质证书创建校验策略（纯核心）。
 * 不依赖 Spring、不读写数据库、不修改入参。
 */
public class QualificationCreationPolicy {

    public QualificationValidationResult validateForCreate(BusinessQualification qualification) {
        if (isBlank(qualification.level())) {
            return QualificationValidationResult.invalid("等级不能为空");
        }
        if (isBlank(qualification.agency())) {
            return QualificationValidationResult.invalid("代理机构不能为空");
        }
        if (isBlank(qualification.agencyContact())) {
            return QualificationValidationResult.invalid("代理联系方式不能为空");
        }
        if (isBlank(qualification.certScope())) {
            return QualificationValidationResult.invalid("认证范围不能为空");
        }
        if (isBlank(qualification.certificateNo())) {
            return QualificationValidationResult.invalid("证书编号不能为空");
        }
        return QualificationValidationResult.success();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
