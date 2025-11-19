package com.lcsc.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一返回结果类
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
public class Result<T> {
    
    // 状态码常量
    public static final Integer SUCCESS_CODE = 200;
    public static final Integer ERROR_CODE = 500;
    public static final Integer PARAM_ERROR_CODE = 400;
    public static final Integer NOT_FOUND_CODE = 404;
    public static final Integer FORBIDDEN_CODE = 403;
    
    private Integer code;
    private String message;
    private T data;
    
    public Result() {}
    
    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    // ========== 成功响应方法 ==========
    
    public static <T> Result<T> success() {
        return new Result<>(SUCCESS_CODE, "操作成功", null);
    }
    
    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, "操作成功", data);
    }
    
    public static <T> Result<T> success(String message) {
        return new Result<>(SUCCESS_CODE, message, null);
    }
    
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(SUCCESS_CODE, message, data);
    }
    
    // ========== 分页结果便捷方法 ==========
    
    public static <T> Result<Map<String, Object>> page(List<T> records, long total, long current, long size) {
        Map<String, Object> pageData = new HashMap<>();
        pageData.put("records", records);
        pageData.put("total", total);
        pageData.put("current", current);
        pageData.put("size", size);
        pageData.put("pages", (total + size - 1) / size);
        return new Result<>(SUCCESS_CODE, "查询成功", pageData);
    }
    
    public static <T> Result<Map<String, Object>> page(List<T> records, long total) {
        return page(records, total, 1L, (long) records.size());
    }
    
    // ========== 错误响应方法 ==========
    
    public static <T> Result<T> error(String message) {
        return new Result<>(ERROR_CODE, message, null);
    }
    
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
    
    public static <T> Result<T> paramError(String message) {
        return new Result<>(PARAM_ERROR_CODE, message, null);
    }
    
    public static <T> Result<T> notFound(String message) {
        return new Result<>(NOT_FOUND_CODE, message, null);
    }
    
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(FORBIDDEN_CODE, message, null);
    }
    
    // ========== Getter和Setter方法 ==========
    
    public Integer getCode() {
        return code;
    }
    
    public void setCode(Integer code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    // ========== 工具方法 ==========
    
    public boolean isSuccess() {
        return SUCCESS_CODE.equals(this.code);
    }
    
    public boolean isError() {
        return !isSuccess();
    }
    
    @Override
    public String toString() {
        return "Result{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}
