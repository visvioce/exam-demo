package com.southcollege.exam.dto.response;

import lombok.Data;

/**
 * 统计数据响应 DTO
 */
@Data
public class StatsResponse {
    private Long userCount;
    private Long courseCount;
    private Long questionCount;
    private Long examCount;
}