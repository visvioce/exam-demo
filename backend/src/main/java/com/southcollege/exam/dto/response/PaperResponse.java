package com.southcollege.exam.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PaperResponse {

    private Long id;

    private String name;

    private String description;

    private Long teacherId;

    private LocalDateTime createdAt;

    private List<Long> questionIds;
}