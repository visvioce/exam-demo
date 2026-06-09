package com.southcollege.exam.service;

import com.southcollege.exam.dto.response.GeneratedQuestionResponse;
import com.southcollege.exam.entity.AiConfig;
import com.southcollege.exam.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * AI出题服务（基于 Spring AI）
 * 使用 ChatClient 调用 AI 接口生成题目，支持 Prompt 模板和结构化输出
 */
@Slf4j
@Service
public class AiQuestionService {

    private final AiConfigService aiConfigService;

    @Value("classpath:/prompts/generate-questions.st")
    private Resource promptTemplateResource;

    public AiQuestionService(AiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    /**
     * 生成题目
     * @param userId 用户ID，用于获取AI配置
     * @param subject 科目
     * @param type 题目类型
     * @param difficulty 难度
     * @param count 数量
     * @param requirements 额外要求
     * @return 生成的题目列表
     */
    public GeneratedQuestionResponse generateQuestions(Long userId, String subject, String type,
                                                       String difficulty, Integer count, String requirements) {
        AiConfig config = aiConfigService.getActiveByUserId(userId);
        if (config == null || !config.hasActiveModel()) {
            throw new BusinessException("未配置AI或没有激活的模型");
        }

        Prompt prompt = buildPrompt(subject, type, difficulty, count, requirements);
        ChatClient chatClient = createChatClient(config);

        try {
            return chatClient.prompt(prompt)
                    .call()
                    .entity(GeneratedQuestionResponse.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            handleAiException(e);
            throw new BusinessException("AI调用失败，请稍后重试");
        }
    }

    /**
     * 流式生成题目（SSE）
     * 直接返回 Flux<ServerSentEvent<String>>，Spring 自动处理 SSE 协议
     * 前端可实时看到 AI 生成的原始文本（打字机效果）
     */
    public Flux<ServerSentEvent<String>> generateQuestionsStream(Long userId, String subject, String type,
                                                                   String difficulty, Integer count, String requirements) {
        AiConfig config = aiConfigService.getActiveByUserId(userId);
        if (config == null || !config.hasActiveModel()) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("未配置AI或没有激活的模型，请先在个人中心配置并激活AI模型")
                    .build());
        }

        Prompt prompt = buildPrompt(subject, type, difficulty, count, requirements);
        ChatClient chatClient = createChatClient(config);

        return chatClient.prompt(prompt)
                .stream()
                .content()
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("chunk")
                        .data(chunk)
                        .build())
                // 流正常结束时发送 done 事件，让前端明确知道生成已完成
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("")
                        .build()))
                .onErrorResume(e -> {
                    log.error("流式AI调用失败", e);
                    String msg = e.getMessage() != null ? e.getMessage() : "AI服务调用失败";
                    if (msg.contains("401") || msg.contains("403")) {
                        msg = "API Key 无效或已过期，请检查AI配置";
                    } else if (msg.contains("429")) {
                        msg = "AI服务请求过于频繁，请稍后重试";
                    } else if (msg.contains("timeout") || msg.contains("Timeout")) {
                        msg = "AI服务响应超时，请稍后重试";
                    } else if (msg.contains("Connection refused") || msg.contains("connect")) {
                        msg = "无法连接AI服务，请检查API地址是否正确";
                    }
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data(msg)
                            .build());
                });
    }

    /**
     * 根据用户配置动态构建 ChatClient
     * 支持多用户各自配置不同的 API Key / Base URL / 模型
     */
    private ChatClient createChatClient(AiConfig config) {
        // AiConfigService.getActiveByUserId() 已经解密过 apiKey，直接使用即可
        String apiKey = config.getApiKey();
        String baseUrl = normalizeBaseUrl(config.getBaseUrl());
        var openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        var chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(config.getActiveModel())
                        .build())
                .build();
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 从模板文件构建 Prompt
     * 使用简单字符串替换，避免 StringTemplate 语法冲突
     */
    private Prompt buildPrompt(String subject, String type, String difficulty, Integer count, String requirements) {
        String template = loadTemplate();
        String prompt = template
                .replace("{subject}", subject != null ? subject : "")
                .replace("{type}", type != null ? type : "")
                .replace("{difficulty}", difficulty != null ? difficulty : "")
                .replace("{count}", count != null ? String.valueOf(count) : "1")
                .replace("{requirements}", requirements != null && !requirements.isEmpty() ? requirements : "无");
        return new Prompt(new UserMessage(prompt));
    }

    private String loadTemplate() {
        try {
            return StreamUtils.copyToString(promptTemplateResource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("加载AI出题模板失败");
        }
    }

    /**
     * 智能规范化 baseUrl，确保传给 OpenAiApi 的地址格式正确。
     * <p>Spring AI 的 OpenAiApi 内部默认 completionsPath = "/v1/chat/completions"，
     * 因此 baseUrl 应该只是根域名（如 https://api.openai.com），不需要带 /v1。</p>
     * <p>处理规则：</p>
     * <ul>
     *   <li>去除末尾多余斜杠</li>
     *   <li>如果用户误填了完整路径（如 .../v1/chat/completions），截断到根域名</li>
     *   <li>如果用户配置了 /v1 结尾，去掉 /v1（Spring AI 内部会自动加）</li>
     * </ul>
     */
    private String normalizeBaseUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BusinessException("AI配置的 baseUrl 为空");
        }

        String url = rawUrl.trim();

        // 去除末尾斜杠
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // 如果用户把完整路径填进来了，截断回根域名
        if (url.endsWith("/chat/completions")) {
            url = url.substring(0, url.length() - "/chat/completions".length());
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
        }

        // 如果末尾是 /v1，去掉它（Spring AI 内部会自动拼接 /v1/chat/completions）
        if (url.endsWith("/v1")) {
            url = url.substring(0, url.length() - "/v1".length());
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
        }

        return url;
    }

    /**
     * 统一处理 AI 调用异常
     */
    private void handleAiException(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            log.error("AI调用失败", e);
            return;
        }
        if (msg.contains("401") || msg.contains("403")) {
            log.error("AI API Key 无效", e);
            throw new BusinessException("API Key 无效或已过期，请检查AI配置");
        }
        if (msg.contains("429")) {
            throw new BusinessException("AI服务请求过于频繁，请稍后重试");
        }
        if (msg.contains("timeout") || msg.contains("Timeout")) {
            log.error("AI调用超时", e);
            throw new BusinessException("AI服务响应超时，请稍后重试");
        }
        if (msg.contains("Connection refused") || msg.contains("connect")) {
            log.error("AI服务连接失败", e);
            throw new BusinessException("无法连接AI服务，请检查API地址是否正确");
        }
        log.error("AI调用失败", e);
    }
}
