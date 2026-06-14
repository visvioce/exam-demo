package com.southcollege.exam.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 考试用题目响应 DTO，包含分值信息（不含正确答案）
 */
@Data
public class QuestionForExamResponse {

    private Long id;
    private String content;
    private String type;
    private String difficulty;
    private BigDecimal score;
    private String subject;
    private List<Option> options;

    private Integer blankCount;

    @Data
    public static class Option {
        private String id;
        private String text;
    }
}