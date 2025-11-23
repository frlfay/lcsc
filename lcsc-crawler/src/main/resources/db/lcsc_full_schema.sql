-- MySQL dump 10.13  Distrib 9.4.0, for macos15.4 (arm64)
--
-- Host: 127.0.0.1    Database: lcsc
-- ------------------------------------------------------
-- Server version	8.0.43

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `category_level1_codes`
--

DROP TABLE IF EXISTS `category_level1_codes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category_level1_codes` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'ä¸€çº§åˆ†ç±»ID',
  `category_level1_name` varchar(100) NOT NULL DEFAULT '未命名分类' COMMENT 'ä¸€çº§åˆ†ç±»åç§°ï¼ˆç¨‹åºç”Ÿæˆï¼‰',
  `catalog_id` varchar(50) NOT NULL DEFAULT '' COMMENT '立创API返回的catalogId',
  `category_code` varchar(50) DEFAULT NULL COMMENT 'åˆ†ç±»ç ï¼ˆæ•°å­—ä¸²ï¼Œå‰ç«¯ç¼–è¾‘å½•å…¥ï¼‰',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `category_level1_name` (`category_level1_name`)
) ENGINE=InnoDB AUTO_INCREMENT=106 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ä¸€çº§åˆ†ç±»åˆ†ç±»ç è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `category_level2_codes`
--

DROP TABLE IF EXISTS `category_level2_codes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category_level2_codes` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'äºŒçº§åˆ†ç±»ID',
  `category_level2_name` varchar(100) NOT NULL DEFAULT '未命名二级分类' COMMENT 'äºŒçº§åˆ†ç±»åç§°ï¼ˆç¨‹åºç”Ÿæˆï¼‰',
  `catalog_id` varchar(50) NOT NULL DEFAULT '' COMMENT '立创API返回的catalogId',
  `category_level1_id` int NOT NULL COMMENT 'æ‰€å±žä¸€çº§åˆ†ç±»IDï¼ˆå¤–é”®ï¼‰',
  `shop_category_codes` json DEFAULT NULL COMMENT 'å„åº—é“ºåˆ†ç±»ç ï¼ˆJSONæ ¼å¼ï¼š{shop_id: category_number_code}ï¼‰ï¼Œå‰ç«¯å¯¼å…¥CVSæ ¼å¼',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `crawl_status` varchar(20) DEFAULT 'NOT_STARTED' COMMENT '爬取状态：NOT_STARTED, IN_QUEUE, PROCESSING, COMPLETED, FAILED',
  `crawl_progress` int DEFAULT '0' COMMENT '爬取进度 0-100',
  `last_crawl_time` datetime DEFAULT NULL COMMENT '最后爬取时间',
  `total_products` int DEFAULT '0' COMMENT '该分类下产品总数',
  `crawled_products` int DEFAULT '0' COMMENT '已爬取产品数',
  `current_page` int DEFAULT '0' COMMENT '当前爬取页码',
  `error_message` text COMMENT '错误信息',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_category_combo` (`category_level2_name`,`category_level1_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1879 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='äºŒçº§åˆ†ç±»åˆ†ç±»ç è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crawler_config`
--

