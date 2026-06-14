package com.southcollege.exam.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息响应 DTO，不包含密码等敏感字段
 */
@Data
public class UserResponse {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String role;

    private String status;

    private LocalDateTime createdAt;
}
