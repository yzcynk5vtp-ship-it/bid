package com.xiyu.bid.platform.util;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.entity.PlatformAccount;

/**
 * 判断当前用户是否为平台账户的绑定联系人。
 *
 * <p>CO-390 前 contactPerson 为姓名或“姓名（工号）”格式；CO-390 后升级为 userId。
 * 本工具同时兼容两种口径。</p>
 */
public final class PlatformAccountContactMatcher {

    private PlatformAccountContactMatcher() {
    }

    public static boolean isContactPerson(PlatformAccount account, User viewer) {
        String contactPerson = account.getContactPerson();
        if (contactPerson == null || contactPerson.isBlank() || viewer == null) {
            return false;
        }
        String viewerName = viewer.getFullName();
        if (viewerName != null && !viewerName.isBlank()) {
            String normalized = contactPerson.replaceFirst("（[^）]+）$", "").trim();
            if (normalized.equalsIgnoreCase(viewerName.trim())) {
                return true;
            }
            String employeeNumber = viewer.getEmployeeNumber();
            if (employeeNumber != null && !employeeNumber.isBlank()
                    && contactPerson.equals(viewerName + "（" + employeeNumber + "）")) {
                return true;
            }
        }
        return contactPerson.equals(String.valueOf(viewer.getId()));
    }
}
