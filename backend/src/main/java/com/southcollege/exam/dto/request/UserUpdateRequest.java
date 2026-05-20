package com.southcollege.exam.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {

    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;

    @Pattern(regexp = "ADMIN|TEACHER|STUDENT", message = "角色不合法")
    private String role;

    @Pattern(regexp = "ACTIVE|INACTIVE|SUSPENDED", message = "状态不合法")
    private String status;
}