package com.xiyu.bid.systems.external;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * 提供外部统一组织架构系统的菜单树结构.
 *
 * <p>数据源为程序内常量，返回客户方规范的 {@link ExternalMenuTreeNode} 树。
 * id 和 menuCode 相同，parentId="0" 表示根节点。</p>
 */
@Service
public class ExternalMenuService {

    /** 菜单树缓存. */
    private final List<ExternalMenuTreeNode> cachedMenus;

    /** 构造服务并构建菜单树缓存. */
    public ExternalMenuService() {
        this.cachedMenus = buildMenuTree();
    }

    /**
     * 返回菜单树列表（一级菜单 + 子菜单）.
     *
     * @return 菜单树
     */
    public List<ExternalMenuTreeNode> getMenus() {
        return cachedMenus;
    }

    private static List<ExternalMenuTreeNode> buildMenuTree() {
        return List.of(
                // ── 工作台 ──
                node("1001", "工作台", "0", "1001", List.of()),

                // ── 标讯中心 ──
                node("1002", "标讯中心", "0", "1002", List.of(
                        node("100201", "标讯列表", "1002", "100201", List.of()),
                        node("100202", "新建标讯", "1002", "100202", List.of()),
                        node("100203", "关键词订阅", "1002", "100203", List.of())
                )),

                // ── 投标项目 ──
                node("1003", "投标项目", "0", "1003", List.of(
                        node("100301", "项目列表", "1003", "100301", List.of()),
                        node("100302", "创建项目", "1003", "100302", List.of())
                )),

                // ── 知识库 ──
                node("1004", "知识库", "0", "1004", List.of(
                        node("100401", "档案台账", "1004", "100401", List.of()),
                        node("100402", "资质库", "1004", "100402", List.of()),
                        node("100403", "人员库", "1004", "100403", List.of()),
                        node("100404", "业绩库", "1004", "100404", List.of()),
                        node("100405", "品牌授权", "1004", "100405", List.of()),
                        node("100406", "案例库", "1004", "100406", List.of()),
                        node("100407", "模板库", "1004", "100407", List.of())
                )),

                // ── 资源管理 ──
                node("1005", "资源管理", "0", "1005", List.of(
                        node("100501", "资产台账", "1005", "100501", List.of()),
                        node("100502", "保证金管理", "1005", "100502", List.of()),
                        node("100503", "费用管理", "1005", "100503", List.of()),
                        node("100504", "账户管理", "1005", "100504", List.of()),
                        node("100505", "CA 管理", "1005", "100505", List.of()),
                        node("100506", "合同借阅", "1005", "100506", List.of()),
                        node("100507", "结果闭环", "1005", "100507", List.of())
                )),

                // ── AI 智能中心 ──
                node("1006", "AI 智能中心", "0", "1006", List.of()),

                // ── 数据分析 ──
                node("1007", "数据分析", "0", "1007", List.of()),

                // ── 操作日志 ──
                node("1008", "操作日志", "0", "1008", List.of()),

                // ── 审计日志 ──
                node("1009", "审计日志", "0", "1009", List.of()),

                // ── 系统设置 ──
                node("1010", "系统设置", "0", "1010", List.of(
                        node("101001", "组织设置", "1010", "101001", List.of()),
                        node("101002", "组织架构", "1010", "101002", List.of()),
                        node("101003", "流程表单配置", "1010", "101003", List.of()),
                        node("101004", "告警规则", "1010", "101004", List.of()),
                        node("101005", "告警历史", "1010", "101005", List.of())
                ))
        );
    }

    private static ExternalMenuTreeNode node(final String id, final String menuName,
                                              final String parentId, final String menuCode,
                                              final List<ExternalMenuTreeNode> children) {
        return new ExternalMenuTreeNode(id, menuName, parentId, menuCode, children);
    }
}
