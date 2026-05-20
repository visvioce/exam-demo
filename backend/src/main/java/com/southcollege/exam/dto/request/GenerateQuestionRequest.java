package com.southcollege.exam.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class GenerateQuestionRequest {

    @NotBlank(message = "学科不能为空")
    private String subject;

    @NotBlank(message = "题型不能为空")
    @Pattern(regexp = "SINGLE_CHOICE|MULTIPLE_CHOICE|TRUE_FALSE|FILL_BLANK|ESSAY",
            message = "题型不合法")
    private String type;

    @NotBlank(message = "难度不能为空")
    @Pattern(regexp = "EASY|MEDIUM|HARD",
            message = "难度不合法")
    private String difficulty;

    @Min(value = 1, message = "生成数量至少为1")
    @Max(value = 20, message = "单次最多生成20道题目")
    private Integer count = 5;

    private String requirements;
}
