package com.lcsc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.lcsc.config.CrawlerConfig;

/**
 * 立创商城爬虫系统启动类
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CrawlerConfig.class)
@MapperScan("com.lcsc.mapper")
public class LcscApplication {

    public static void main(String[] args) {
        SpringApplication.run(LcscApplication.class, args);
        System.out.println("立创商城爬虫系统启动成功！");
        System.out.println("访问地址：http://localhost:8080");
    }
}
