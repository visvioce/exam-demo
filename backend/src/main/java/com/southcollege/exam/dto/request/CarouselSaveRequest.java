package com.southcollege.exam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CarouselSaveRequest {

    @NotBlank(message = "轮播图标题不能为空")
    @Size(max = 200, message = "轮播图标题不能超过200个字符")
    private String title;

    @NotBlank(message = "轮播图链接不能为空")
    @Pattern(regexp = "^https?://.*", message = "图片地址必须以 http:// 或 https:// 开头")
    private String imageUrl;

    @Pattern(regexp = "^(https?://.*)?$", message = "链接地址必须以 http:// 或 https:// 开头")
    private String linkUrl;

    private Integer sortOrder;

    private Boolean isActive;
}