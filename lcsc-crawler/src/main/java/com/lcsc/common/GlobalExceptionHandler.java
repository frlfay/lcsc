package com.lcsc.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 全局异常处理器
 * 确保所有异常都返回统一的Result格式
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleValidationException(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        StringBuilder errorMsg = new StringBuilder("参数验证失败：");
        for (FieldError fieldError : fieldErrors) {
            errorMsg.append(fieldError.getField())
                    .append(" ")
                    .append(fieldError.getDefaultMessage())
                    .append("；");
        }
        String message = errorMsg.toString();
        logger.warn("参数验证异常: {}", message, e);
        return Result.paramError(message);
    }
    
    /**
     * 表单绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleBindException(BindException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        StringBuilder errorMsg = new StringBuilder("参数绑定失败：");
        for (FieldError fieldError : fieldErrors) {
            errorMsg.append(fieldError.getField())
                    .append(" ")
                    .append(fieldError.getDefaultMessage())
                    .append("；");
        }
        String message = errorMsg.toString();
        logger.warn("参数绑定异常: {}", message, e);
        return Result.paramError(message);
    }
    
    /**
     * 参数类型转换异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String message = String.format("参数类型错误：%s 应该是 %s 类型", 
                e.getName(), e.getRequiredType().getSimpleName());
        logger.warn("参数类型转换异常: {}", message, e);
        return Result.paramError(message);
    }
    
    /**
     * 非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleIllegalArgumentException(IllegalArgumentException e) {
        String message = "非法参数：" + e.getMessage();
        logger.warn("非法参数异常: {}", message, e);
        return Result.paramError(message);
    }
    
    /**
     * 业务异常
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> handleRuntimeException(RuntimeException e) {
        String message = "业务处理失败：" + e.getMessage();
        logger.error("业务异常: {}", message, e);
        return Result.error(message);
    }
    
    /**
     * 通用异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> handleException(Exception e) {
        String message = "系统异常：" + e.getMessage();
        logger.error("系统异常: {}", message, e);
        return Result.error(message);
    }
    
    /**
     * 自定义业务异常（如果需要的话）
     */
    public static class BusinessException extends RuntimeException {
        private final Integer code;
        
        public BusinessException(String message) {
            super(message);
            this.code = Result.ERROR_CODE;
        }
        
        public BusinessException(Integer code, String message) {
            super(message);
            this.code = code;
        }
        
        public Integer getCode() {
            return code;
        }
    }
    
    /**
     * 自定义业务异常处理
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> handleBusinessException(BusinessException e) {
        String message = e.getMessage();
        logger.warn("业务异常: {}", message, e);
        return Result.error(e.getCode(), message);
    }
}