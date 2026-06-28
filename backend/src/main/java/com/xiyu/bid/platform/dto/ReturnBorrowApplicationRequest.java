package com.xiyu.bid.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for returning a borrowed platform account. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnBorrowApplicationRequest {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "新密码长度不能少于6位")
    private String newPassword;

    @NotNull(message = "实际归还时间不能为空")
    private String actualReturnedAt;

}
