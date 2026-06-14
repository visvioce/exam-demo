package com.southcollege.exam.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类
 * <p>角色权限控制已迁移至 Spring Security {@code @PreAuthorize} 注解，不再需要自定义拦截器。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}