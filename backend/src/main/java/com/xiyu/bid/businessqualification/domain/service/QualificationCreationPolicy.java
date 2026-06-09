package com.xiyu.bid.businessqualification.domain.service;

import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;

import java.util.regex.Pattern;

/**
 * 资质证书创建校验策略（纯核心）。
 * 不依赖 Spring、不读写数据库、不修改入参。
 */
public class QualificationCreationPolicy {

    private static final Pattern CONTACT_PATTERN = Pattern.compile(
            "^(1[3-9]\\d{9}|(0\\d{2,3})[-]?\\d{7,8}|[^\\s@]+@[^\\s@]+\\.[^\\s@]+)$"
    );

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
        if (!CONTACT_PATTERN.matcher(qualification.agencyContact()).matches()) {
            return QualificationValidationResult.invalid("代理联系方式格式不正确，请输入有效的手机号、固话或邮箱");
        }
        if (isBlank(qualification.certScope())) {
            return QualificationValidationResult.invalid("认证范围不能为空");
        }
        if (isBlank(qualification.certificateNo())) {
            return QualificationValidationResult.invalid("证书编号不能为空");
        }
        ValidityPeriod period = qualification.validityPeriod();
        if (period == null) {
            return QualificationValidationResult.invalid("有效期不能为空");
        }
        if (period.getIssueDate() != null && period.getExpiryDate() != null
                && period.getIssueDate().isAfter(period.getExpiryDate())) {
            return QualificationValidationResult.invalid("证书发证日期不可晚于到期日期");
        }
        return QualificationValidationResult.success();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
