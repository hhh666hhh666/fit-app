package com.example.fitapp.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("business error: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::describe)
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.BAD_REQUEST, message);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public Result<Void> handleBadArgument(Exception e) {
        return Result.fail(ResultCode.BAD_REQUEST, "请求参数不正确：" + e.getMessage());
    }

    /**
     * 基础设施瞬时故障：与 quizzy 共用的 MySQL 容器被内核 OOM-killer 杀掉重启（ADR 0005）、
     * 或池子里的连接整体失效时，写请求就会抛这一族异常。给一句能指导行动的话
     * （「重按一次」），而不是含糊的「系统繁忙」——用户看不懂那句话，也不知道该做什么。
     *
     * <p>写路径上的这类失败先经过 {@link TransientRetry} 自动重试；能走到这里，
     * 说明重试也没扛过去。响应仍是 HTTP 200 + 业务码，与其他错误一致。</p>
     *
     * <p>⚠️ 三个类型都要列：{@code RecoverableDataAccessException} 是 {@code DataAccessException}
     * 的直接子类、不在 {@code TransientDataAccessException} 之下，而它正是线上真正的形态。</p>
     */
    @ExceptionHandler({
            TransientDataAccessException.class,
            RecoverableDataAccessException.class,
            DataAccessResourceFailureException.class
    })
    public Result<Void> handleTransientDatabaseFailure(DataAccessException e) {
        log.warn("transient database failure: {}", e.getMessage());
        return Result.fail(ResultCode.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception e) {
        log.error("unexpected error", e);
        return Result.fail(ResultCode.SYSTEM_ERROR, ResultCode.SYSTEM_ERROR.getMessage());
    }

    private String describe(FieldError fieldError) {
        return fieldError.getField() + " " + fieldError.getDefaultMessage();
    }
}
