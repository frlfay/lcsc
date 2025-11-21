# 立创商城爬虫系统 (LCSC Crawler System)

[![Java](https://img.shields.io/badge/Java-17-red.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1.6-green.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.3.8-brightgreen.svg)](https://vuejs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.0-red.svg)](https://redis.io/)

一个基于 Spring Boot + Vue 3 的立创商城产品数据爬虫系统，支持智能任务调度、三级分类爬取、品牌拆分突破5000条限制、实时进度监控等功能。

## 📖 目录

- [功能特性](#-功能特性)
- [技术栈](#-技术栈)
- [环境要求](#-环境要求)
- [快速开始](#-快速开始)
  - [方式一：Docker一键启动（推荐）](#方式一docker一键启动推荐)
  - [方式二：本地开发环境](#方式二本地开发环境)
- [详细使用指南](#-详细使用指南)
- [项目结构](#-项目结构)
- [配置说明](#-配置说明)
- [常见问题](#-常见问题)
- [开发规范](#-开发规范)
- [许可证](#-许可证)

---

## ✨ 功能特性

### 核心功能
- ✅ **智能分类爬取**：支持一级/二级/三级分类的递归爬取
- ✅ **突破5000条限制**：通过品牌拆分策略，突破立创API的5000条限制
- ✅ **实时任务调度**：基于Redis的优先级任务队列，支持手动/自动任务
- ✅ **多线程并发**：可配置线程池（2-4线程），提升爬取效率
- ✅ **分类名称管理**：区分API源名称和用户自定义名称，防止覆盖
- ✅ **6级阶梯价格**：完整保存产品价格阶梯信息
- ✅ **图片智能选择**：按优先级（front > blank > package > back）选择产品图片
- ✅ **PDF开关下载**：可配置是否下载PDF，默认关闭节省带宽

### 用户体验
- 📊 **实时进度监控**：WebSocket实时推送爬取进度和日志
- 🌲 **树形分类选择**：树形结构展示分类，支持记忆上次选择
- 📈 **数据可视化**：爬取统计、成功率、产品分布等图表展示
- 🔍 **产品管理**：支持搜索、筛选、编辑、导出产品数据
- 🖼️ **图片链接管理**：独立模块管理多平台图片链接（Excel导入）
- 📤 **高级导出**：多维度筛选、价格折扣、聚合数据导出

### 技术亮点
- ⚡ **批量处理**：批量保存数据，减少数据库交互
- 🔄 **智能重试**：支持失败自动重试（最多10次）
- 🛡️ **限流保护**：动态限流，避免被封IP
- 💾 **内存优化**：流式处理大数据量，避免OOM

---

## 🛠 技术栈

### 后端
- **框架**：Spring Boot 3.1.6 + MyBatis Plus 3.5.3
- **语言**：Java 17
- **数据库**：MySQL 8.0
- **缓存**：Redis 7.0
- **构建工具**：Maven 3.8+

### 前端
- **框架**：Vue 3.3.8 + TypeScript 5.2
- **UI组件**：Ant Design Vue 4.0
- **图表**：ECharts 6.0
- **构建工具**：Vite 5.0

### 基础设施
- **容器化**：Docker + Docker Compose
- **数据库管理**：phpMyAdmin
- **缓存管理**：Redis Commander

---

## 📋 环境要求

### 必需环境
- **操作系统**：Windows 10+、macOS 10.15+、Linux（Ubuntu 20.04+）
- **Docker**：20.10+ 及 Docker Compose 2.0+
- **Node.js**：18.x 或 20.x（本地开发需要）
- **Java**：JDK 17+（本地开发需要）
- **Maven**：3.8+（本地开发需要）

### 可选环境
- **Git**：版本管理
- **IDE**：IntelliJ IDEA（后端）、VSCode（前端）

---

## 🚀 快速开始

### 方式一：Docker一键启动（推荐）

**适用场景**：快速体验、生产部署

#### 1. 克隆项目
```bash
git clone git@github.com:frlfay/lcsc.git
```

#### 2. 启动基础服务（MySQL + Redis）
```bash
# 启动数据库和缓存
docker compose up -d

# 查看服务状态
docker compose ps

# 预期输出：
# lcsc-mysql         运行中   0.0.0.0:13306->3306/tcp
# lcsc-redis         运行中   0.0.0.0:6380->6379/tcp
# lcsc-phpmyadmin    运行中   0.0.0.0:8081->80/tcp
# lcsc-redis-commander 运行中 0.0.0.0:8082->8081/tcp
```

#### 3. 初始化数据库
```bash
# 方法1: 使用phpMyAdmin（浏览器访问 http://localhost:8081）
# 用户名: lcsc_user
# 密码: lcsc123456
# 导入文件: lcsc-crawler/src/main/resources/db/lcsc_full_schema.sql

# 方法2: 命令行导入
docker exec -i lcsc-mysql mysql -ulcsc_user -plcsc123456 lcsc < lcsc-crawler/src/main/resources/db/lcsc_full_schema.sql
```

#### 4. 启动后端
```bash
cd lcsc-crawler

# Windows
mvnw.cmd spring-boot:run

# macOS/Linux
mvn spring-boot:run
```

访问：http://localhost:8080/api/health （应返回 `{"status":"UP"}`）

#### 5. 启动前端
```bash
cd lcsc-frontend

# 安装依赖（首次运行）
npm install

# 启动开发服务器
npm run dev
```

访问：http://localhost:5173

---

### 方式二：本地开发环境

**适用场景**：开发调试、功能开发

#### 1. 安装依赖服务

**选项A：使用Docker（推荐）**
```bash
# 只启动MySQL和Redis
docker-compose up -d lcsc-mysql lcsc-redis
```

**选项B：本地安装**
- MySQL 8.0：端口3306，创建数据库 `lcsc`
- Redis 7.0：端口6379，设置密码 `lcsc123456`

#### 2. 配置后端

编辑 `lcsc-crawler/src/main/resources/application.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:13306/lcsc  # Docker使用13306，本地MySQL使用3306
    username: lcsc_user
    password: lcsc123456
  redis:
    host: localhost
    port: 6380  # Docker使用6380，本地Redis使用6379
    password: lcsc123456
```

#### 3. 启动后端
```bash
cd lcsc-crawler

# 使用Maven
mvn clean spring-boot:run

# 或使用IDE（IntelliJ IDEA）
# 直接运行 LcscApplication.java
```

#### 4. 启动前端
```bash
cd lcsc-frontend

# 安装依赖
npm install

# 启动
npm run dev
```

---

## 📚 详细使用指南

### 第一步：同步分类

1. 访问前端页面：http://localhost:5173
2. 进入 **"分类管理"** 页面
3. 点击 **"同步分类"** 按钮
4. 等待同步完成（通常需要10-30秒）
5. 查看一级、二级、三级分类数据

### 第二步：创建爬虫任务

#### 方式A：手动选择分类爬取（推荐）
1. 进入 **"爬虫控制台V3"** 页面
2. 在树形选择器中勾选需要爬取的分类
   - 支持一级、二级、三级分类
   - 支持多选
   - 会自动记住上次选择
3. 点击 **"创建爬虫任务"**
4. 查看任务队列状态

#### 方式B：全量爬取
1. 进入 **"爬虫控制台V3"** 页面
2. 点击 **"全量爬取"** 按钮
3. 系统会自动为所有分类创建任务

### 第三步：启动爬虫

1. 点击 **"启动爬虫"** 按钮
2. 实时查看：
   - 任务进度条
   - WebSocket实时日志
   - 产品爬取统计
   - 队列状态（待处理、处理中、已完成）

### 第四步：查看数据

1. 进入 **"产品管理"** 页面
2. 功能：
   - 搜索产品（按型号、品牌、分类）
   - 查看产品详情（价格、库存、参数、图片、PDF）
   - 编辑产品信息
   - 导出Excel

### 第五步：管理图片链接（可选）

**使用场景**：当你将产品图片上传到各电商平台图床后，可以通过此功能管理平台专属链接

1. 进入 **"图片管理"** 页面
2. 下载Excel导入模板
3. 按格式填写：
   ```
   | 店铺名称 | 产品编号 | 图片名称 | 图片链接 |
   | 淘宝旗舰店 | C123456 | C123456_front.jpg | https://img.taobao.com/xxx.jpg |
   ```
4. 上传Excel文件
5. 查看导入结果（成功数/失败数）

**数据模型说明**：
- 支持One-to-Many关系：一张图片可以关联多个店铺的不同链接
- 唯一约束：`(image_name, shop_id)` 组合唯一，重复导入会更新

### 第六步：高级导出（可选）

**使用场景**：需要按特定条件导出聚合数据（含运费模板、图片链接、阶梯价等）

1. 进入 **"高级导出"** 页面
2. 配置筛选条件：
   - **店铺选择**：选择导出哪些店铺的数据（不选则全部）
   - **分类筛选**：树形选择器选择二级分类
   - **品牌筛选**：输入品牌名称（支持多个）
   - **关键词搜索**：模糊匹配产品编号/型号/品牌
   - **价格折扣**：输入折扣百分比（如90表示9折）
3. 导出选项：
   - ☑️ 包含图片链接（每个店铺一列）
   - ☑️ 包含阶梯价格（6级）
4. 点击 **"预览数据"** 查看统计信息
5. 点击 **"导出Excel"** 下载文件

**导出内容说明**：
- 基础列：产品编号、品牌、型号、封装、分类、库存
- 阶梯价格：6级数量+价格（应用折扣后）
- 店铺列：运费模板ID、图片链接（优先自定义链接，备用立创原始链接）

---

## 📁 项目结构

```
lcsc-crawler/
├── lcsc-crawler/                 # 后端项目
│   ├── src/main/java/com/lcsc/
│   │   ├── config/              # 配置类（Redis、MyBatis、WebSocket等）
│   │   ├── controller/          # 控制器（API接口）
│   │   ├── entity/              # 实体类（数据库表映射）
│   │   ├── mapper/              # MyBatis Mapper
│   │   ├── service/             # 业务逻辑
│   │   │   └── crawler/         # 爬虫相关服务
│   │   │       ├── v3/          # V3版本（工作池、队列、任务拆分）
│   │   │       ├── core/        # 核心爬虫逻辑
│   │   │       ├── network/     # 网络层（限流、重试）
│   │   │       └── data/        # 数据处理（批量保存）
│   │   └── LcscApplication.java # 启动类
│   ├── src/main/resources/
│   │   ├── application.yml      # 配置文件
│   │   └── db/                  # 数据库脚本
│   │       └── lcsc_full_schema.sql  # 完整数据库Schema
│   └── pom.xml                  # Maven依赖
├── lcsc-frontend/               # 前端项目
│   ├── src/
│   │   ├── views/               # 页面组件
│   │   │   ├── DashboardV3.vue  # 爬虫控制台V3
│   │   │   ├── CategoryManagement.vue  # 分类管理
│   │   │   ├── ProductManagement.vue   # 产品管理
│   │   │   ├── ImageManagement.vue     # 图片链接管理
│   │   │   └── AdvancedExport.vue      # 高级导出
│   │   ├── api/                 # API接口封装
│   │   ├── components/          # 通用组件
│   │   └── utils/               # 工具函数
│   ├── package.json             # 依赖管理
│   └── vite.config.ts           # Vite配置
├── docker-compose.yml           # Docker编排文件
├── .claude/                     # Claude开发规则
│   └── claude.md                # 项目开发规范
└── README.md                    # 本文件
```

---

## ⚙️ 配置说明

### 后端配置（application.yml）

#### 数据库配置
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:13306/lcsc
    username: lcsc_user
    password: lcsc123456
```

#### 爬虫配置
```yaml
crawler:
  delay: 2000                    # 爬取间隔（毫秒），建议2000-5000
  timeout: 10000                 # 超时时间（毫秒）
  max-retry: 10                  # 最大重试次数
  thread-pool-size: 5            # 线程池大小（2-5）
  auto-retry: true               # 是否自动重试
  save-images: true              # 是否保存图片
  enable-pdf-download: false     # 是否下载PDF（默认关闭）
  storage:
    base-path: "data"            # 数据存储路径
```

**重要配置项说明**：
- `delay`：请求间隔，过小可能被封IP，建议≥2000ms
- `enable-pdf-download`：全站爬取时建议关闭，节省带宽和存储

### 前端配置

#### 开发环境（.env.development）
```env
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080/ws
```

#### 生产环境（.env.production）
```env
VITE_API_BASE_URL=https://your-domain.com
VITE_WS_URL=wss://your-domain.com/ws
```

---

## ❓ 常见问题

### 1. 启动后端报错：连接MySQL失败
**原因**：MySQL未启动或端口配置错误

**解决**：
```bash
# 检查MySQL容器状态
docker-compose ps lcsc-mysql

# 查看MySQL日志
docker-compose logs lcsc-mysql

# 重启MySQL
docker-compose restart lcsc-mysql
```

### 2. 前端显示"网络错误"
**原因**：后端未启动或端口被占用

**解决**：
```bash
# 检查后端是否运行（应返回200）
curl http://localhost:8080/api/health

# 检查端口占用
lsof -i :8080  # macOS/Linux
netstat -ano | findstr :8080  # Windows
```

### 3. 爬虫任务一直"处理中"
**原因**：Worker池未启动或Redis连接失败

**解决**：
```bash
# 检查Redis连接
docker exec -it lcsc-redis redis-cli -a lcsc123456 ping
# 应返回 PONG

# 查看后端日志
# 搜索 "Worker-X 已启动" 日志
```

### 4. 图片/PDF下载失败
**原因**：网络问题或存储路径权限不足

**解决**：
```bash
# 检查存储目录权限
ls -la data/images data/pdfs

# 手动创建目录
mkdir -p data/images data/pdfs
chmod 755 data/images data/pdfs
```

### 5. 数据库导入失败
**原因**：字符集或SQL语法错误

**解决**：
```bash
# 检查数据库字符集
docker exec lcsc-mysql mysql -ulcsc_user -plcsc123456 -e "SHOW VARIABLES LIKE 'character_set%';"

# 重新创建数据库
docker exec -it lcsc-mysql mysql -uroot -plcsc123456
DROP DATABASE lcsc;
CREATE DATABASE lcsc CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
exit

# 重新导入
docker exec -i lcsc-mysql mysql -ulcsc_user -plcsc123456 lcsc < lcsc-crawler/src/main/resources/db/lcsc_full_schema.sql
```

### 6. 爬取5000+产品的分类失败
**原因**：未触发品牌拆分或拆分失败

**解决**：
- 检查日志中是否有 "启动品牌拆分策略" 提示
- 确认 `TaskSplitService` 正常运行
- 查看子任务是否创建成功（队列状态显示"含XX个品牌子任务"）

---

## 📖 开发规范

详细开发规范请查看：[.claude/claude.md](.claude/claude.md)

### 核心原则
- **优先编辑现有文件**，避免创建新文件
- **保持代码风格一致**，遵循项目现有规范
- **安全第一**，避免SQL注入、XSS等漏洞
- **批量操作使用事务**，确保数据一致性

### 提交规范
```
<type>(<scope>): <subject>

type: feat/fix/refactor/perf/docs/test/chore
scope: crawler/frontend/database等
subject: 简短描述（≤50字符）
```

示例：
```
feat(crawler): 支持三级分类爬取

- 修改CategoryCrawlerService,支持递归遍历三级分类
- 更新数据库schema,添加category_level3_codes表
- 前端增加三级分类选择器组件

Closes #123
```

---

## 🔧 高级功能

### 自定义分类名称
1. 进入 **"分类管理"** 页面
2. 点击分类右侧的 **"编辑名称"** 按钮
3. 输入自定义名称
4. 系统会记录 `is_customized=1`，后续同步不会覆盖

### 批量导出产品
1. 进入 **"产品管理"** 页面
2. 使用筛选条件过滤产品
3. 点击 **"导出Excel"** 按钮
4. 下载包含完整字段的Excel文件

### 图片链接管理
**功能**：管理产品在各电商平台的图片链接
1. 进入 **"图片管理"** 页面
2. 下载导入模板（格式：店铺名称、产品编号、图片名称、图片链接）
3. 填写Excel并上传
4. 查看导入结果（成功数/失败数/错误详情）

**数据说明**：
- 支持一张图片关联多个店铺（One-to-Many）
- 重复导入会更新已有数据（UPSERT模式）

### 高级导出
**功能**：按条件筛选并导出聚合数据（包含运费模板、图片链接、阶梯价）

1. 进入 **"高级导出"** 页面
2. 设置筛选条件：店铺/分类/品牌/关键词/折扣
3. 勾选导出选项：图片链接、阶梯价格
4. 预览统计数据
5. 导出Excel

**图片链接逻辑**：
- 优先使用自定义链接（`image_links`表）
- 备用立创原始链接（`products.product_image_url_big`）
- 即使未导入自定义链接，也能导出立创图片

### 监控爬虫状态
- 访问：http://localhost:8081（phpMyAdmin）查看数据库
- 访问：http://localhost:8082（Redis Commander）查看队列
- 查看后端日志：`logs/lcsc-crawler.log`

---

## 📊 性能优化建议

### 1. 调整线程数
```yaml
crawler:
  thread-pool-size: 3  # 根据服务器性能调整（2-5）
```

### 2. 调整批量大小
```java
// BatchDataProcessor.java
private static final int BATCH_SIZE = 100;  // 默认100，可调整为50-200
```

### 3. 数据库连接池
```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20  # 根据并发量调整
```

---

## 📝 更新日志

### v1.3.0 (2025-11-21)
- ✅ **P2-1**: 高级导出独立模块
  - 多维度筛选（店铺/分类/品牌/关键词）
  - 价格折扣设置（1-100%）
  - 聚合数据导出（运费模板+图片链接+阶梯价）
  - 预览功能（统计产品/店铺/分类/品牌数量）
  - 图片链接Fallback（优先自定义链接，备用立创原始链接）

### v1.2.0 (2025-11-21)
- ✅ **P1-1**: 图片链接管理独立模块
  - Excel导入功能（模板下载+文件上传）
  - One-to-Many数据模型（一张图片→N个店铺→N条链接）
  - 表格显示优化（固定布局+文本截断）

### v1.1.0 (2025-11-21)
- ✅ **P0-1**: 爬虫选区UI升级（树形结构）
- ✅ **P0-2**: 爬虫选择记忆功能
- ✅ **P0-3**: 三级分类兼容性修复
- ✅ **P0-4**: 突破5000条列表限制（品牌拆分）
- ✅ **P0-5**: 分类名称持久化修复
- ✅ **P0-6**: 价格阶梯扩展至6级
- ✅ **P0-7**: 图片命名与逻辑重构
- ✅ **P0-8**: PDF命名与开关

详细更新记录：[.claude/claude.md](.claude/claude.md)

---

## 📄 许可证

本项目仅供学习和研究使用，请遵守立创商城的使用条款。

**免责声明**：
- 本项目不得用于商业用途
- 使用本项目造成的任何法律后果由使用者自行承担
- 请合理控制爬取频率，避免对目标网站造成压力

---

## 🤝 贡献指南

欢迎提交Issue和Pull Request！

### 开发流程
1. Fork本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交改动 (`git commit -m 'feat: Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交Pull Request

### 问题反馈
- GitHub Issues：[https://github.com/your-repo/lcsc-crawler/issues](https://github.com/your-repo/lcsc-crawler/issues)
- 邮件联系：your-email@example.com

---

## 📞 联系方式

- **项目维护者**：Your Name
- **Email**：your-email@example.com
- **项目主页**：[https://github.com/your-repo/lcsc-crawler](https://github.com/your-repo/lcsc-crawler)

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给一个Star！⭐**

Made with ❤️ by LCSC Crawler Team

</div>
