package com.lcsc.dto;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 文件下载任务的数据传输对象 (DTO).
 * 封装一次下载任务所需的所有信息，可被序列化并存入Redis.
 *
 * @author Gemini-assisted
 * @since 2025-10-08
 */
public class DownloadTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务的唯一ID，用于追踪.
     */
    private String taskId;

    /**
     * 关联的产品编号.
     */
    private String productCode;

    /**
     * 要下载的资源URL.
     */
    private String url;

    /**
     * 期望保存的文件名.
     */
    private String targetFilename;

    /**
     * 下载文件的类型.
     */
    private DownloadType type;

    /**
     * 当前重试次数.
     */
    private int retryCount;

    /**
     * 下载文件类型的枚举.
     */
    public enum DownloadType {
        IMAGE_THUMBNAIL,
        IMAGE_MAIN,
        IMAGE_GALLERY,
        PDF
    }

    // 构造函数
    public DownloadTask() {
        this.taskId = UUID.randomUUID().toString();
        this.retryCount = 0;
    }

    public DownloadTask(String productCode, String url, String targetFilename, DownloadType type) {
        this();
        this.productCode = productCode;
        this.url = url;
        this.targetFilename = targetFilename;
        this.type = type;
    }

    // Getters and Setters
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTargetFilename() {
        return targetFilename;
    }

    public void setTargetFilename(String targetFilename) {
        this.targetFilename = targetFilename;
    }

    public DownloadType getType() {
        return type;
    }

    public void setType(DownloadType type) {
        this.type = type;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    @Override
    public String toString() {
        return "DownloadTask{" +
                "taskId='" + taskId + '\'' +
                ", productCode='" + productCode + '\'' +
                ", url='" + url + '\'' +
                ", targetFilename='" + targetFilename + '\'' +
                ", type=" + type +
                ", retryCount=" + retryCount +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DownloadTask that = (DownloadTask) o;
        return taskId.equals(that.taskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId);
    }
}
