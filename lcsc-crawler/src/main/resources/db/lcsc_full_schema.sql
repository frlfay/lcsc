-- LCSC 数据库初始化脚本
-- 简化版本，兼容 Spring Boot SQL 初始化

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 一级分类表
DROP TABLE IF EXISTS `category_level1_codes`;
CREATE TABLE `category_level1_codes` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '一级分类ID',
  `category_level1_name` varchar(100) NOT NULL DEFAULT '未命名分类' COMMENT '一级分类名称',
  `source_name` varchar(200) NULL COMMENT 'API源名称（只读）',
  `custom_name` varchar(200) NULL COMMENT '用户自定义名称',
  `is_customized` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否被用户修改过',
  `catalog_id` varchar(50) NOT NULL DEFAULT '' COMMENT '立创API返回的catalogId',
  `category_code` varchar(50) DEFAULT NULL COMMENT '分类码',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `category_level1_name` (`category_level1_name`),
  UNIQUE KEY `uk_catalog_id` (`catalog_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一级分类表';

-- 二级分类表
DROP TABLE IF EXISTS `category_level2_codes`;
CREATE TABLE `category_level2_codes` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '二级分类ID',
  `category_level2_name` varchar(100) NOT NULL DEFAULT '未命名二级分类' COMMENT '二级分类名称',
  `source_name` varchar(200) NULL COMMENT 'API源名称（只读）',
  `custom_name` varchar(200) NULL COMMENT '用户自定义名称',
  `is_customized` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否被用户修改过',
  `catalog_id` varchar(50) NOT NULL DEFAULT '' COMMENT '立创API返回的catalogId',
  `category_level1_id` int NOT NULL COMMENT '所属一级分类ID',
  `shop_category_codes` json DEFAULT NULL COMMENT '各店铺分类码JSON',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `crawl_status` varchar(20) DEFAULT 'NOT_STARTED' COMMENT '爬取状态',
  `crawl_progress` int DEFAULT 0 COMMENT '爬取进度 0-100',
  `last_crawl_time` datetime DEFAULT NULL,
  `total_products` int DEFAULT 0,
  `crawled_products` int DEFAULT 0,
  `current_page` int DEFAULT 0,
  `error_message` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_category_combo` (`category_level2_name`,`category_level1_id`),
  UNIQUE KEY `uk_catalog_id` (`catalog_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='二级分类表';

-- 三级分类表
DROP TABLE IF EXISTS `category_level3_codes`;
CREATE TABLE `category_level3_codes` (
  `id` int NOT NULL AUTO_INCREMENT,
  `category_level3_name` varchar(255) NOT NULL,
  `source_name` varchar(200) NULL COMMENT 'API源名称（只读）',
  `custom_name` varchar(200) NULL COMMENT '用户自定义名称',
  `is_customized` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否被用户修改过',
  `catalog_id` varchar(64) NOT NULL,
  `category_level1_id` int NOT NULL,
  `category_level2_id` int NOT NULL,
  `crawl_status` varchar(32) DEFAULT 'NOT_STARTED',
  `crawl_progress` int DEFAULT 0,
  `last_crawl_time` datetime NULL,
  `total_products` int DEFAULT 0,
  `crawled_products` int DEFAULT 0,
  `current_page` int DEFAULT 0,
  `error_message` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `catalog_id` (`catalog_id`),
  INDEX `idx_category_level2_id` (`category_level2_id`),
  INDEX `idx_category_level1_id` (`category_level1_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='三级分类表';

-- 爬虫配置表
DROP TABLE IF EXISTS `crawler_config`;
CREATE TABLE `crawler_config` (
  `id` int NOT NULL AUTO_INCREMENT,
  `config_key` varchar(50) NOT NULL COMMENT '配置键',
  `config_value` varchar(200) DEFAULT NULL COMMENT '配置值',
  `description` varchar(200) DEFAULT NULL COMMENT '描述',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='爬虫配置表';

-- 爬虫任务表
DROP TABLE IF EXISTS `crawler_tasks`;
CREATE TABLE `crawler_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(100) NOT NULL,
  `task_type` varchar(50) NOT NULL,
  `task_status` varchar(20) NOT NULL,
  `task_params` json DEFAULT NULL,
  `task_result` json DEFAULT NULL,
  `priority` int DEFAULT 1,
  `retry_count` int DEFAULT 0,
  `error_message` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `execution_duration_ms` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `task_id` (`task_id`),
  KEY `idx_task_status` (`task_status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='爬虫任务表';

-- 图片链接表
DROP TABLE IF EXISTS `image_links`;
CREATE TABLE `image_links` (
  `id` int NOT NULL AUTO_INCREMENT,
  `image_name` varchar(100) NOT NULL COMMENT '图片名称',
  `shop_id` int NOT NULL COMMENT '店铺ID',
  `image_link` varchar(1000) NOT NULL COMMENT '图片链接URL',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_image_shop` (`image_name`,`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图片链接表';

-- 产品表
DROP TABLE IF EXISTS `products`;
CREATE TABLE `products` (
  `id` int NOT NULL AUTO_INCREMENT,
  `product_code` varchar(20) NOT NULL COMMENT '产品编号',
  `category_level1_id` int NOT NULL,
  `category_level2_id` int DEFAULT NULL,
  `category_level3_id` int DEFAULT NULL,
  `brand` varchar(200) DEFAULT NULL COMMENT '品牌',
  `model` varchar(200) DEFAULT NULL COMMENT '型号',
  `package_name` varchar(100) DEFAULT NULL COMMENT '封装',
  `pdf_filename` varchar(500) DEFAULT NULL,
  `pdf_local_path` varchar(1000) DEFAULT NULL,
  `image_name` varchar(512) DEFAULT NULL,
  `image_local_path` varchar(1000) DEFAULT NULL,
  `total_stock_quantity` int DEFAULT 0,
  `brief_description` varchar(200) DEFAULT NULL,
  `tier_prices` json DEFAULT NULL,
  `tier_prices_last_update` date DEFAULT NULL,
  `tier_prices_manual_edit` tinyint(1) DEFAULT 0,
  `detailed_parameters` json DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_crawled_at` timestamp NULL DEFAULT NULL,
  `product_images_info` json DEFAULT NULL,
  `main_image_local_path` varchar(500) DEFAULT NULL,
  `category_level1_name` varchar(100) DEFAULT NULL,
  `category_level2_name` varchar(100) DEFAULT NULL,
  `category_level3_name` varchar(100) DEFAULT NULL,
  `product_image_url_big` varchar(1000) DEFAULT NULL,
  `pdf_url` varchar(1000) DEFAULT NULL,
  `ladder_price1_quantity` int DEFAULT NULL,
  `ladder_price1_price` decimal(10,4) DEFAULT NULL,
  `ladder_price2_quantity` int DEFAULT NULL,
  `ladder_price2_price` decimal(10,4) DEFAULT NULL,
  `ladder_price3_quantity` int DEFAULT NULL,
  `ladder_price3_price` decimal(10,4) DEFAULT NULL,
  `ladder_price4_quantity` int DEFAULT NULL,
  `ladder_price4_price` decimal(10,4) DEFAULT NULL,
  `ladder_price5_quantity` int DEFAULT NULL,
  `ladder_price5_price` decimal(10,4) DEFAULT NULL,
  `ladder_price6_quantity` int DEFAULT NULL,
  `ladder_price6_price` decimal(10,4) DEFAULT NULL,
  `parameters_text` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `product_code` (`product_code`),
  KEY `idx_category_level2` (`category_level2_id`),
  KEY `idx_category_level3` (`category_level3_id`),
  KEY `idx_category_level1_name` (`category_level1_name`),
  KEY `idx_category_level2_name` (`category_level2_name`),
  KEY `idx_ladder_price1_quantity` (`ladder_price1_quantity`),
  KEY `idx_ladder_price2_quantity` (`ladder_price2_quantity`),
  KEY `idx_ladder_price3_quantity` (`ladder_price3_quantity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品表';

-- 店铺表
DROP TABLE IF EXISTS `shops`;
CREATE TABLE `shops` (
  `id` int NOT NULL AUTO_INCREMENT,
  `shop_name` varchar(200) NOT NULL COMMENT '店铺名称',
  `shipping_template_id` varchar(100) NOT NULL COMMENT '运费模板ID',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `shipping_template_id` (`shipping_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='店铺表';

-- 任务日志表
DROP TABLE IF EXISTS `task_logs`;
CREATE TABLE `task_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(100) NOT NULL,
  `parent_task_id` varchar(100) DEFAULT NULL,
  `task_type` varchar(50) DEFAULT 'UNKNOWN',
  `level` varchar(20) NOT NULL DEFAULT 'INFO',
  `step` varchar(50) NOT NULL DEFAULT 'UNKNOWN',
  `message` text NOT NULL,
  `progress` int DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `extra_data` json DEFAULT NULL,
  `metadata` json DEFAULT NULL,
  `error_code` varchar(50) DEFAULT NULL,
  `retry_count` int DEFAULT 0,
  `sequence_order` int DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_level` (`level`),
  KEY `idx_step` (`step`),
  KEY `idx_task_id_create_time` (`task_id`,`create_time` DESC),
  KEY `idx_level_create_time` (`level`,`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务日志表';

SET FOREIGN_KEY_CHECKS = 1;

-- 为shops表添加seller_category_id字段
-- 用于淘宝CSV导出时的店铺分类码

ALTER TABLE `shops`
    ADD COLUMN `seller_category_id` VARCHAR(100) NULL COMMENT '店铺分类码（用于淘宝导出）' AFTER `shipping_template_id`;
