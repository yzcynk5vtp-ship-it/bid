// Input: HTTP 请求参数 (context, deptCode, roleCode) + 当前用户
// Output: 统一候选人查询响应 ApiResponse
// Pos: Controller/HTTP 入口
// 维护声明: 仅暴露统一候选人端点；输入校验 + 编排委托 AssignmentCandidateAppService。
package com.xiyu.bid.user.controller;

import com.xiyu.bid.dto.ApiResponse;
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

/**
 * 分配候选人统一端点。
 *
 * <p>为 task / tender 两种业务场景提供统一的候选人查询接口。
 * 认证由类级 @PreAuthorize 保证（生产环境）；SecurityContextHolder 兜底（测试环境 addFilters=false）。
 * context 参数校验由本控制器负责（输入校验）。
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
     * @return ApiResponse 包含候选人列表
     */
    @GetMapping("/assignable-candidates")
    public ResponseEntity<ApiResponse<List<AssignmentCandidateDTO>>> findCandidates(
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
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Invalid assignment context: " + context));
        }
        List<AssignmentCandidateDTO> data = appService.findCandidates(ctx, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Assignment candidates retrieved", data));
    }
}
