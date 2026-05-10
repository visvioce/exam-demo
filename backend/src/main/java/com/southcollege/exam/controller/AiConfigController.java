package com.southcollege.exam.controller;

import com.southcollege.exam.dto.response.Result;
import com.southcollege.exam.entity.AiConfig;
import com.southcollege.exam.service.AiConfigService;
import com.southcollege.exam.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 配置管理控制器
 * 提供用户AI API配置的增删改查、模型管理和激活切换功能，
 * 支持管理员和教师使用，API Key自动脱敏展示
 */
@Tag(name = "AI 配置管理", description = "AI API 配置增删改查")
@RestController
@RequestMapping("/api/ai-configs")
@com.southcollege.exam.annotation.RequireRole({com.southcollege.exam.enums.RoleEnum.ADMIN, com.southcollege.exam.enums.RoleEnum.TEACHER})
public class AiConfigController {

    private final AiConfigService aiConfigService;

    public AiConfigController(AiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    @GetMapping("/my")
    public Result<List<AiConfig>> getMyConfigs(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        List<AiConfig> configs = aiConfigService.getByUserId(userId);
        configs.forEach(c -> c.setApiKey(aiConfigService.maskApiKey(c.getApiKey())));
        return Result.success(configs);
    }

    @GetMapping("/my/active")
    public Result<AiConfig> getMyActiveConfig(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        AiConfig config = aiConfigService.getActiveByUserId(userId);
        if (config != null) {
            config.setApiKey(aiConfigService.maskApiKey(config.getApiKey()));
        }
        return Result.success(config);
    }

    @GetMapping("/{id}")
    public Result<AiConfig> getById(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        AiConfig config = aiConfigService.getById(id);
        if (config == null || !config.getUserId().equals(userId)) {
            return Result.error("无权访问此配置");
        }
        config.setApiKey(aiConfigService.maskApiKey(config.getApiKey()));
        return Result.success(config);
    }

    @PostMapping
    public Result<AiConfig> create(@RequestBody AiConfig aiConfig, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        aiConfig.setUserId(userId);

        String validationError = aiConfigService.validateAiConfig(aiConfig);
        if (validationError != null) {
            return Result.error(validationError);
        }

        if ((aiConfig.getActiveModel() == null || aiConfig.getActiveModel().isEmpty())
                && aiConfig.getModels() != null && !aiConfig.getModels().isEmpty()) {
            aiConfig.setActiveModel(aiConfig.getModels().get(0));
        }

        aiConfigService.save(aiConfig);
        aiConfig.setApiKey(aiConfigService.maskApiKey(aiConfig.getApiKey()));
        return Result.success(aiConfig);
    }

    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody AiConfig aiConfig, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        AiConfig existing = aiConfigService.getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.error("无权修改此配置");
        }

        if (aiConfig.getApiKey() == null
                || aiConfig.getApiKey().trim().isEmpty()
                || aiConfig.getApiKey().startsWith("****")) {
            aiConfig.setApiKey(existing.getApiKey());
        }

        String validationError = aiConfigService.validateAiConfig(aiConfig);
        if (validationError != null) {
            return Result.error(validationError);
        }

        aiConfig.setId(id);
        aiConfig.setUserId(userId);
        return Result.success(aiConfigService.updateById(aiConfig));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        AiConfig existing = aiConfigService.getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.error("无权删除此配置");
        }
        return Result.success(aiConfigService.removeById(id));
    }

    @PostMapping("/{id}/models")
    @Operation(summary = "添加模型", description = "向配置中添加一个新模型")
    public Result<AiConfig> addModel(
            @PathVariable Long id,
            @RequestBody AddModelRequest modelRequest,
            HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        AiConfig existing = aiConfigService.getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.error("无权操作此配置");
        }

        aiConfigService.addModel(id, modelRequest.getModel());
        AiConfig updated = aiConfigService.getById(id);
        updated.setApiKey(aiConfigService.maskApiKey(updated.getApiKey()));
        return Result.success(updated);
    }

    @DeleteMapping("/{id}/models/{model}")
    @Operation(summary = "删除模型", description = "从配置中删除指定模型")
    public Result<AiConfig> removeModel(
            @PathVariable Long id,
            @PathVariable String model,
            HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        AiConfig existing = aiConfigService.getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.error("无权操作此配置");
        }

        aiConfigService.removeModel(id, model);
        AiConfig updated = aiConfigService.getById(id);
        updated.setApiKey(aiConfigService.maskApiKey(updated.getApiKey()));
        return Result.success(updated);
    }

    @PostMapping("/{id}/models/{model}/activate")
    @Operation(summary = "激活模型(路径参数)", description = "将指定模型设为当前使用的模型（模型名通过URL路径传递）")
    public Result<AiConfig> activateModel(
            @PathVariable Long id,
            @PathVariable String model,
            HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        AiConfig existing = aiConfigService.getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.error("无权操作此配置");
        }

        aiConfigService.setActiveModel(id, model, userId);
        AiConfig updated = aiConfigService.getById(id);
        updated.setApiKey(aiConfigService.maskApiKey(updated.getApiKey()));
        return Result.success(updated);
    }

    @PostMapping("/{id}/activate-model")
    @Operation(summary = "激活模型(请求体)", description = "将指定模型设为当前使用的模型（模型名通过请求体传递，推荐使用）")
    public Result<AiConfig> activateModelByBody(
            @PathVariable Long id,
            @RequestBody ActivateModelRequest body,
            HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        AiConfig existing = aiConfigService.getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.error("无权操作此配置");
        }
        if (body == null || body.getModel() == null || body.getModel().trim().isEmpty()) {
            return Result.error("模型名称不能为空");
        }

        aiConfigService.setActiveModel(id, body.getModel().trim(), userId);
        AiConfig updated = aiConfigService.getById(id);
        updated.setApiKey(aiConfigService.maskApiKey(updated.getApiKey()));
        return Result.success(updated);
    }

    @GetMapping("/active-model")
    @Operation(summary = "获取激活模型", description = "获取当前用户激活的模型信息（API Key已脱敏）")
    public Result<ActiveModelInfo> getActiveModel(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        AiConfig config = aiConfigService.getActiveByUserId(userId);
        if (config == null || !config.hasActiveModel()) {
            return Result.success(null);
        }

        ActiveModelInfo info = new ActiveModelInfo();
        info.setConfigId(config.getId());
        info.setConfigName(config.getName());
        info.setBaseUrl(config.getBaseUrl());
        info.setApiKey(aiConfigService.maskApiKey(config.getApiKey()));
        info.setModel(config.getActiveModel());
        return Result.success(info);
    }

    @lombok.Data
    public static class AddModelRequest {
        private String model;
    }

    @lombok.Data
    public static class ActivateModelRequest {
        private String model;
    }

    @lombok.Data
    public static class ActiveModelInfo {
        private Long configId;
        private String configName;
        private String baseUrl;
        private String apiKey;
        private String model;
    }
}