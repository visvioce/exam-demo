package com.southcollege.exam.utils;

import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全工具类
 * 基于 Spring Security SecurityContextHolder 获取当前用户信息
 */
public class SecurityUtil {

    /**
     * 获取当前用户ID（带null检查）
     * @param request HTTP请求（保留参数兼容旧接口，实际从 SecurityContext 读取）
     * @return 用户ID
     * @throws BusinessException 如果用户未登录或token无效
     */
    public static Long getCurrentUserId(Object request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new BusinessException("用户未登录或token已失效，请重新登录");
        }
        return Long.valueOf(auth.getName());
    }

    /**
     * 获取当前用户ID（安全版本，返回null）
     * @param request HTTP请求（保留参数兼容旧接口，实际从 SecurityContext 读取）
     * @return 用户ID，可能为null
     */
    public static Long getCurrentUserIdSafe(Object request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        try {
            return Long.valueOf(auth.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取当前用户名（带null检查）
     * @param request HTTP请求（保留参数兼容旧接口，实际从 SecurityContext 读取）
     * @return 用户名
     * @throws BusinessException 如果用户未登录或token无效
     */
    public static String getCurrentUsername(Object request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new BusinessException("无法获取用户信息，请重新登录");
        }
        Object details = auth.getDetails();
        if (details instanceof java.util.Map) {
            String username = (String) ((java.util.Map<?, ?>) details).get("username");
            if (username != null) {
                return username;
            }
        }
        throw new BusinessException("无法获取用户信息，请重新登录");
    }

    /**
     * 获取当前用户角色（带null检查）
     * @param request HTTP请求（保留参数兼容旧接口，实际从 SecurityContext 读取）
     * @return 用户角色
     * @throws BusinessException 如果用户未登录或role无效
     */
    public static String getCurrentUserRole(Object request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new BusinessException("无法获取用户角色信息，请重新登录");
        }
        return auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(role -> role.replace("ROLE_", ""))
                .orElseThrow(() -> new BusinessException("无法获取用户角色信息，请重新登录"));
    }

    /**
     * 获取当前用户角色（安全版本，返回null）
     * @param request HTTP请求（保留参数兼容旧接口，实际从 SecurityContext 读取）
     * @return 用户角色，可能为null
     */
    public static String getCurrentUserRoleSafe(Object request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(role -> role.replace("ROLE_", ""))
                .orElse(null);
    }

    /**
     * 检查当前用户是否拥有指定角色
     * @param request HTTP请求（保留参数兼容旧接口）
     * @param requiredRoles 需要的角色列表
     * @return 是否拥有任一角色
     */
    public static boolean hasAnyRole(Object request, String... requiredRoles) {
        String role = getCurrentUserRoleSafe(request);
        if (role == null) return false;

        for (String required : requiredRoles) {
            if (role.equals(required)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否是管理员
     */
    public static boolean isAdmin(Object request) {
        return hasAnyRole(request, RoleEnum.ADMIN.getCode());
    }

    /**
     * 检查是否是教师
     */
    public static boolean isTeacher(Object request) {
        return hasAnyRole(request, RoleEnum.TEACHER.getCode());
    }

    /**
     * 检查是否是学生
     */
    public static boolean isStudent(Object request) {
        return hasAnyRole(request, RoleEnum.STUDENT.getCode());
    }
}