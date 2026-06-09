package com.southcollege.exam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseSaveRequest {

    @NotBlank(message = "课程名称不能为空")
    @Size(max = 100, message = "课程名称不能超过100个字符")
    private String name;

    private String code;

    @Size(max = 500, message = "课程描述不能超过500个字符")
    private String description;

    @Pattern(regexp = "^(https?://.*)?$", message = "封面地址必须以 http:// 或 https:// 开头")
    private String coverUrl;

    private BigDecimal credits;
}