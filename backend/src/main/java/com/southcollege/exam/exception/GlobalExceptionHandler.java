package com.southcollege.exam.exception;

import com.southcollege.exam.dto.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * 全局异常处理器，统一处理各类异常并返回 Result 响应
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TEXT_EVENT_STREAM = "text/event-stream";

    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(TEXT_EVENT_STREAM);
    }

    private boolean isSseRelatedException(Exception e) {
        String message = e.getMessage();
        if (message != null) {
            return message.contains("text/event-stream") ||
                   message.contains("SSE") ||
                   message.contains("AI调用失败");
        }
        return false;
    }

    private void sendSseErrorEvent(HttpServletRequest request, HttpServletResponse response, int code, String message) {
        try {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(TEXT_EVENT_STREAM + ";charset=UTF-8");
            String sseEvent = "event:error\ndata:{\"code\":" + code + ",\"message\":\"" +
                    message.replace("\"", "\\\"") + "\"}\n\n";
            response.getOutputStream().write(sseEvent.getBytes(StandardCharsets.UTF_8));
            response.getOutputStream().flush();
        } catch (IOException e) {
            log.debug("发送SSE错误事件失败: {}", e.getMessage());
        }
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("业务异常: {}", e.getMessage());
        if (isSseRequest(request)) {
            sendSseErrorEvent(request, response, e.getCode(), e.getMessage());
            return null;
        }
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理权限不足异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("权限不足: {}", e.getMessage());
        if (isSseRequest(request)) {
            sendSseErrorEvent(request, response, 403, "权限不足");
            return null;
        }
        return Result.error(403, "权限不足");
    }

    /**
     * 处理方法参数校验失败异常（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request, HttpServletResponse response) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        if (isSseRequest(request)) {
            sendSseErrorEvent(request, response, 400, message);
            return null;
        }
        return Result.error(400, message);
    }

    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    public Object handleBindException(BindException e, HttpServletRequest request, HttpServletResponse response) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}", message);
        if (isSseRequest(request)) {
            sendSseErrorEvent(request, response, 400, message);
            return null;
        }
        return Result.error(400, message);
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Object handleRuntimeException(RuntimeException e, HttpServletRequest request, HttpServletResponse response) {
        log.error("运行时异常: {}", e.getMessage(), e);
        if (isSseRequest(request) || isSseRelatedException(e)) {
            sendSseErrorEvent(request, response, 500, "系统内部错误，请稍后重试");
            return null;
        }
        return Result.error(500, "系统内部错误，请稍后重试");
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Object handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("参数类型不匹配: 参数名={}, 值={}, 期望类型={}",
                e.getName(), e.getValue(), e.getRequiredType());
        if (isSseRequest(request)) {
            String message = "参数 '" + e.getName() + "' 的值 '" + e.getValue() + "' 格式不正确";
            sendSseErrorEvent(request, response, 400, message);
            return null;
        }
        String message = "参数 '" + e.getName() + "' 的值 '" + e.getValue() + "' 格式不正确";
        return Result.error(400, message);
    }

    /**
     * 处理请求体不可读异常（JSON 格式错误等）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handleHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("请求体解析失败: {}", e.getMessage());
        if (isSseRequest(request)) {
            sendSseErrorEvent(request, response, 400, "请求数据格式错误");
            return null;
        }
        return Result.error(400, "请求数据格式错误，请检查请求体是否为有效的JSON格式");
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingServletRequestParameterException(MissingServletRequestParameterException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        if (isSseRequest(request)) {
            sendSseErrorEvent(request, response, 400, "缺少必要参数: " + e.getParameterName());
            return null;
        }
        return Result.error(400, "缺少必要参数: " + e.getParameterName());
    }

    /**
     * 处理 404 接口不存在异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Object handleNoHandlerFoundException(NoHandlerFoundException e, HttpServletRequest request) {
        log.warn("请求路径不存在: {} {}", request.getMethod(), request.getRequestURL());
        return Result.error(404, "接口不存在: " + request.getMethod() + " " + request.getRequestURL());
    }

    /**
     * 处理数据库唯一约束冲突异常
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Object handleDuplicateKeyException(DuplicateKeyException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("数据唯一约束冲突: {}", e.getMessage());
        if (isSseRequest(request)) {
            sendSseErrorEvent(request, response, 409, "数据已存在，请勿重复操作");
            return null;
        }
        return Result.error(409, "数据已存在，请勿重复操作");
    }

    /**
     * 处理乐观锁冲突异常
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public Object handleOptimisticLockingFailureException(OptimisticLockingFailureException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("乐观锁冲突: {}", e.getMessage());
        if (isSseRequest(request)) {
            sendSseErrorEvent(request, response, 409, "数据已被他人修改，请刷新后重试");
            return null;
        }
        return Result.error(409, "数据已被他人修改，请刷新后重试");
    }

    /**
     * 处理其他未捕获的通用异常
     */
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        log.error("系统异常: {}", e.getMessage(), e);
        if (isSseRequest(request) || isSseRelatedException(e)) {
            sendSseErrorEvent(request, response, 500, "系统错误，请联系管理员");
            return null;
        }
        return Result.error(500, "系统错误，请联系管理员");
    }
}
