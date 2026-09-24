package com.hjgd.plm.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常 → HTTP 响应映射。错误契约(前端与 EBMS/ERP 集成方统一按此实现):
 *
 * <p>1. <b>业务错误一律返回 HTTP 200</b>, 语义状态只放在 body.code。客户端必须读
 * body.code 判定成败, 不得只看 HTTP 状态码 —— 例: {@code ResultCode.NOT_FOUND}(404)、
 * {@code PARAM_ERROR}(400) 与域内业务码(如 10001 料号重复)都只是 body.code,
 * HTTP 状态码保持 200。集成接口同样直接返回 {@code Result.failed(<code>, msg)}
 * (见 IntegrationV1Controller)。
 *
 * <p>2. <b>只有认证/授权失败例外</b>, 返回 HTTP 401 / 403: 这两个状态码用于网关与
 * 前端 axios 拦截器识别「登录态失效」(见 plm-frontend/src/utils/request.js 的 401 分支)。
 *
 * <p>为何不把语义码映射成 HTTP 状态码: 前端统一拦截器、dataio.js / progress.js 的 blob
 * 下载(在 HTTP 200 上解析 JSON 错误体取 message)以及外部集成方都依赖上述契约,
 * 改状态码会静默改变这些调用方的错误分支, 属于跨端契约变更, 需与前端/集成方同步发版。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** HTTP 200 + body.code —— 见类注释「错误契约」第 1 条 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.failed(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.failed(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.failed(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        return Result.failed(ResultCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthentication(AuthenticationException e) {
        return Result.failed(ResultCode.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.failed(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.failed("系统异常,请联系管理员");
    }
}
