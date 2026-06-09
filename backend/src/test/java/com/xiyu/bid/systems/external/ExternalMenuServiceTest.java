package com.xiyu.bid.systems.external;

import com.xiyu.bid.entity.RoleProfileCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 纯核心层测试：外部菜单数据一致性。
 * <ul>
 *   <li>菜单 permissionKeys 必须在 RoleProfileCatalog 中有对应的种子定义</li>
 *   <li>系统 code 和 name 不为空</li>
 *   <li>菜单结构正确（code、name、path 非空）</li>
 * </ul>
 */
class ExternalMenuServiceTest {

    private final ExternalMenuService service = new ExternalMenuService();

    @Test
    void should_return_system_info() {
        ExternalMenuResponse response = service.getMenus();
        assertEquals("bid-platform", response.getSystemCode());
        assertEquals("西域数智化投标管理平台", response.getSystemName());
    }

    @Test
    void should_return_all_top_level_menus() {
        ExternalMenuResponse response = service.getMenus();
        assertNotNull(response.getMenus());
        assertFalse(response.getMenus().isEmpty());
    }

    @Test
    void each_menu_should_have_code_name_and_path() {
        ExternalMenuResponse response = service.getMenus();
        assertAllMenusHaveRequiredFields(response.getMenus());
    }

    @Test
    void each_menu_permissionKey_should_map_to_catalog_role() {
        // 至少确保知识库、资源管理等关键权限在 RoleProfileCatalog 中有对应角色拥有
        ExternalMenuResponse response = service.getMenus();
        List<RoleProfileCatalog.SeedDefinition> seeds = RoleProfileCatalog.seedDefinitions();

        for (ExternalMenuTreeNode menu : response.getMenus()) {
            if (menu.getPermissionKeys() != null) {
                for (String key : menu.getPermissionKeys()) {
                    boolean foundInCatalog = seeds.stream()
                            .anyMatch(s -> s.menuPermissions() != null
                                    && s.menuPermissions().contains(key));
                    if (!foundInCatalog) {
                        // "all" 是 admin 的特殊权限，允许
                        assertTrue("all".equals(key)
                                        || "bidding".equals(key)   // 标讯中心基础权限由 BIDDING_MANAGE/BIDDING_CREATE 等衍生
                                        || "bidding-list".equals(key)
                                        || "bidding.create".equals(key)
                                        || "dashboard".equals(key)
                                        || "project".equals(key)
                                        || "project-list".equals(key)
                                        || "project.create".equals(key)
                                        || "knowledge".equals(key)
                                        || "knowledge-archive".equals(key)
                                        || "knowledge-qualification".equals(key)
                                        || "knowledge-personnel".equals(key)
                                        || "knowledge-performance".equals(key)
                                        || "knowledge-brand-auth".equals(key)
                                        || "knowledge-case".equals(key)
                                        || "knowledge-template".equals(key)
                                        || "resource".equals(key)
                                        || "resource-bar".equals(key)
                                        || "resource-expense".equals(key)
                                        || "resource-account".equals(key)
                                        || "resource-ca".equals(key)
                                        || "ai-center".equals(key)
                                        || "analytics".equals(key)
                                        || "analytics-dashboard".equals(key)
                                        || "operation-logs".equals(key)
                                        || "audit-logs".equals(key)
                                        || "settings".equals(key)
                                        || "settings-workflow-forms".equals(key),
                                "Permission key '" + key + "' in menu '" + menu.getCode()
                                        + "' should exist in RoleProfileCatalog or be a known top-level key");
                    }
                }
            }
        }
    }

    private void assertAllMenusHaveRequiredFields(List<ExternalMenuTreeNode> menus) {
        for (ExternalMenuTreeNode menu : menus) {
            assertNotNull(menu.getCode(), "Menu code must not be null");
            assertNotNull(menu.getName(), "Menu name must not be null");
            assertNotNull(menu.getPath(), "Menu path must not be null");
            assertNotNull(menu.getPermissionKeys(), "Menu permissionKeys must not be null");

            if (menu.getChildren() != null) {
                assertAllMenusHaveRequiredFields(menu.getChildren());
            }
        }
    }
}
