package com.southcollege.exam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PaperSaveRequest {

    @NotBlank(message = "试卷名称不能为空")
    @Size(max = 200, message = "试卷名称不能超过200个字符")
    private String name;

    @Size(max = 1000, message = "试卷描述不能超过1000个字符")
    private String description;

    private List<Long> questionIds;
}