package com.southcollege.exam.dto.response;

import lombok.Data;

@Data
public class StatsResponse {
    private Long userCount;
    private Long courseCount;
    private Long questionCount;
    private Long examCount;
}