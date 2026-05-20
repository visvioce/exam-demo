package com.southcollege.exam.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExamSessionResponse {

    private Long id;

    private Long examId;

    private Long studentId;

    private String studentName;

    private LocalDateTime startedAt;

    private LocalDateTime submittedAt;

    private BigDecimal score;

    private BigDecimal totalScore;

    private String status;

    private String gradingStatus;

    private List<AnswerResponse> answers;

    @Data
    public static class AnswerResponse {
        private Long questionId;
        private String answer;
        private Boolean isCorrect;
        private BigDecimal score;
        private String questionType;
        private String gradingStatus;
        private String teacherComment;
    }
}