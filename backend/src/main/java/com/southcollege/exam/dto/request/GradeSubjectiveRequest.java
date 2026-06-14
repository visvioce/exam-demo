package com.southcollege.exam.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 主观题评分请求 DTO
 */
@Data
public class GradeSubjectiveRequest {

    @NotNull(message = "考试记录ID不能为空")
    private Long examSessionId;

    @NotNull(message = "评分列表不能为空")
    @Size(min = 1, message = "至少需要评一题")
    private List<SubjectiveGrade> grades;

    @Data
    public static class SubjectiveGrade {

        @NotNull(message = "题目ID不能为空")
        private Long questionId;

        @NotNull(message = "得分不能为空")
        @Positive(message = "得分必须为正数")
        private BigDecimal score;

        private String comment;
    }
}