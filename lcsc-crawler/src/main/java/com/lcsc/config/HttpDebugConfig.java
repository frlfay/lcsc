package com.lcsc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * HTTP调试配置
 * 用于控制HTTP请求和响应的详细日志输出
 *
 * @author lcsc-crawler
 */
@Component
@ConfigurationProperties(prefix = "crawler.http.debug")
public class HttpDebugConfig {

    /**
     * 是否启用HTTP调试日志
     */
    private boolean enabled = true;

    /**
     * 是否打印完整的响应体
     */
    private boolean printFullResponse = true;

    /**
     * 是否打印请求头信息
     */
    private boolean printHeaders = false;

    /**
     * 响应体最大打印长度（字符数）
     */
    private int maxResponseLength = 10000;

    /**
     * 是否打印API调用的详细信息
     */
    private boolean printApiCallDetails = true;

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPrintFullResponse() {
        return printFullResponse;
    }

    public void setPrintFullResponse(boolean printFullResponse) {
        this.printFullResponse = printFullResponse;
    }

    public boolean isPrintHeaders() {
        return printHeaders;
    }

    public void setPrintHeaders(boolean printHeaders) {
        this.printHeaders = printHeaders;
    }

    public int getMaxResponseLength() {
        return maxResponseLength;
    }

    public void setMaxResponseLength(int maxResponseLength) {
        this.maxResponseLength = maxResponseLength;
    }

    public boolean isPrintApiCallDetails() {
        return printApiCallDetails;
    }

    public void setPrintApiCallDetails(boolean printApiCallDetails) {
        this.printApiCallDetails = printApiCallDetails;
    }
}