DROP TABLE IF EXISTS `crawler_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crawler_config` (
  `id` int NOT NULL AUTO_INCREMENT,
  `config_key` varchar(50) NOT NULL COMMENT '配置键',
  `config_value` varchar(200) DEFAULT NULL COMMENT '配置值',
  `description` varchar(200) DEFAULT NULL COMMENT '描述',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='爬虫配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crawler_tasks`
--

DROP TABLE IF EXISTS `crawler_tasks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crawler_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(100) NOT NULL,
  `task_type` varchar(50) NOT NULL,
  `task_status` varchar(20) NOT NULL,
  `task_params` json DEFAULT NULL COMMENT '任务参数',
  `task_result` json DEFAULT NULL COMMENT '任务结果',
  `priority` int DEFAULT '1' COMMENT '优先级',
  `retry_count` int DEFAULT '0' COMMENT '重试次数',
  `error_message` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `execution_duration_ms` bigint DEFAULT NULL COMMENT '执行时长（毫秒）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `task_id` (`task_id`),
  KEY `idx_task_status` (`task_status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `image_links`
--

DROP TABLE IF EXISTS `image_links`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `image_links` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'é“¾æŽ¥ID',
  `image_name` varchar(100) NOT NULL COMMENT 'å›¾ç‰‡åç§°ï¼ˆC********_****.jpgæ ¼å¼ï¼‰',
  `shop_id` int NOT NULL COMMENT 'åº—é“ºIDï¼ˆå¤–é”®ï¼‰',
  `image_link` varchar(1000) NOT NULL COMMENT 'å›¾ç‰‡é“¾æŽ¥URL',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_image_shop` (`image_name`,`shop_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å›¾ç‰‡é“¾æŽ¥è¡¨ï¼ˆæ¯ä¸ªåº—é“ºç‹¬ç«‹é“¾æŽ¥ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `products`
--

DROP TABLE IF EXISTS `products`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `products` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'äº§å“ID',
  `product_code` varchar(20) NOT NULL COMMENT 'äº§å“ç¼–å·ï¼ˆC********æ ¼å¼ï¼‰',
  `category_level1_id` int NOT NULL COMMENT 'æ‰€å±žä¸€çº§åˆ†ç±»IDï¼ˆå¤–é”®ï¼‰',
  `category_level2_id` int DEFAULT NULL,
  `brand` varchar(200) DEFAULT NULL COMMENT 'æ‰€å±žå“ç‰Œï¼ˆä¸­æ–‡ï¼Œ&æ›¿æ¢ä¸ºç©ºæ ¼ï¼‰',
  `model` varchar(200) DEFAULT NULL COMMENT 'åž‹å·ï¼ˆä¸æ”¹å˜ä»»ä½•å­—ç¬¦ï¼‰',
  `package_name` varchar(100) DEFAULT NULL COMMENT 'å°è£…åç§°ï¼ˆ-åˆ é™¤ä¸ºç©ºï¼‰',
  `pdf_filename` varchar(500) DEFAULT NULL COMMENT 'PDFæ–‡ä»¶åï¼ˆäº§å“ç¼–å·_å“ç‰Œ_åž‹å·æ ¼å¼ï¼Œ-åˆ é™¤ä¸ºç©ºï¼‰',
  `pdf_local_path` varchar(1000) DEFAULT NULL COMMENT 'PDFæ–‡ä»¶æœ¬åœ°è·¯å¾„',
  `image_name` varchar(512) DEFAULT NULL,
  `image_local_path` varchar(1000) DEFAULT NULL COMMENT 'å›¾ç‰‡æ–‡ä»¶æœ¬åœ°è·¯å¾„',
  `total_stock_quantity` int DEFAULT '0' COMMENT 'æ€»åº“å­˜æ•°é‡',
  `brief_description` varchar(200) DEFAULT NULL COMMENT 'ç®€ä»‹ï¼ˆåž‹å·+å°è£…+äºŒçº§åˆ†ç±»+ä¸€çº§åˆ†ç±»ï¼Œ60å­—èŠ‚é™åˆ¶ï¼‰',
  `tier_prices` json DEFAULT NULL COMMENT 'é˜¶æ¢¯æ•°é‡åŠä»·æ ¼ï¼ˆæœ€å¤š6é˜¶ï¼ŒåŒ…å«æ—¥æœŸè®°å½•ï¼‰',
  `tier_prices_last_update` date DEFAULT NULL COMMENT 'é˜¶æ¢¯ä»·æ ¼æœ€åŽæ›´æ–°æ—¥æœŸï¼ˆ180å¤©å†…ä¸æ›´æ–°ï¼‰',
  `tier_prices_manual_edit` tinyint(1) DEFAULT '0' COMMENT 'æ˜¯å¦äººå·¥ç¼–è¾‘è¿‡',
  `detailed_parameters` json DEFAULT NULL COMMENT 'è¯¦ç»†å‚æ•°ï¼ˆæ‰€æœ‰ä¸­æ–‡å­—æ®µï¼Œ-åˆ é™¤ä¸ºç©ºï¼‰',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `last_crawled_at` timestamp NULL DEFAULT NULL COMMENT 'æœ€åŽçˆ¬å–æ—¶é—´',
  `product_images_info` json DEFAULT NULL COMMENT 'äº§å“å›¾ç‰‡ä¿¡æ¯JSONæ•°ç»„',
  `main_image_local_path` varchar(500) DEFAULT NULL COMMENT 'ä¸»å›¾æœ¬åœ°è·¯å¾„',
  `category_level1_name` varchar(100) DEFAULT NULL COMMENT 'ä¸€çº§åˆ†ç±»åç§°ï¼ˆå¯¹åº”CSVä¸­çš„"ä¸€çº§åˆ†ç±»"ï¼‰',
  `category_level2_name` varchar(100) DEFAULT NULL COMMENT 'äºŒçº§åˆ†ç±»åç§°ï¼ˆå¯¹åº”CSVä¸­çš„"äºŒçº§åˆ†ç±»"ï¼‰',
  `product_image_url_big` varchar(1000) DEFAULT NULL COMMENT 'ä¸»å›¾URLï¼ˆå¯¹åº”CSVä¸­çš„"ä¸»å›¾"ï¼‰',
  `pdf_url` varchar(1000) DEFAULT NULL COMMENT 'PDFæ–‡ä»¶URLï¼ˆå¯¹åº”CSVä¸­çš„"pdf"ï¼‰',
  `ladder_price1_quantity` int DEFAULT NULL COMMENT 'é˜¶æ¢¯ä»·1_æ•°é‡',
  `ladder_price1_price` decimal(10,4) DEFAULT NULL COMMENT 'é˜¶æ¢¯ä»·1_ä»·æ ¼',
  `ladder_price2_quantity` int DEFAULT NULL COMMENT 'é˜¶æ¢¯ä»·2_æ•°é‡',
  `ladder_price2_price` decimal(10,4) DEFAULT NULL COMMENT 'é˜¶æ¢¯ä»·2_ä»·æ ¼',
  `ladder_price3_quantity` int DEFAULT NULL COMMENT 'é˜¶æ¢¯ä»·3_æ•°é‡',
  `ladder_price3_price` decimal(10,4) DEFAULT NULL COMMENT 'é˜¶æ¢¯ä»·3_ä»·æ ¼',
  `ladder_price4_quantity` int DEFAULT NULL COMMENT 'é˜¶æ¢¯ä»·4_æ•°é‡',
  `ladder_price4_price` decimal(10,4) DEFAULT NULL COMMENT 'é˜¶æ¢¯ä»·4_ä»·æ ¼',
  `ladder_price5_quantity` int DEFAULT NULL COMMENT 'é˜¶æ¢¯ä»·5_æ•°é‡',
  `ladder_price5_price` decimal(10,4) DEFAULT NULL COMMENT 'é˜¶æ¢¯ä»·5_ä»·æ ¼',
  `parameters_text` text COMMENT 'äº§å“å‚æ•°æ–‡æœ¬æ ¼å¼ï¼ˆå¯¹åº”CSVä¸­çš„"äº§å“å‚æ•°"ï¼‰',
  PRIMARY KEY (`id`),
  UNIQUE KEY `product_code` (`product_code`),
  KEY `idx_category_level2` (`category_level2_id`),
  KEY `idx_category_level1_name` (`category_level1_name`),
  KEY `idx_category_level2_name` (`category_level2_name`),
  KEY `idx_ladder_price1_quantity` (`ladder_price1_quantity`),
  KEY `idx_ladder_price2_quantity` (`ladder_price2_quantity`),
  KEY `idx_ladder_price3_quantity` (`ladder_price3_quantity`)
) ENGINE=InnoDB AUTO_INCREMENT=251251 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='äº§å“ä¿¡æ¯ä¸»è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `shops`
--

