package com.southcollege.exam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI 配置保存请求 DTO
 */
@Data
public class AiConfigSaveRequest {

    @NotBlank(message = "配置名称不能为空")
    @Size(max = 100, message = "配置名称不能超过100个字符")
    private String name;

    @NotBlank(message = "接口地址不能为空")
    @Size(max = 500, message = "接口地址不能超过500个字符")
    private String baseUrl;

    @Size(max = 500, message = "API密钥不能超过500个字符")
    private String apiKey;

    private List<String> models;

    private String activeModel;
}