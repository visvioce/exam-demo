package com.southcollege.exam.dto.response;

import lombok.Data;

/**
 * 登录响应 DTO，包含 JWT 令牌和用户信息
 */
@Data
public class LoginResponse {

    private String token;

    private UserResponse user;
}
