package com.xiyu.bid.platform.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for approving a borrow application. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveRequest {

    @Size(max = 500, message = "审批意见不能超过500字")
    private String comment;
}
