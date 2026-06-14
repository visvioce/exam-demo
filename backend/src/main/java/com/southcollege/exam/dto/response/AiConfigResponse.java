package com.southcollege.exam.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 配置响应 DTO
 */
@Data
public class AiConfigResponse {

    private Long id;

    private Long userId;

    private String name;

    private String baseUrl;

    private String apiKey;

    private List<String> models;

    private String activeModel;

    private LocalDateTime createdAt;
}