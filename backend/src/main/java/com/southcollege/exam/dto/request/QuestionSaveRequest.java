package com.southcollege.exam.dto.request;

import com.southcollege.exam.entity.Question;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class QuestionSaveRequest {

    @NotBlank(message = "题目内容不能为空")
    private String content;

    @NotNull(message = "题目类型不能为空")
    @Pattern(regexp = "SINGLE_CHOICE|MULTIPLE_CHOICE|TRUE_FALSE|FILL_BLANK|ESSAY",
            message = "题型不合法")
    private String type;

    @NotNull(message = "题目难度不能为空")
    @Pattern(regexp = "EASY|MEDIUM|HARD",
            message = "难度不合法")
    private String difficulty;

    private String subject;

    private String explanation;

    private List<Question.Option> options;

    private Object correctAnswer;

    private List<Question.ScoringCriterion> scoringCriteria;
}