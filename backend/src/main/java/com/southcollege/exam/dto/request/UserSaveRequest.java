package com.southcollege.exam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.southcollege.exam.validator.ValidPassword;
import lombok.Data;

/**
 * 创建用户请求 DTO
 */
@Data
public class UserSaveRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6-100之间")
    @ValidPassword
    private String password;

    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "ADMIN|TEACHER|STUDENT",
            message = "角色不合法")
    private String role;
}