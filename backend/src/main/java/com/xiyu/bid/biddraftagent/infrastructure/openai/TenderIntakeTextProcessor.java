package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.util.ArrayList;
import java.util.List;

class TenderIntakeTextProcessor {

    private static final int INTAKE_CONTEXT_RADIUS = 3;
    private static final int INTAKE_CONTEXT_MAX_CHARS = 20_000;
    private static final List<String> INTAKE_KEYWORDS = List.of(
            "项目名称", "项目标题", "标讯标题", "招标项目", "采购项目", "公告标题",
            "招标编号", "采购编号", "项目编号", "标段名称", "包号", "品目名称",
            "预算", "最高限价", "控制价", "金额", "人民币", "采购预算", "预算金额",
            "限价", "总价", "单价", "报价", "投标保证金", "总部", "所在地", "地区",
            "地点", "地址", "省", "市", "实施地点", "交货地点", "服务地点", "项目地点",
            "行政区划", "截止", "递交", "投标截止", "开标时间", "报名", "报名开始",
            "报名结束", "响应截止", "提交截止", "资格预审截止", "开标日期", "采购人",
            "采购单位", "招标人", "招标机构", "代理机构", "采购代理机构", "组织单位",
            "主办单位", "采购部门", "需求单位", "联系人", "联系方式", "经办人",
            "项目负责人", "负责人", "联系电话", "电话", "传真", "电子邮箱", "通讯地址",
            "客户类型", "优先级", "采购方式", "招标方式", "组织形式", "项目概况",
            "项目描述", "采购内容", "招标范围", "标签", "项目背景", "建设内容",
            "服务范围", "技术要求", "资格条件", "商务要求"
    );

    static String buildTenderIntakeCandidateText(String text) {
        String normalized = text == null ? "" : text;
        String[] lines = normalized.split("\\R");
        List<String> selected = new ArrayList<>();
        boolean[] include = new boolean[lines.length];
        for (int i = 0; i < lines.length; i++) {
            if (!containsIntakeKeyword(lines[i])) {
                continue;
            }
            int start = Math.max(0, i - INTAKE_CONTEXT_RADIUS);
            int end = Math.min(lines.length - 1, i + INTAKE_CONTEXT_RADIUS);
            for (int j = start; j <= end; j++) {
                include[j] = true;
            }
        }
        for (int i = 0; i < lines.length; i++) {
            if (include[i]) {
                selected.add(lines[i]);
            }
        }
        String candidate = String.join("\n", selected).trim();
        if (candidate.isBlank()) {
            candidate = normalized.substring(0, Math.min(normalized.length(), 8_000));
        }
        return candidate.length() <= INTAKE_CONTEXT_MAX_CHARS
                ? candidate
                : candidate.substring(0, INTAKE_CONTEXT_MAX_CHARS);
    }

    private static boolean containsIntakeKeyword(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        return INTAKE_KEYWORDS.stream().anyMatch(line::contains);
    }

    static String sanitizeUntrusted(String raw) {
        if (raw == null) return "";
        return raw.replace("<document>", "&lt;document&gt;").replace("</document>", "&lt;/document&gt;");
    }
}