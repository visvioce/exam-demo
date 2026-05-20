package com.southcollege.exam.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @deprecated JWT 认证逻辑已合并到 {@link JwtAuthenticationFilter} 中，
 *             本类保留仅供参考，不再作为 Spring Bean 注册。
 */
@Deprecated
public class JwtInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        return true;
    }
}