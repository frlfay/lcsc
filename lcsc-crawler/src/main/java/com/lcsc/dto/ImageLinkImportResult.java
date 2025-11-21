package com.lcsc.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel导入结果DTO
 */
public class ImageLinkImportResult {
    /**
     * 成功导入的记录数
     */
    private int successCount;

    /**
     * 失败的记录数
     */
    private int failureCount;

    /**
     * 错误列表（包含行号和错误信息）
     */
    private List<ImageLinkImportError> errors;

    public ImageLinkImportResult() {
        this.errors = new ArrayList<>();
    }

    public ImageLinkImportResult(int successCount, int failureCount, List<ImageLinkImportError> errors) {
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public List<ImageLinkImportError> getErrors() {
        return errors;
    }

    public void setErrors(List<ImageLinkImportError> errors) {
        this.errors = errors;
    }

    /**
     * 添加一个错误记录
     */
    public void addError(ImageLinkImportError error) {
        this.errors.add(error);
    }
}
