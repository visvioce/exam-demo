package com.southcollege.exam.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class TypeConfig {
    @Positive(message = "题目数量必须为正数")
    private Integer count;

    @Pattern(regexp = "|EASY|MEDIUM|HARD",
            message = "难度不合法")
    private String difficulty;

    private String subject;
}