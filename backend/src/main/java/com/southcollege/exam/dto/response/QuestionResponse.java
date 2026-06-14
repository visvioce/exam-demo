package com.southcollege.exam.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 题目信息响应 DTO，包含选项和评分标准
 */
@Data
public class QuestionResponse {

    private Long id;

    private String content;

    private String type;

    private String difficulty;

    private Long teacherId;

    private String subject;

    private String explanation;

    private List<OptionResponse> options;

    private Object correctAnswer;

    private List<ScoringCriterionResponse> scoringCriteria;

    @Data
    public static class OptionResponse {
        private String id;
        private String text;
    }

    @Data
    public static class ScoringCriterionResponse {
        private String point;
        private BigDecimal score;
    }
}