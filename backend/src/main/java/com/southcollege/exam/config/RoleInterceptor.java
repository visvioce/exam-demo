package com.southcollege.exam.config;

import com.southcollege.exam.annotation.RequireRole;
import com.southcollege.exam.entity.User;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.enums.UserStatusEnum;
import com.southcollege.exam.exception.BusinessException;
import com.southcollege.exam.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class RoleInterceptor implements HandlerInterceptor {

    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);

        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }

        if (requireRole == null) {
            return true;
        }

        String userRole = (String) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");

        if (userId == null) {
            throw new BusinessException("未登录或 token 无效");
        }

        if (userRole == null) {
            User user = userService.getById(userId);
            if (user == null) {
                throw new BusinessException("用户不存在");
            }
            if (!UserStatusEnum.ACTIVE.equals(user.getStatus())) {
                throw new BusinessException("账户已被禁用或锁定，请联系管理员");
            }
            userRole = user.getRole();
            request.setAttribute("role", userRole);
        }

        RoleEnum currentUserRole = RoleEnum.fromCode(userRole);

        boolean hasPermission = Arrays.stream(requireRole.value())
                .anyMatch(currentUserRole::hasPermission);

        if (!hasPermission) {
            throw new BusinessException("权限不足");
        }

        return true;
    }
}
