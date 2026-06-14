package com.southcollege.exam.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程信息响应 DTO
 */
@Data
public class CourseResponse {

    private Long id;

    private String name;

    private String code;

    private String description;

    private String coverUrl;

    private Long teacherId;

    private String teacherName;

    private BigDecimal credits;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime deadline;
}