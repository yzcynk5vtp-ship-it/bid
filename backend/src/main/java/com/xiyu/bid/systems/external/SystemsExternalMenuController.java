package com.xiyu.bid.systems.external;

import com.xiyu.bid.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部系统菜单接口.
 *
 * <p>供统一组织架构系统通过 <code>/api/systems/external/menus</code>
 * 拉取本系统的完整菜单列表，用于菜单权限配置。</p>
 *
 * <h3>说明</h3>
 * <p>本接口已由 SecurityConfig 白名单放行，外部系统可直接调用。</p>
 */
@RestController
@RequestMapping("/api/systems/external/menus")
public class SystemsExternalMenuController {

    /** 外部菜单服务. */
    private final ExternalMenuService menuService;

    /**
     * 构造控制器.
     *
     * @param service 外部菜单服务
     */
    public SystemsExternalMenuController(final ExternalMenuService service) {
        this.menuService = service;
    }

    /**
     * 获取本系统完整菜单列表（含系统标识）.
     *
     * @return 菜单列表响应
     */
    @GetMapping
    public ResponseEntity<ApiResponse<ExternalMenuResponse>> getMenus() {
        ExternalMenuResponse data = menuService.getMenus();
        // 外部系统要求返回 code=0 表示成功
        ApiResponse<ExternalMenuResponse> resp = ApiResponse.<ExternalMenuResponse>builder()
                .success(true)
                .code(0)
                .message("ok")
                .data(data)
                .build();
        return ResponseEntity.ok(resp);
    }
}
