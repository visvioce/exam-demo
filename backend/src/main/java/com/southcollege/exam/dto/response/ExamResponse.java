package com.southcollege.exam.dto.response;

import com.southcollege.exam.entity.Exam;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExamResponse {

    private Long id;

    private String title;

    private String description;

    private Long courseId;

    private Long teacherId;

    private String teacherName;

    private String courseName;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private Integer duration;

    private BigDecimal totalScore;

    private BigDecimal passScore;

    private String status;

    private LocalDateTime createdAt;

    private String studentExamStatus;

    private Exam.ExamPaperData examPaper;

    private Long participantCount;

    private Long submittedCount;

    private Long pendingGradingCount;
}