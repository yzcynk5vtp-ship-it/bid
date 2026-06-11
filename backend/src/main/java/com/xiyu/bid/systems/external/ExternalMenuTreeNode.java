package com.xiyu.bid.systems.external;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部系统菜单节点.
 *
 * <p>符合客户方统一组织架构系统的菜单结构规范。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalMenuTreeNode {

    /** 菜单 id（等同 menuCode）. */
    private String id;

    /** 菜单名称. */
    private String menuName;

    /** 父级菜单 id（0 表示根节点）. */
    private String parentId;

    /** 菜单 code. */
    private String menuCode;

    /** 子菜单. */
    private List<ExternalMenuTreeNode> children;
}
