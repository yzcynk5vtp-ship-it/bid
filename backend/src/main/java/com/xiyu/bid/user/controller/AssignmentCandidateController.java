// Input: HTTP 请求参数 (context, deptCode, roleCode) + 当前用户
// Output: 统一候选人查询响应 { success, data }
// Pos: Controller/HTTP 入口
// 维护声明: 仅暴露统一候选人端点；编排委托 AssignmentCandidateAppService。
package com.xiyu.bid.user.controller;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.user.core.AssignmentContext;
import com.xiyu.bid.user.dto.AssignmentCandidateDTO;
import com.xiyu.bid.user.service.AssignmentCandidateAppService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 分配候选人统一端点。
 *
 * <p>为 task / tender 两种业务场景提供统一的候选人查询接口。
 * 缺失 context 参数由 Spring 自动返回 400；
 * 无效 context 由本控制器校验后返回 400；
 * 未认证请求由 @PreAuthorize（生产环境）或 SecurityContextHolder 检查（测试环境）返回 401。
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("isAuthenticated()")
public class AssignmentCandidateController {

    private final AssignmentCandidateAppService appService;

    public AssignmentCandidateController(AssignmentCandidateAppService appService) {
        this.appService = appService;
    }

    /**
     * 查询可分配候选人。
     *
     * @param context     业务场景（task / tender，必需）
     * @param deptCode    可选部门过滤参数（大小写不敏感）
     * @param roleCode    可选角色过滤参数（大小写不敏感）
     * @param currentUser 当前登录用户
     * @return { "success": true, "data": [...] }
     */
    @GetMapping("/assignable-candidates")
    public ResponseEntity<Map<String, Object>> findCandidates(
            @RequestParam String context,
            @RequestParam(required = false) String deptCode,
            @RequestParam(required = false) String roleCode,
            @AuthenticationPrincipal User currentUser) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AssignmentContext ctx = AssignmentContext.of(context, deptCode, roleCode);
        if (!ctx.isValidContextType()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid assignment context: " + context
            ));
        }
        List<AssignmentCandidateDTO> data = appService.findCandidates(ctx, currentUser);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data == null ? List.of() : data
        ));
    }
}
