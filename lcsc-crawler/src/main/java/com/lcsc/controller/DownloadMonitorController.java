package com.lcsc.controller;

import com.lcsc.common.Result;
import com.lcsc.service.crawler.FileDownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 下载服务监控器
 *
 * @author lcsc-crawler
 * @since 2025-10-09
 */
@RestController
@RequestMapping("/api/downloads")
public class DownloadMonitorController {

    @Autowired
    private FileDownloadService fileDownloadService;

    /**
     * 获取下载服务的实时状态
     * @return 包含队列和统计信息的Map
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        return Result.success(fileDownloadService.getStatus());
    }
}
