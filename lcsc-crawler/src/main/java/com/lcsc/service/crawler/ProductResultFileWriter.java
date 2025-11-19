package com.lcsc.service.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 将完整的API结果直接保存到独立的JSON文件中。
 * 每次API调用的result对象将被完整保存为单独的文件。
 */
@Service
public class ProductResultFileWriter {

    private static final Logger logger = LoggerFactory.getLogger(ProductResultFileWriter.class);

    private final ObjectMapper objectMapper;
    private final Lock writeLock = new ReentrantLock();

    @Value("${crawler.product-result.enabled:true}")
    private boolean exportEnabled;

    @Value("${crawler.product-result.path:data/results}")
    private String exportBasePath;

    @Value("${crawler.storage.base-path:}")
    private String storageBasePath;

    private Path resultDirectoryPath;

    public ProductResultFileWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initializePath() {
        try {
            this.resultDirectoryPath = resolveResultDirectoryPath();
            if (resultDirectoryPath != null) {
                Files.createDirectories(resultDirectoryPath);
                logger.info("产品结果文件导出目录已初始化: {}", resultDirectoryPath);
            }
        } catch (IOException e) {
            exportEnabled = false;
            logger.error("初始化产品结果文件导出目录失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 将完整的API结果保存到独立文件
     *
     * @param result 完整的API result对象
     * @param categoryLevel1Id 一级分类ID
     * @param categoryLevel2Id 二级分类ID
     * @param currentPage 当前页码
     * @return 保存的文件路径，如果保存失败则返回null
     */
    public Path saveResultToFile(Object result, Integer categoryLevel1Id, Integer categoryLevel2Id, Integer currentPage) {
        if (!exportEnabled) {
            logger.debug("产品结果文件导出已禁用，跳过保存");
            return null;
        }

        if (resultDirectoryPath == null) {
            logger.warn("产品结果文件导出目录未初始化，跳过保存");
            return null;
        }

        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String fileName = generateFileName(categoryLevel1Id, categoryLevel2Id, currentPage, timestamp);
        Path filePath = resultDirectoryPath.resolve(fileName);

        writeLock.lock();
        try {
            String jsonContent;
            if (result instanceof String) {
                // 如果已经是JSON字符串，直接使用
                jsonContent = (String) result;
            } else if (result instanceof JsonNode) {
                // 如果是JsonNode，转换为格式化的JSON字符串
                jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            } else {
                // 其他对象，转换为JSON
                jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            }

            // 添加元数据
            String contentWithMetadata = addMetadata(jsonContent, categoryLevel1Id, categoryLevel2Id, currentPage);

            try (BufferedWriter writer = Files.newBufferedWriter(
                    filePath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {

                writer.write(contentWithMetadata);
                writer.newLine();

                logger.info("产品结果已保存到文件: {} (大小: {} 字节)", filePath, Files.size(filePath));
                return filePath;
            }

        } catch (IOException e) {
            logger.error("保存产品结果文件失败: {}", e.getMessage(), e);
            return null;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 生成文件名
     */
    private String generateFileName(Integer categoryLevel1Id, Integer categoryLevel2Id, Integer currentPage, String timestamp) {
        StringBuilder fileName = new StringBuilder();

        if (categoryLevel1Id != null) {
            fileName.append("cat1-").append(categoryLevel1Id);
        }
        if (categoryLevel2Id != null) {
            fileName.append("-cat2-").append(categoryLevel2Id);
        }
        if (currentPage != null) {
            fileName.append("-page-").append(currentPage);
        }
        fileName.append("-").append(timestamp).append(".json");

        return fileName.toString();
    }

    /**
     * 添加元数据到JSON内容
     */
    private String addMetadata(String jsonContent, Integer categoryLevel1Id, Integer categoryLevel2Id, Integer currentPage) {
        StringBuilder metadata = new StringBuilder();
        metadata.append("{\n");
        metadata.append("  \"metadata\": {\n");
        metadata.append("    \"savedAt\": \"").append(OffsetDateTime.now().toString()).append("\",\n");
        metadata.append("    \"categoryLevel1Id\": ").append(categoryLevel1Id != null ? categoryLevel1Id : "null").append(",\n");
        metadata.append("    \"categoryLevel2Id\": ").append(categoryLevel2Id != null ? categoryLevel2Id : "null").append(",\n");
        metadata.append("    \"currentPage\": ").append(currentPage != null ? currentPage : "null").append("\n");
        metadata.append("  },\n");
        metadata.append("  \"result\": ");

        // 如果原始内容已经是JSON对象，我们需要移除外层的大括号并将内容嵌入
        String trimmedContent = jsonContent.trim();
        if (trimmedContent.startsWith("{")) {
            trimmedContent = trimmedContent.substring(1);
            if (trimmedContent.endsWith("}")) {
                trimmedContent = trimmedContent.substring(0, trimmedContent.length() - 1);
            }
            // 移除开头可能的换行符
            if (trimmedContent.startsWith("\n")) {
                trimmedContent = trimmedContent.substring(1);
            }
        }

        return metadata.append(trimmedContent).toString();
    }

    /**
     * 解析结果目录路径
     */
    private Path resolveResultDirectoryPath() {
        Path configuredPath = null;
        if (exportBasePath != null && !exportBasePath.isBlank()) {
            configuredPath = Paths.get(exportBasePath);
        }

        if (configuredPath != null && configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }

        Path basePath = null;
        if (storageBasePath != null && !storageBasePath.isBlank()) {
            basePath = Paths.get(storageBasePath);
        }

        if (basePath == null) {
            basePath = Paths.get(System.getProperty("user.dir"), "data");
        }

        if (configuredPath == null || configuredPath.toString().isBlank()) {
            configuredPath = Paths.get("results");
        }

        return basePath.resolve(configuredPath).normalize();
    }

    /**
     * 检查导出是否启用
     */
    public boolean isEnabled() {
        return exportEnabled;
    }

    /**
     * 获取结果目录路径
     */
    public Path getResultDirectoryPath() {
        return resultDirectoryPath;
    }
}