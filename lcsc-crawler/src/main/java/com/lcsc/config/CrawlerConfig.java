package com.lcsc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 爬虫配置类
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@Configuration
@ConfigurationProperties(prefix = "crawler")
public class CrawlerConfig {

    /**
     * 爬取间隔(毫秒)
     */
    private Long delay = 2000L;

    /**
     * 超时时间(毫秒)
     */
    private Long timeout = 10000L;

    /**
     * User-Agent
     */
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 是否启用无头模式
     */
    private Boolean headless = true;

    /**
     * 最大重试次数
     */
    private Integer maxRetry = 3;

    /**
     * 并发线程数
     */
    private Integer threadPoolSize = 5;

    /**
     * 是否自动重试
     */
    private Boolean autoRetry = true;

    /**
     * 是否保存图片
     */
    private Boolean saveImages = true;

    /**
     * API地址配置
     */
    private Api api = new Api();

    /**
     * 存储配置
     */
    private Storage storage = new Storage();

    /**
     * API地址配置类
     */
    public static class Api {
        private String catalogUrl = "https://wmsc.lcsc.com/ftps/wm/product/catalogs/search";
        private String searchParamGroupUrl = "https://wmsc.lcsc.com/ftps/wm/product/search/param/group";
        private String searchListUrl = "https://wmsc.lcsc.com/ftps/wm/product/search/list";

        // Getters and Setters
        public String getCatalogUrl() { return catalogUrl; }
        public void setCatalogUrl(String catalogUrl) { this.catalogUrl = catalogUrl; }
        public String getSearchParamGroupUrl() { return searchParamGroupUrl; }
        public void setSearchParamGroupUrl(String searchParamGroupUrl) { this.searchParamGroupUrl = searchParamGroupUrl; }
        public String getSearchListUrl() { return searchListUrl; }
        public void setSearchListUrl(String searchListUrl) { this.searchListUrl = searchListUrl; }
    }

    /**
     * 存储配置类
     */
    public static class Storage {
        private String dataDir = "data";
        private String exportDir = "exports";
        private String imageDir = "images";
        private String pdfDir = "pdfs";

        // Getters and Setters
        public String getDataDir() { return dataDir; }
        public void setDataDir(String dataDir) { this.dataDir = dataDir; }
        public String getExportDir() { return exportDir; }
        public void setExportDir(String exportDir) { this.exportDir = exportDir; }
        public String getImageDir() { return imageDir; }
        public void setImageDir(String imageDir) { this.imageDir = imageDir; }
        public String getPdfDir() { return pdfDir; }
        public void setPdfDir(String pdfDir) { this.pdfDir = pdfDir; }
    }

    // Getters and Setters
    public Long getDelay() {
        return delay;
    }

    public void setDelay(Long delay) {
        this.delay = delay;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Boolean getHeadless() {
        return headless;
    }

    public void setHeadless(Boolean headless) {
        this.headless = headless;
    }

    public Integer getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(Integer maxRetry) {
        this.maxRetry = maxRetry;
    }

    public Integer getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(Integer threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public Boolean getAutoRetry() {
        return autoRetry;
    }

    public void setAutoRetry(Boolean autoRetry) {
        this.autoRetry = autoRetry;
    }

    public Boolean getSaveImages() {
        return saveImages;
    }

    public void setSaveImages(Boolean saveImages) {
        this.saveImages = saveImages;
    }

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }
}
