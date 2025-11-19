package com.lcsc.service.crawler.network;

import com.lcsc.config.CrawlerConfig;
import com.lcsc.config.HttpDebugConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpStatusCodeException;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP客户端服务
 * 负责所有网络请求的发送，包含重试机制和限流控制
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@Service
public class HttpClientService {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientService.class);

    @Autowired
    private CrawlerConfig crawlerConfig;

    @Autowired
    private RetryService retryService;

    @Autowired
    private HttpDebugConfig debugConfig;

    private RestTemplate restTemplate;
    private HttpHeaders defaultHeaders;
    private volatile LocalDateTime lastRequestTime = LocalDateTime.now();

    @PostConstruct
    public void init() {
        this.restTemplate = createRestTemplate();
        this.defaultHeaders = createDefaultHeaders();
        logger.info("HTTP客户端服务初始化完成");
    }

    /**
     * 创建配置好的RestTemplate，支持gzip解压
     */
    private RestTemplate createRestTemplate() {
        // 创建支持gzip解压的请求工厂
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10秒连接超时
        factory.setReadTimeout(30000);    // 30秒读取超时
        
        RestTemplate template = new RestTemplate(factory);
        
        // 添加字符串消息转换器以处理不同编码
        template.getMessageConverters().add(0, new org.springframework.http.converter.StringHttpMessageConverter(java.nio.charset.StandardCharsets.UTF_8));
        
        // RestTemplate默认已经包含了支持gzip的消息转换器
        // 设置Accept-Encoding头以支持gzip, deflate, br
        logger.info("RestTemplate已配置支持gzip解压缩和UTF-8编码");
        
        return template;
    }

    /**
     * 创建默认请求头
     */
    private HttpHeaders createDefaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", crawlerConfig.getUserAgent());
        headers.set("Referer", "https://www.lcsc.com/");
        headers.set("Origin", "https://www.lcsc.com");
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json, text/plain, */*");
        headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.set("Accept-Encoding", "gzip, deflate, br");
        headers.set("Connection", "keep-alive");
        headers.set("Cache-Control", "no-cache");
        headers.set("Pragma", "no-cache");
        headers.set("Sec-Fetch-Dest", "empty");
        headers.set("Sec-Fetch-Mode", "cors");
        headers.set("Sec-Fetch-Site", "same-site");
        headers.set("X-Requested-With", "XMLHttpRequest");
        return headers;
    }

    /**
     * 发送GET请求
     * 
     * @param url 请求URL
     * @return 响应数据
     */
    public Map<String, Object> get(String url) {
        return get(url, null);
    }

    /**
     * 发送GET请求
     * 
     * @param url 请求URL
     * @param params 请求参数
     * @return 响应数据
     */
    public Map<String, Object> get(String url, Map<String, Object> params) {
        return retryService.executeWithRetry(() -> {
            // 限流控制
            applyRateLimit();
            
            HttpEntity<Void> entity = new HttpEntity<>(defaultHeaders);
            
            try {
                logger.debug("发送GET请求: {}", url);
                ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    logger.debug("GET请求成功: {}", url);
                    try {
                        String responseBody = response.getBody();

                        // 打印调试信息（如果启用）
                        if (debugConfig.isEnabled() && responseBody != null) {
                            logger.info("=== HTTP GET RESPONSE DEBUG INFO ===");
                            logger.info("URL: {}", url);
                            logger.info("Response Length: {} characters", responseBody.length());

                            if (debugConfig.isPrintFullResponse()) {
                                // 截取响应内容到配置的最大长度
                                String printBody = responseBody.length() > debugConfig.getMaxResponseLength() ?
                                    responseBody.substring(0, debugConfig.getMaxResponseLength()) + "...(truncated)" :
                                    responseBody;
                                logger.info("Full Response Body:\n{}", printBody);
                            }
                            logger.info("=== END RESPONSE DEBUG INFO ===");
                        }

                        if (responseBody != null) {
                            // 清理非法控制字符
                            String cleanedBody = cleanJsonString(responseBody);
                            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(cleanedBody, Map.class);
                        } else {
                            if (debugConfig.isEnabled()) {
                                logger.warn("Response body is null for URL: {}", url);
                            }
                        }
                        return new HashMap<>();
                    } catch (Exception e) {
                        logger.error("解析JSON响应失败: {}", url, e);
                        // 记录原始响应内容（截断到前500个字符以避免日志过长）
                        String bodyPreview = response.getBody() != null ?
                            response.getBody().substring(0, Math.min(500, response.getBody().length())) : "null";
                        logger.debug("原始响应内容预览: {}", bodyPreview);
                        throw new RuntimeException("解析JSON响应失败", e);
                    }
                } else {
                    logger.warn("GET请求失败: {}, 状态码: {}", url, response.getStatusCode());
                    throw new HttpStatusCodeException(response.getStatusCode()) {};
                }
            } catch (ResourceAccessException e) {
                logger.error("GET请求网络异常: {}", url, e);
                throw new NetworkException("网络请求超时: " + url, e);
            }
        }, "GET " + url);
    }

    /**
     * 发送POST请求
     * 
     * @param url 请求URL
     * @param requestData 请求数据
     * @return 响应数据
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> post(String url, Object requestData) {
        return retryService.executeWithRetry(() -> {
            // 限流控制
            applyRateLimit();
            
            HttpEntity<Object> entity = new HttpEntity<>(requestData, defaultHeaders);
            
            try {
                logger.debug("发送POST请求: {}", url);
                ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    logger.debug("POST请求成功: {}", url);
                    try {
                        String responseBody = response.getBody();

                        // 打印调试信息（如果启用）
                        if (debugConfig.isEnabled() && responseBody != null) {
                            logger.info("=== HTTP POST RESPONSE DEBUG INFO ===");
                            logger.info("URL: {}", url);
                            logger.info("Request Body: {}", requestData);
                            logger.info("Response Length: {} characters", responseBody.length());

                            if (debugConfig.isPrintFullResponse()) {
                                // 截取响应内容到配置的最大长度
                                String printBody = responseBody.length() > debugConfig.getMaxResponseLength() ?
                                    responseBody.substring(0, debugConfig.getMaxResponseLength()) + "...(truncated)" :
                                    responseBody;
                                logger.info("Full Response Body:\n{}", printBody);
                            }
                            logger.info("=== END RESPONSE DEBUG INFO ===");
                        }

                        if (responseBody != null) {
                            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(responseBody, Map.class);
                        } else {
                            if (debugConfig.isEnabled()) {
                                logger.warn("Response body is null for URL: {}", url);
                            }
                        }
                        return new HashMap<>();
                    } catch (Exception e) {
                        logger.error("解析JSON响应失败: {}", url, e);
                        throw new RuntimeException("解析JSON响应失败", e);
                    }
                } else {
                    logger.warn("POST请求失败: {}, 状态码: {}", url, response.getStatusCode());
                    throw new HttpStatusCodeException(response.getStatusCode()) {};
                }
            } catch (ResourceAccessException e) {
                logger.error("POST请求网络异常: {}", url, e);
                throw new NetworkException("网络请求超时: " + url, e);
            }
        }, "POST " + url);
    }

    /**
     * 应用限流控制
     */
    private void applyRateLimit() {
        try {
            LocalDateTime now = LocalDateTime.now();
            long timeSinceLastRequest = java.time.Duration.between(lastRequestTime, now).toMillis();
            
            if (timeSinceLastRequest < crawlerConfig.getDelay()) {
                long sleepTime = crawlerConfig.getDelay() - timeSinceLastRequest;
                logger.debug("限流等待: {}ms", sleepTime);
                Thread.sleep(sleepTime);
            }
            
            lastRequestTime = LocalDateTime.now();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("限流等待被中断", e);
        }
    }

    /**
     * 清理JSON字符串中的非法控制字符
     * 移除ASCII控制字符（0-31，除了合法的空白字符9,10,13）
     */
    private String cleanJsonString(String jsonString) {
        if (jsonString == null) {
            return null;
        }
        
        // 移除非法控制字符，保留合法的制表符(9)、换行符(10)、回车符(13)
        StringBuilder cleaned = new StringBuilder();
        for (char c : jsonString.toCharArray()) {
            if (c >= 32 || c == 9 || c == 10 || c == 13) {
                cleaned.append(c);
            } else {
                // 跳过非法控制字符
                logger.trace("移除非法控制字符: {}", (int)c);
            }
        }
        
        return cleaned.toString();
    }

    /**
     * 网络异常类
     */
    public static class NetworkException extends RuntimeException {
        public NetworkException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
