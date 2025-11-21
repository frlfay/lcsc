package com.lcsc.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel导入错误DTO
 */
public class ImageLinkImportError {
    /**
     * 行号（从1开始，不包含标题行）
     */
    private int rowNumber;

    /**
     * 错误信息列表
     */
    private List<String> errors;

    public ImageLinkImportError() {
        this.errors = new ArrayList<>();
    }

    public ImageLinkImportError(int rowNumber) {
        this.rowNumber = rowNumber;
        this.errors = new ArrayList<>();
    }

    public ImageLinkImportError(int rowNumber, List<String> errors) {
        this.rowNumber = rowNumber;
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    /**
     * 添加一个错误信息
     */
    public void addError(String error) {
        this.errors.add(error);
    }

    /**
     * 是否有错误
     */
    public boolean hasErrors() {
        return !this.errors.isEmpty();
    }
}
