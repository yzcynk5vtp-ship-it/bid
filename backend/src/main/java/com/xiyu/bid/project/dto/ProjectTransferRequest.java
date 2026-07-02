// Input: HTTP 请求体 JSON
// Output: 转移请求 DTO
// Pos: project/dto/ - 协议适配
// 维护声明: 仅维护请求字段绑定与基本校验；业务规则在 Service 层。

package com.xiyu.bid.project.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目转移请求 DTO。
 * <p>对应 FR-001：POST /api/projects/{projectId}/transfer 请求体。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTransferRequest {

    /** 新项目负责人用户 ID */
    @NotNull(message = "新负责人 ID 不能为空")
    @Positive(message = "新负责人 ID 必须为正整数")
    private Long newOwnerUserId;

    /** 转移原因（可选，最多 500 字符） */
    @Size(max = 500, message = "转移原因最多 500 字符")
    private String reason;
}
