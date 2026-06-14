package com.southcollege.exam.mapstruct;

import com.southcollege.exam.dto.response.AiConfigResponse;
import com.southcollege.exam.entity.AiConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * AiConfig → AiConfigResponse 映射器
 * <p>apiKey 通过 {@link AiConfigDtoMapper#maskApiKey} 脱敏后输出</p>
 */
@Mapper(componentModel = "spring")
public interface AiConfigDtoMapper {

    @Mapping(target = "apiKey", source = "apiKey", qualifiedByName = "maskApiKey")
    AiConfigResponse toResponse(AiConfig config);

    List<AiConfigResponse> toResponseList(List<AiConfig> configs);

    /**
     * API Key 脱敏：只显示前4位和后4位，中间用 *** 代替
     */
    @Named("maskApiKey")
    static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return apiKey == null ? null : "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}