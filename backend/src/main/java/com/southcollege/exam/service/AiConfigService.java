package com.southcollege.exam.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.dto.response.AiConfigResponse;
import com.southcollege.exam.entity.AiConfig;
import com.southcollege.exam.exception.BusinessException;
import com.southcollege.exam.mapper.AiConfigMapper;
import com.southcollege.exam.mapstruct.AiConfigDtoMapper;
import com.southcollege.exam.utils.AesUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI配置服务
 * 管理用户的AI API配置，包括增删改查、模型管理、API Key的加解密和校验
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigService extends ServiceImpl<AiConfigMapper, AiConfig> {

    private final AiConfigDtoMapper aiConfigDtoMapper;

    /**
     * 查询用户的全部AI配置，API Key自动解密
     */
    public List<AiConfig> getByUserId(Long userId) {
        List<AiConfig> configs = lambdaQuery()
                .eq(AiConfig::getUserId, userId)
                .list();
        configs.forEach(this::decryptApiKey);
        return configs;
    }

    /**
     * 查询用户当前激活的AI配置
     */
    public AiConfig getActiveByUserId(Long userId) {
        List<AiConfig> configs = getByUserId(userId);
        return configs.stream()
                .filter(AiConfig::hasActiveModel)
                .findFirst()
                .orElse(null);
    }

    /**
     * 保存AI配置，API Key自动加密。
     * 如果新配置设置了激活模型，先清除该用户其他配置的激活状态。
     */
    @Override
    @Transactional
    public boolean save(AiConfig entity) {
        encryptApiKey(entity);
        entity.setCreatedAt(LocalDateTime.now());

        // 如果新配置设置了激活模型，清除其他配置的激活状态，保证只有一个激活模型
        if (entity.getActiveModel() != null && !entity.getActiveModel().isEmpty()) {
            baseMapper.clearAllActiveModels(entity.getUserId());
        }

        return super.save(entity);
    }

    /**
     * 更新AI配置：
     * 如果传入的API Key为空或以星号开头，保留原Key；
     * 如果传入明文Key则加密存储；
     * 如果传入的已是加密Key则跳过加密；
     * 如果设置了激活模型，清除其他配置的激活状态。
     */
    @Override
    @Transactional
    public boolean updateById(AiConfig entity) {
        AiConfig existing = super.getById(entity.getId());
        if (existing == null) {
            throw new BusinessException("配置不存在，id=" + entity.getId());
        }

        String entityApiKey = entity.getApiKey();
        if (entityApiKey != null && !entityApiKey.isEmpty()) {
            if (isEncryptedApiKey(entityApiKey)) {
                log.warn("检测到传入的API Key已为加密状态（可能是解密失败残留），跳过重新加密，configId={}", entity.getId());
                entity.setApiKey(entityApiKey);
            } else {
                encryptApiKey(entity);
            }
        } else {
            entity.setApiKey(existing.getApiKey());
        }

        // 如果更新了激活模型，清除该用户其他配置的激活状态，保证只有一个激活模型
        if (entity.getActiveModel() != null && !entity.getActiveModel().isEmpty()) {
            baseMapper.clearAllActiveModels(entity.getUserId());
        }

        return super.updateById(entity);
    }

    /**
     * 根据ID查询配置，API Key自动解密
     */
    @Override
    public AiConfig getById(Serializable id) {
        AiConfig config = super.getById(id);
        if (config != null) {
            decryptApiKey(config);
        }
        return config;
    }

    /**
     * 向指定配置添加一个新模型
     *
     * @param configId 配置ID
     * @param model    模型名称
     */
    @Transactional
    public void addModel(Long configId, String model) {
        AiConfig config = lambdaQuery().eq(AiConfig::getId, configId).one();
        if (config == null) {
            throw new BusinessException("配置不存在");
        }

        List<String> models = config.getModels();
        if (models == null) {
            models = new ArrayList<>();
        }

        if (models.contains(model)) {
            throw new BusinessException("模型已存在");
        }

        models.add(model);

        // 如果还没有激活模型，自动激活第一个，同时清除其他配置的激活状态
        String newActiveModel = config.getActiveModel();
        if (newActiveModel == null || newActiveModel.isEmpty()) {
            newActiveModel = model;
            baseMapper.clearAllActiveModels(config.getUserId());
        }

        lambdaUpdate()
            .eq(AiConfig::getId, configId)
            .set(AiConfig::getModels, models)
            .set(AiConfig::getActiveModel, newActiveModel)
            .update();
    }

    /**
     * 从指定配置中移除一个模型，如果移除的是激活模型，自动切换到第一个可用模型
     */
    @Transactional
    public void removeModel(Long configId, String model) {
        AiConfig config = lambdaQuery().eq(AiConfig::getId, configId).one();
        if (config == null) {
            throw new BusinessException("配置不存在");
        }

        List<String> models = config.getModels();
        if (models == null || !models.contains(model)) {
            throw new BusinessException("模型不存在");
        }

        models.remove(model);

        String newActiveModel = config.getActiveModel();
        if (model.equals(newActiveModel)) {
            newActiveModel = models.isEmpty() ? null : models.get(0);
        }

        lambdaUpdate()
            .eq(AiConfig::getId, configId)
            .set(AiConfig::getModels, models)
            .set(AiConfig::getActiveModel, newActiveModel)
            .update();
    }

    /**
     * 设置用户的激活模型，同时清除该用户其他配置的激活状态
     *
     * @param configId 配置ID
     * @param model    模型名称
     * @param userId   用户ID（用于清除其他配置的激活状态）
     */
    @Transactional
    public void setActiveModel(Long configId, String model, Long userId) {
        if (model == null || model.trim().isEmpty()) {
            throw new BusinessException("模型名称不能为空");
        }

        log.info("开始设置激活模型: configId={}, model={}, userId={}", configId, model, userId);

        AiConfig config = lambdaQuery().eq(AiConfig::getId, configId).one();
        if (config == null) {
            throw new BusinessException("配置不存在，configId=" + configId);
        }

        List<String> models = config.getModels();
        if (models == null || models.isEmpty()) {
            throw new BusinessException("该配置没有模型列表，请先添加模型");
        }

        if (!models.contains(model)) {
            throw new BusinessException("模型 [" + model + "] 不存在于配置 [" + config.getName() + "] 的模型列表中，可用模型: " + String.join(", ", models));
        }

        baseMapper.clearAllActiveModels(userId);

        lambdaUpdate()
            .eq(AiConfig::getId, configId)
            .set(AiConfig::getActiveModel, model)
            .update();

        log.info("成功设置激活模型: configId={}, model={}", configId, model);
    }

    /**
     * 对API Key进行脱敏处理，只显示最后4位
     */
    public String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 校验AI配置的完整性和合法性
     *
     * @param aiConfig 待校验的AI配置
     * @return 校验错误信息，为null表示校验通过
     */
    public String validateAiConfig(AiConfig aiConfig) {
        if (aiConfig.getName() == null || aiConfig.getName().trim().isEmpty()) {
            return "配置名称不能为空";
        }
        if (aiConfig.getName().length() > 100) {
            return "配置名称不能超过100个字符";
        }

        if (aiConfig.getBaseUrl() == null || aiConfig.getBaseUrl().trim().isEmpty()) {
            return "API 地址不能为空";
        }
        if (!aiConfig.getBaseUrl().startsWith("http://") && !aiConfig.getBaseUrl().startsWith("https://")) {
            return "API 地址必须以 http:// 或 https:// 开头";
        }
        if (aiConfig.getBaseUrl().length() > 500) {
            return "API 地址不能超过500个字符";
        }

        String ssrfError = checkSsrf(aiConfig.getBaseUrl());
        if (ssrfError != null) {
            return ssrfError;
        }

        if (aiConfig.getApiKey() == null || aiConfig.getApiKey().trim().isEmpty()) {
            return "API Key 不能为空";
        }
        if (aiConfig.getApiKey().length() > 500) {
            return "API Key 不能超过500个字符";
        }

        if (aiConfig.getModels() != null) {
            for (String model : aiConfig.getModels()) {
                if (model == null || model.trim().isEmpty()) {
                    return "模型名称不能为空";
                }
                if (model.length() > 100) {
                    return "模型名称不能超过100个字符";
                }
            }
        }

        if (aiConfig.getActiveModel() != null && !aiConfig.getActiveModel().isEmpty()) {
            if (aiConfig.getModels() == null || !aiConfig.getModels().contains(aiConfig.getActiveModel())) {
                return "激活模型必须在模型列表中";
            }
        }

        return null;
    }

    /**
     * 加密API Key
     */
    private void encryptApiKey(AiConfig config) {
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            try {
                config.setApiKey(AesUtil.encrypt(config.getApiKey()));
            } catch (Exception e) {
                log.error("API Key 加密失败，configId={}", config.getId(), e);
                throw new BusinessException("API Key 加密失败，请检查系统配置");
            }
        }
    }

    /**
     * 解密API Key
     * <p>如果解密失败但 key 看起来是已加密的（十六进制长串），说明密钥不匹配，
     * 抛出明确错误提示用户重新配置。如果 key 看起来是明文（短串），则保留原值。</p>
     */
    private void decryptApiKey(AiConfig config) {
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            return;
        }

        String apiKey = config.getApiKey();
        try {
            String decrypted = AesUtil.decrypt(apiKey);

            if (isValidApiKey(decrypted)) {
                config.setApiKey(decrypted);
                log.debug("API Key 解密成功，configId={}", config.getId());
            } else {
                log.warn("解密后的 API Key 格式异常，可能使用了不同的加密密钥，保持原值，configId={}", config.getId());
            }
        } catch (Exception e) {
            // 如果 key 是十六进制加密格式但解密失败，说明 AES 密钥不匹配
            if (isEncryptedApiKey(apiKey)) {
                log.error("API Key 解密失败（AES密钥可能已变更），configId={}", config.getId(), e);
                config.setApiKey(null); // 清空，让用户重新输入
            } else {
                log.debug("API Key 解密失败，可能是明文存储，保持原值，configId={}", config.getId());
            }
        }
    }

    /**
     * 校验解密后的API Key是否为有效格式
     */
    private boolean isValidApiKey(String key) {
        if (key == null || key.length() < 10 || key.length() > 200) {
            return false;
        }
        for (char c : key.toCharArray()) {
            if (c < 32 || c > 126) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断API Key是否已加密（十六进制格式，长度≥64）
     */
    private boolean isEncryptedApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 32) {
            return false;
        }
        if (apiKey.length() % 2 != 0) {
            return false;
        }
        if (apiKey.length() < 64) {
            return false;
        }
        return apiKey.matches("^[0-9a-fA-F]+$");
    }

    public AiConfigResponse convertToResponse(AiConfig entity) {
        if (entity == null) return null;
        return aiConfigDtoMapper.toResponse(entity);
    }

    public List<AiConfigResponse> convertToResponses(List<AiConfig> entities) {
        if (entities == null || entities.isEmpty()) return List.of();
        return aiConfigDtoMapper.toResponseList(entities);
    }

    /**
     * SSRF 防护：检查 baseUrl 是否指向内部/私有地址
     */
    private String checkSsrf(String baseUrl) {
        try {
            java.net.URL url = new java.net.URL(baseUrl);
            String host = url.getHost().toLowerCase();

            if (host.isEmpty()) {
                return "API 地址格式不正确";
            }

            if ("localhost".equals(host) || "127.0.0.1".equals(host) || "0.0.0.0".equals(host) || "[::1]".equals(host)) {
                return "不允许使用本地回环地址";
            }

            if (isPrivateIpPrefix(host)) {
                return "不允许使用内网地址";
            }

            if (host.equals("169.254.169.254")) {
                return "不允许使用云元数据地址";
            }

            if (host.contains("metadata") && host.contains("internal")) {
                return "不允许使用内部元数据服务地址";
            }

            try {
                java.net.InetAddress resolved = java.net.InetAddress.getByName(host);
                String resolvedIp = resolved.getHostAddress();
                if (isPrivateIp(resolvedIp)) {
                    return "不允许使用内网地址（DNS解析后指向私有IP）";
                }
                if ("127.0.0.1".equals(resolvedIp) || "0.0.0.0".equals(resolvedIp)
                        || "::1".equals(resolvedIp) || resolvedIp.startsWith("0:0:0:0:0:0:0:1")) {
                    return "不允许使用本地回环地址（DNS解析后指向回环地址）";
                }
                if ("169.254.169.254".equals(resolvedIp)) {
                    return "不允许使用云元数据地址（DNS解析后指向元数据服务）";
                }
            } catch (java.net.UnknownHostException e) {
                return "API 地址无法解析，请检查域名是否正确";
            }
        } catch (java.net.MalformedURLException e) {
            return "API 地址格式不正确";
        }
        return null;
    }

    private boolean isPrivateIpPrefix(String host) {
        if (host.startsWith("10.")) return true;
        if (host.startsWith("192.168.")) return true;
        if (host.startsWith("172.")) {
            String[] parts = host.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) return true;
                } catch (NumberFormatException ignored) {
                    log.warn("172.x 网段第二段解析失败，非预期格式: {}", parts[1]);
                }
            }
        }
        return false;
    }

    private boolean isPrivateIp(String ip) {
        if (ip == null) return false;
        if (ip.startsWith("10.")) return true;
        if (ip.startsWith("192.168.")) return true;
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) return true;
                } catch (NumberFormatException ignored) {
                    log.warn("IP 172.x 网段第二段解析失败，非预期格式: {}", parts[1]);
                }
            }
        }
        if (ip.startsWith("169.254.")) return true;
        return false;
    }
}