DROP TABLE IF EXISTS `shops`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shops` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'åº—é“ºID',
  `shop_name` varchar(200) NOT NULL COMMENT 'åº—é“ºåç§°ï¼ˆè‹±æ–‡ã€ä¸­æ–‡ã€æ•°å­—å’Œç¬¦å·ï¼‰',
  `shipping_template_id` varchar(100) NOT NULL COMMENT 'è¿è´¹æ¨¡æ¿IDç ï¼ˆæ•°å­—ä¸²ï¼‰',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `shipping_template_id` (`shipping_template_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='åº—é“ºåŠè¿è´¹æ¨¡æ¿è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `task_logs`
--

DROP TABLE IF EXISTS `task_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®ID',
  `task_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ä»»åŠ¡ID',
  `parent_task_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `task_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'UNKNOWN',
  `level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INFO' COMMENT 'æ—¥å¿—çº§åˆ«',
  `step` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNKNOWN' COMMENT 'ä»»åŠ¡æ­¥éª¤',
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æ—¥å¿—æ¶ˆæ¯',
  `progress` int DEFAULT NULL COMMENT 'ä»»åŠ¡è¿›åº¦ç™¾åˆ†æ¯”(0-100)',
  `duration_ms` bigint DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `extra_data` json DEFAULT NULL COMMENT 'æ‰©å±•æ•°æ®',
  `metadata` json DEFAULT NULL,
  `error_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `retry_count` int DEFAULT '0',
  `sequence_order` int DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_level` (`level`),
  KEY `idx_step` (`step`),
  KEY `idx_task_id_create_time` (`task_id`,`create_time` DESC),
  KEY `idx_level_create_time` (`level`,`create_time` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=3605 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ä»»åŠ¡æ—¥å¿—è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-10-09  4:26:29


-- 新增三级分类表
DROP TABLE IF EXISTS `category_level3_codes`;
CREATE TABLE `category_level3_codes` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `category_level3_name` VARCHAR(255) NOT NULL,
  `catalog_id` VARCHAR(64) UNIQUE NOT NULL,
  `category_level1_id` INT NOT NULL,
  `category_level2_id` INT NOT NULL,
  `crawl_status` VARCHAR(32) DEFAULT 'NOT_STARTED',
  `crawl_progress` INT DEFAULT 0,
  `last_crawl_time` DATETIME NULL,
  `total_products` INT DEFAULT 0,
  `crawled_products` INT DEFAULT 0,
  `current_page` INT DEFAULT 0,
  `error_message` TEXT,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (`category_level1_id`) REFERENCES `category_level1_codes`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`category_level2_id`) REFERENCES `category_level2_codes`(`id`) ON DELETE CASCADE,
  INDEX `idx_category_level2_id` (`category_level2_id`),
  INDEX `idx_category_level1_id` (`category_level1_id`)
);

-- 为了保证层级查询性能，按 catalog_id 建立唯一索引（已在字段定义中）

-- 为products表添加category_level3_id字段
ALTER TABLE products 
ADD COLUMN category_level3_id INT DEFAULT NULL COMMENT '所属三级分类ID（外键，可为空）' 
AFTER category_level2_id;

-- 添加索引
ALTER TABLE products 
ADD INDEX idx_category_level3 (category_level3_id);

ALTER TABLE products ADD COLUMN category_level3_name VARCHAR(100) DEFAULT NULL COMMENT '三级分类名称' AFTER category_level2_name;

-- 为分类表的catalog_id字段添加UNIQUE索引，确保ID稳定性
-- 这样可以避免重新同步分类后ID变化导致前端保存的ID失效

-- 1. 为一级分类表添加catalog_id唯一索引
ALTER TABLE category_level1_codes
ADD UNIQUE KEY `uk_catalog_id` (`catalog_id`);

-- 2. 为二级分类表添加catalog_id唯一索引
ALTER TABLE category_level2_codes
ADD UNIQUE KEY `uk_catalog_id` (`catalog_id`);

-- 注意：三级分类表已经有catalog_id的UNIQUE约束，无需添加
-- 修复products表中错误的图片URL（将"https:null"等错误URL设为NULL）
UPDATE products
SET product_image_url_big = NULL,
    image_name = NULL
WHERE product_image_url_big LIKE '%:null%' OR product_image_url_big = 'null';

-- 同时修复其他可能的空图片字段
UPDATE products
SET main_image_local_path = NULL
WHERE main_image_local_path LIKE '%:null%' OR main_image_local_path = 'null';

UPDATE products
SET image_local_path = NULL
WHERE image_local_path LIKE '%:null%' OR image_local_path = 'null';

-- P0-5: 分类名称持久化修复
-- 目标：区分API源名称和用户自定义名称，确保用户编辑的中文名称永久保存

-- ========== 一级分类表 ==========
ALTER TABLE `category_level1_codes`
    ADD COLUMN `source_name` VARCHAR(200) NULL COMMENT 'API源名称（只读）' AFTER `category_level1_name`,
    ADD COLUMN `custom_name` VARCHAR(200) NULL COMMENT '用户自定义名称' AFTER `source_name`,
    ADD COLUMN `is_customized` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否被用户修改过（0=否，1=是）' AFTER `custom_name`;

-- 迁移现有数据：将现有名称复制到source_name
UPDATE `category_level1_codes`
SET `source_name` = `category_level1_name`,
    `is_customized` = 0
WHERE `source_name` IS NULL;

-- ========== 二级分类表 ==========
ALTER TABLE `category_level2_codes`
    ADD COLUMN `source_name` VARCHAR(200) NULL COMMENT 'API源名称（只读）' AFTER `category_level2_name`,
    ADD COLUMN `custom_name` VARCHAR(200) NULL COMMENT '用户自定义名称' AFTER `source_name`,
    ADD COLUMN `is_customized` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否被用户修改过（0=否，1=是）' AFTER `custom_name`;

-- 迁移现有数据
UPDATE `category_level2_codes`
SET `source_name` = `category_level2_name`,
    `is_customized` = 0
WHERE `source_name` IS NULL;

-- ========== 三级分类表 ==========
ALTER TABLE `category_level3_codes`
    ADD COLUMN `source_name` VARCHAR(200) NULL COMMENT 'API源名称（只读）' AFTER `category_level3_name`,
    ADD COLUMN `custom_name` VARCHAR(200) NULL COMMENT '用户自定义名称' AFTER `source_name`,
    ADD COLUMN `is_customized` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否被用户修改过（0=否，1=是）' AFTER `custom_name`;

-- 迁移现有数据
UPDATE `category_level3_codes`
SET `source_name` = `category_level3_name`,
    `is_customized` = 0
WHERE `source_name` IS NULL;

-- ========== 说明 ==========
-- 字段用途：
-- - category_levelX_name: 显示名称（优先显示custom_name，若为NULL则显示source_name）
-- - source_name: API原始名称（只读，每次同步会更新）
-- - custom_name: 用户自定义名称（手动编辑后设置，优先级最高）
-- - is_customized: 标记是否被用户修改（1=已修改，同步时不会覆盖custom_name）

-- 同步逻辑（伪代码）：
-- if (is_customized == 0) {
--     source_name = apiData.name;  // 更新API源名称
--     category_levelX_name = source_name;  // 显示API名称
-- } else {
--     source_name = apiData.name;  // 仍然更新源名称（保留备份）
--     category_levelX_name = custom_name;  // 显示用户自定义名称
-- }

-- ========================================
-- P0-6: 价格阶梯扩展（从5级扩展至6级）
-- 创建时间: 2025-11-21
-- 描述: 为products表添加第6级阶梯价字段
-- ========================================

-- 添加第6级阶梯价字段
ALTER TABLE `products`
    ADD COLUMN `ladder_price6_quantity` INT NULL COMMENT '阶梯6数量' AFTER `ladder_price5_price`,
    ADD COLUMN `ladder_price6_price` DECIMAL(10, 4) NULL COMMENT '阶梯6单价（CNY）' AFTER `ladder_price6_quantity`;

-- 添加索引（可选，如果需要按阶梯6价格查询）
-- CREATE INDEX idx_ladder_price6_quantity ON products(ladder_price6_quantity);
-- CREATE INDEX idx_ladder_price6_price ON products(ladder_price6_price);

