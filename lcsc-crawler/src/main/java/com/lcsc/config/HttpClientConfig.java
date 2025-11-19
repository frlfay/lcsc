package com.lcsc.config;

import com.lcsc.service.crawler.network.DynamicRateLimiter;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP客户端配置
 * 优化连接池、超时设置和请求拦截器
 * 
 * @author lcsc-crawler
 * @since 2025-09-06
 */
@Configuration
public class HttpClientConfig {
    
    @Value("${lcsc.crawler.http.max-total-connections:200}")
    private int maxTotalConnections;
    
    @Value("${lcsc.crawler.http.max-connections-per-route:20}")
    private int maxConnectionsPerRoute;
    
    @Value("${lcsc.crawler.http.connect-timeout:10000}")
    private int connectTimeout;
    
    @Value("${lcsc.crawler.http.read-timeout:30000}")
    private int readTimeout;
    
    @Value("${lcsc.crawler.http.connection-request-timeout:5000}")
    private int connectionRequestTimeout;
    
    @Value("${lcsc.crawler.http.keep-alive-timeout:60000}")
    private long keepAliveTimeout;
    
    @Value("${lcsc.crawler.http.validate-after-inactivity:30000}")
    private int validateAfterInactivity;
    
    @Value("${lcsc.crawler.proxy.enabled:false}")
    private boolean proxyEnabled;
    
    @Value("${lcsc.crawler.proxy.host:127.0.0.1}")
    private String proxyHost;
    
    @Value("${lcsc.crawler.proxy.port:7890}")
    private int proxyPort;
    
    @Autowired
    private DynamicRateLimiter rateLimiter;
    
    /**
     * 配置HTTP连接管理器
     */
    @Bean
    public PoolingHttpClientConnectionManager connectionManager() throws Exception {
        // SSL配置 - 信任自签名证书（仅开发环境）
        SSLContext sslContext = SSLContextBuilder.create()
            .loadTrustMaterial(TrustSelfSignedStrategy.INSTANCE)
            .build();
            
        SSLConnectionSocketFactory sslConnectionFactory = new SSLConnectionSocketFactory(
            sslContext, NoopHostnameVerifier.INSTANCE);
        
        // Socket配置
        SocketConfig socketConfig = SocketConfig.custom()
            .setSoTimeout(Timeout.ofMilliseconds(readTimeout))
            .setSoKeepAlive(true)
            .setTcpNoDelay(true)
            .setSoReuseAddress(true)
            .build();
        
        // 连接管理器配置
        PoolingHttpClientConnectionManager connectionManager = 
            PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslConnectionFactory)
                .setDefaultSocketConfig(socketConfig)
                .setMaxConnTotal(maxTotalConnections)
                .setMaxConnPerRoute(maxConnectionsPerRoute)
                .setValidateAfterInactivity(TimeValue.ofMilliseconds(validateAfterInactivity))
                .build();
        
