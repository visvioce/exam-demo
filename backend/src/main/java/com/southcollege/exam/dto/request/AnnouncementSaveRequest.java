package com.southcollege.exam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公告保存请求 DTO
 */
@Data
public class AnnouncementSaveRequest {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 200, message = "公告标题不能超过200个字符")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    private String content;

    private Boolean isPinned;

    private String status;
}