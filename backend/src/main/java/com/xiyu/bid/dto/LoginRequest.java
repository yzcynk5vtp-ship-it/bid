package com.xiyu.bid.dto;

import com.xiyu.bid.annotation.Sensitive;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    @Sensitive
    private String password;

    private Boolean rememberMe = false;
}