        return connectionManager;
    }
    
    /**
     * 配置HTTP请求配置
     */
    @Bean
    public RequestConfig requestConfig() {
        return RequestConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout))
            .setResponseTimeout(Timeout.ofMilliseconds(readTimeout))
            .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout))
            .setRedirectsEnabled(true)
            .setMaxRedirects(3)
            .build();
    }
    
    /**
     * 配置Apache HttpClient
     */
    @Bean
    public CloseableHttpClient httpClient(
            PoolingHttpClientConnectionManager connectionManager,
            RequestConfig requestConfig) {
        
        var httpClientBuilder = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .setKeepAliveStrategy((response, context) -> 
                TimeValue.ofMilliseconds(keepAliveTimeout))
            .disableAuthCaching()
            .disableCookieManagement()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        
        // 配置代理
        if (proxyEnabled) {
            HttpHost proxy = new HttpHost(proxyHost, proxyPort);
            httpClientBuilder.setProxy(proxy);
            System.out.println("HTTP代理已启用: " + proxyHost + ":" + proxyPort);
        } else {
            System.out.println("HTTP代理已禁用");
        }
        
        return httpClientBuilder.build();
    }
    
    /**
     * 配置RestTemplate
     */
    @Bean
    public RestTemplate restTemplate(CloseableHttpClient httpClient) {
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory(httpClient);
        
        factory.setConnectTimeout(connectTimeout);
        factory.setConnectionRequestTimeout(connectionRequestTimeout);
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // 添加拦截器
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new BrowserHeadersInterceptor());
        interceptors.add(new RequestLoggingInterceptor());
        interceptors.add(new RateLimitInterceptor(rateLimiter));
        interceptors.add(new MetricsInterceptor());
        
        restTemplate.setInterceptors(interceptors);
        
        return restTemplate;
    }
    
    /**
     * 浏览器请求头拦截器
     */
    private static class BrowserHeadersInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) 
                throws java.io.IOException {
            
            // 添加完整的浏览器特征请求头
            request.getHeaders().set("Accept", "application/json, text/plain, */*");
            request.getHeaders().set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            request.getHeaders().set("Accept-Encoding", "gzip, deflate, br");
            request.getHeaders().set("Content-Type", "application/json");
            request.getHeaders().set("Origin", "https://www.lcsc.com");
            request.getHeaders().set("Referer", "https://www.lcsc.com/");
            request.getHeaders().set("Connection", "keep-alive");
            request.getHeaders().set("Sec-Fetch-Dest", "empty");
            request.getHeaders().set("Sec-Fetch-Mode", "cors");
            request.getHeaders().set("Sec-Fetch-Site", "same-site");
            request.getHeaders().set("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"");
            request.getHeaders().set("sec-ch-ua-mobile", "?0");
            request.getHeaders().set("sec-ch-ua-platform", "\"Windows\"");
            
            return execution.execute(request, body);
        }
    }
    
    /**
     * 请求日志拦截器
     */
    private static class RequestLoggingInterceptor implements ClientHttpRequestInterceptor {
        private static final org.slf4j.Logger logger = 
            org.slf4j.LoggerFactory.getLogger(RequestLoggingInterceptor.class);
        
        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) 
                throws java.io.IOException {
            
            long startTime = System.currentTimeMillis();
            
            logger.debug("发起HTTP请求: {} {}", request.getMethod(), request.getURI());
            
            try {
                org.springframework.http.client.ClientHttpResponse response = execution.execute(request, body);
                
                long duration = System.currentTimeMillis() - startTime;
                logger.debug("HTTP请求完成: {} {} -> {} 耗时: {}ms", 
                    request.getMethod(), request.getURI(), 
                    response.getStatusCode().value(), duration);
                
                return response;
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                logger.warn("HTTP请求失败: {} {} 耗时: {}ms 错误: {}", 
                    request.getMethod(), request.getURI(), duration, e.getMessage());
                throw e;
            }
        }
    }
    
    /**
     * 频率限制拦截器
     */
    private static class RateLimitInterceptor implements ClientHttpRequestInterceptor {
        private final DynamicRateLimiter rateLimiter;
        
        public RateLimitInterceptor(DynamicRateLimiter rateLimiter) {
            this.rateLimiter = rateLimiter;
        }
        
        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) 
                throws java.io.IOException {
            
            String endpoint = extractEndpoint(request.getURI().getPath());
            
            // 应用频率限制
            rateLimiter.waitForNextRequest(endpoint);
            
            long startTime = System.currentTimeMillis();
            boolean success = false;
            org.springframework.http.HttpStatusCode statusCode = null;
            
            try {
                org.springframework.http.client.ClientHttpResponse response = execution.execute(request, body);
                success = response.getStatusCode().is2xxSuccessful();
                statusCode = response.getStatusCode();
                return response;
                
            } catch (Exception e) {
                success = false;
                if (e instanceof org.springframework.web.client.HttpStatusCodeException) {
                    statusCode = ((org.springframework.web.client.HttpStatusCodeException) e).getStatusCode();
                }
                throw e;
                
            } finally {
                // 调整频率限制
                long responseTime = System.currentTimeMillis() - startTime;
                org.springframework.http.HttpStatus httpStatus = statusCode != null ? 
                    org.springframework.http.HttpStatus.valueOf(statusCode.value()) : null;
                    
                rateLimiter.adjustInterval(endpoint, success, httpStatus, responseTime);
            }
        }
        
        private String extractEndpoint(String path) {
            if (path.contains("/catalog/list")) return "CATALOG_LIST";
            if (path.contains("/query/param/group")) return "QUERY_PARAM_GROUP";
            if (path.contains("/query/list")) return "QUERY_LIST";
            return "OTHER";
        }
    }
    
    /**
     * 指标收集拦截器
     */
    private static class MetricsInterceptor implements ClientHttpRequestInterceptor {
        private static final org.slf4j.Logger logger = 
            org.slf4j.LoggerFactory.getLogger(MetricsInterceptor.class);
        
        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) 
                throws java.io.IOException {
            
            long startTime = System.currentTimeMillis();
            String endpoint = request.getURI().getPath();
            
            try {
                org.springframework.http.client.ClientHttpResponse response = execution.execute(request, body);
                
                long duration = System.currentTimeMillis() - startTime;
                boolean success = response.getStatusCode().is2xxSuccessful();
                
                // TODO: 集成实际的指标收集系统（如Micrometer）
                logger.debug("指标收集: endpoint={}, duration={}ms, success={}, status={}", 
                    endpoint, duration, success, response.getStatusCode().value());
                
                return response;
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                logger.debug("指标收集: endpoint={}, duration={}ms, success=false, error={}", 
                    endpoint, duration, e.getClass().getSimpleName());
                throw e;
            }
        }
    }
}