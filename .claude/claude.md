# 立创商城爬虫系统 - Claude 开发规则

## 一、项目基本信息

**项目名称**: 立创商城爬虫系统 (LCSC Crawler System)
**技术栈**: Vue 3 + TypeScript + Spring Boot 3.1.6 + MySQL 8.0 + Redis 7
**开发语言**: Java 17 (后端) + TypeScript (前端)
**构建工具**: Maven (后端) + Vite (前端)

---

## 二、核心开发原则

### 1. 代码修改原则
- **优先编辑现有文件**,避免创建新文件
- **保持代码风格一致**,遵循项目现有代码规范
- **避免向后兼容性hack**,如重命名未使用变量、重新导出类型、添加`// removed`注释等
- **删除即删除**,如果代码未使用,直接删除而非注释
- **安全第一**,避免引入SQL注入、XSS、命令注入等OWASP Top 10漏洞

### 2. 架构原则
- **严格分层**: Controller → Service → Mapper (后端)
- **模块化设计**: 功能拆分为独立模块
- **单一职责**: 每个类/组件只负责一项功能
- **接口隔离**: 后端接口与前端API一一对应
- **依赖注入**: 使用Spring的@Autowired/@Resource

### 3. 数据库原则
- **所有表必须有主键** (通常为自增id)
- **所有表必须有created_at和updated_at字段**
- **使用MyBatis Plus的BaseMapper**,避免重复写CRUD
- **批量操作使用事务**,确保数据一致性
- **外键关系通过应用层维护**,不使用数据库外键

---

## 三、后端开发规范

### 1. Controller层规范
```java
@RestController
@RequestMapping("/api/xxx")
public class XxxController {
    @Autowired
    private XxxService xxxService;

    // 统一返回Result<T>
    @GetMapping("/list")
    public Result<PageResult<Xxx>> list(@RequestParam xxx) {
        return Result.success(xxxService.list(xxx));
    }
}
```

**规则**:
- 所有API返回`Result<T>`统一格式
- 使用RESTful风格路由
- 参数验证使用`@Valid`注解
- 异常由`GlobalExceptionHandler`统一处理

### 2. Service层规范
```java
@Service
public class XxxService {
    @Autowired
    private XxxMapper xxxMapper;

    @Transactional(rollbackFor = Exception.class)
    public void batchSave(List<Xxx> list) {
        // 批量保存逻辑
    }
}
```

**规则**:
- 复杂业务逻辑放在Service层
- 涉及多表操作使用`@Transactional`
- 批量操作使用批处理,避免循环单条插入
- Service不直接操作HTTP请求/响应

### 3. Mapper层规范
```java
@Mapper
public interface XxxMapper extends BaseMapper<Xxx> {
    // 自定义查询方法
    List<Xxx> selectByCustomCondition(@Param("xxx") String xxx);
}
```

**规则**:
- 继承`BaseMapper<T>`获取基础CRUD
- 复杂查询写在XML文件中
- 使用`@Param`注解绑定参数

### 4. Entity层规范
```java
@Data
@TableName("xxx_table")
public class Xxx {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

**规则**:
- 使用Lombok `@Data`注解
- 主键使用`@TableId(type = IdType.AUTO)`
- 字段名与数据库列名映射使用`@TableField`
- 时间字段使用`LocalDateTime`

---

## 四、前端开发规范

### 1. API调用规范
```typescript
// src/api/xxx.ts
import request from '@/utils/request';

export const getXxxList = (params: any) => {
  return request.get<PageResult<Xxx>>('/api/xxx/list', { params });
};

export const createXxx = (data: Xxx) => {
  return request.post<Xxx>('/api/xxx', data);
};
```

**规则**:
- 所有API调用封装在`api/`目录
- 使用TypeScript类型约束
- 统一使用`request`工具函数
- 接口路径与后端保持一致

### 2. 组件开发规范
```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue';
import type { Xxx } from '@/types';

const dataSource = ref<Xxx[]>([]);
const loading = ref(false);

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getXxxList();
    dataSource.value = res.data;
  } catch (error) {
    message.error('加载失败');
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  fetchData();
});
</script>
```

**规则**:
- 使用`<script setup>`语法
- 响应式数据使用`ref`或`reactive`
- 异步操作使用`async/await`
- 错误处理使用`try/catch`
- 加载状态使用`loading`标识

### 3. 类型定义规范
```typescript
// src/types/index.ts
export interface Xxx {
  id?: number;
  name: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
}
```

**规则**:
- 所有类型定义在`types/index.ts`
- 与后端实体保持一致
- 可选字段使用`?`标识
- 复用通用类型如`PageResult<T>`

---

## 五、爬虫模块开发规范

### 1. 爬虫服务目录结构
```
service/crawler/
├── core/           # 核心爬虫逻辑
├── network/        # 网络层(HTTP客户端、限流、重试)
├── parser/         # 数据解析器
├── processor/      # 数据处理器
├── error/          # 错误处理
├── memory/         # 内存优化
├── monitoring/     # 监控指标
├── scheduler/      # 任务调度
├── data/           # 批量数据处理
└── v3/             # V3版本(工作池、队列)
```

### 2. 爬虫开发原则
- **限流保护**: 所有API调用必须经过限流器
- **智能重试**: 使用`SmartRetryHandler`处理失败请求
- **批量处理**: 使用`BatchDataProcessor`批量保存数据
- **内存优化**: 大数据量使用流式处理,避免OOM
- **实时推送**: 使用WebSocket推送进度和日志
- **幂等设计**: 爬虫任务可重复执行,不产生重复数据

### 3. 爬虫配置规范
```yaml
crawler:
  delay: 2000                    # 爬取间隔(毫秒)
  timeout: 10000                 # 超时时间(毫秒)
  max-retry: 10                  # 最大重试次数
  thread-pool-size: 5            # 线程池大小
  auto-retry: true               # 自动重试
  save-images: true              # 是否保存图片
  save-pdfs: false               # 是否保存PDF(默认关闭)
```

**规则**:
- 所有配置项可在`application.yml`中调整
- 敏感操作(如删除、停止)需要确认
- 爬取间隔不低于1秒,避免被封IP

---

## 六、数据库设计规范

### 1. 表命名规范
- **小写+下划线**: `product_table`
- **复数形式**: `products`, `categories`
- **关联表**: `table1_table2` (如`product_images`)

### 2. 字段命名规范
- **主键**: `id` (BIGINT AUTO_INCREMENT)
- **外键**: `xxx_id` (如`category_id`)
- **时间戳**: `created_at`, `updated_at` (DATETIME)
- **布尔值**: `is_xxx` (TINYINT 0/1)
- **枚举值**: `xxx_status` (VARCHAR 或 TINYINT)

### 3. 索引规范
- **主键索引**: 自动创建
- **唯一索引**: 唯一约束字段(如`product_code`)
- **普通索引**: 常用查询字段(如`brand`, `category_id`)
- **联合索引**: 多字段组合查询(如`(category_id, brand)`)

### 4. 字段类型规范
| 数据类型 | 数据库类型 | 说明 |
|---------|----------|------|
| 字符串 | VARCHAR(255) | 默认长度255 |
| 长文本 | TEXT | 超过255字符 |
| 整数 | BIGINT | 主键、大数值 |
| 小整数 | INT | 普通数值 |
| 布尔值 | TINYINT | 0/1 |
| 金额 | DECIMAL(10,2) | 精确小数 |
| 时间 | DATETIME | 标准时间格式 |

---

## 七、功能开发优先级

### P0 - 核心爬虫与数据修复 (Critical / Blocker)
**优先级说明**: 直接影响数据完整性、准确性和系统稳定性,必须优先解决

1. **爬虫选区UI升级**
   - 树形结构: 一级/二级/三级折叠��构
   - 默认状态: 一级分类默认不全选

2. **爬虫选择记忆功能**
   - 记住用户上次选择的分类

3. **三级分类兼容性修复 (CRITICAL)**
   - 支持递归遍历三级分类
   - 确保无死角抓取

4. **突破5000条列表限制 (CRITICAL)**
   - 实现"筛选拆分"爬取策略
   - 通过价格区间、品牌、封装等参数切片

5. **分类名称持久化修复**
   - 数据库增加`source_name`(源名称)和`custom_name`(自定义名称)
   - 同步逻辑改为"仅更新未修改过的分类"

6. **价格阶梯扩展**
   - 从5级阶梯价扩展至6级阶梯价

7. **图片命名与逻辑重构**
   - 命名规则: `编号_图类.jpg` (如`C123456_front.jpg`)
   - 优先级: `front > blank > package > back > 无`

8. **PDF命名与开关**
   - 命名规则: `产品编号.pdf` (如`C123456789.pdf`)
   - 全局配置: `ENABLE_PDF_DOWNLOAD` (默认False)

### P1 - 新增模块与体验优化 (Major / Feature)
**优先级说明**: 在P0问题解决后,立即着手开发

1. **图片链接管理独立模块**
   - 独立CRUD页面
   - 数据模型: One-to-Many关系(一张图片 → N个店铺 → N个链接)
   - 导入功能: 支持Excel导入

### P2 - 高级导出独立模块 (Enhancement)
**优先级说明**: 增强功能,提升用户体验

1. **高级导出独立模块**
   - 独立配置页面
   - 筛选维度: 店铺选择、产品选择、价格折扣设置
   - 导出内容: 聚合数据(店铺运费模板、分类码、自定义图片链接、基础产品信息)

---

## 八、开发流程规范

### 1. 新功能开发流程
1. **需求分析**: 明确功能需求和业务逻辑
2. **数据库设计**: 设计表结构,编写SQL脚本
3. **后端开发**: Entity → Mapper → Service → Controller
4. **前端开发**: Types → API → Components → Views
5. **联调测试**: 前后端联调,修复bug
6. **代码审查**: 检查代码质量、安全性
7. **文档更新**: 更新功能列表、API文档

### 2. Bug修复流程
1. **问题定位**: 通过日志、调试定位问题
2. **根因分析**: 分析问题根本原因
3. **修复方案**: 设计修复方案
4. **代码修改**: 修改代码并测试
5. **回归测试**: 确保修复不引入新问题

### 3. 代码提交规范
```
<type>(<scope>): <subject>

<body>

<footer>
```

**Type类型**:
- `feat`: 新功能
- `fix`: Bug修复
- `refactor`: 重构
- `perf`: 性能优化
- `style`: 代码格式调整
- `docs`: 文档更新
- `test`: 测试相关
- `chore`: 构建/工具链相关

**示例**:
```
feat(crawler): 支持三级分类爬取

- 修改CategoryCrawlerService,支持递归遍历三级分类
- 更新数据库schema,添加category_level3_codes表
- 前端增加三级分类选择器组件

Closes #123
```

---

## 九、安全规范

### 1. 后端安全
- **SQL注入防护**: 使用MyBatis的`#{}`参数绑定
- **XSS防护**: 输出HTML时进行转义
- **CSRF防护**: 使用Spring Security的CSRF保护
- **权限控制**: 重要接口添加权限验证
- **敏感信息**: 密码、密钥不能硬编码,使用配置文件或环境变量

### 2. 前端安全
- **输入验证**: 所有用户输入需要验证
- **XSS防护**: 使用`v-text`而非`v-html`
- **HTTPS**: 生产环境强制使用HTTPS
- **敏感数据**: 不在localStorage存储敏感信息

### 3. 爬虫安全
- **限流保护**: 避免请求过快被封IP
- **User-Agent**: 使用合法的User-Agent
- **错误处理**: 捕获异常,避免程序崩溃
- **数据验证**: 验证爬取数据的有效性

---

## 十、性能优化规范

### 1. 后端性能优化
- **数据库优化**: 添加索引、优化SQL查询
- **缓存策略**: Redis缓存热点数据
- **批量操作**: 使用批处理减少数据库交互
- **异步处理**: 耗时操作使用异步执行
- **连接池**: 合理配置数据库连接池和HTTP连接池

### 2. 前端性能优化
- **懒加载**: 图片、组件按需加载
- **虚拟滚动**: 长列表使用虚拟滚动
- **防抖节流**: 搜索、滚动等使用防抖节流
- **代码分割**: 路由级别的代码分割
- **CDN加速**: 静态资源使用CDN

### 3. 爬虫性能优化
- **并发控制**: 合理设置线程池大小
- **批量处理**: 批量保存数据到数据库
- **内存优化**: 流式处理大数据量
- **连接复用**: HTTP连接池复用连接

---

## 十一、测试规范

### 1. 单元测试
- **覆盖率**: Service层代码覆盖率>80%
- **测试框架**: JUnit 5 + Mockito (后端), Vitest (前端)
- **Mock数据**: 使用Mock对象模拟依赖

### 2. 集成测试
- **数据库测试**: 使用H2内存数据库
- **API测试**: 使用MockMvc测试Controller
- **E2E测试**: 使用Playwright/Cypress

### 3. 爬虫测试
- **单分类测试**: 先测试单个分类爬取
- **小批量测试**: 测试批量爬取逻辑
- **异常测试**: 测试网络异常、数据异常等场景

---

## 十二、日志规范

### 1. 日志级别
- **ERROR**: 系统错误,需要立即处理
- **WARN**: 警告信息,可能存在问题
- **INFO**: 重要业务流程
- **DEBUG**: 调试信息

### 2. 日志内容
```java
// 好的日志
log.info("开始爬取分类[{}],catalogId={}", categoryName, catalogId);
log.error("爬取产品失败,productCode={}, error={}", productCode, e.getMessage(), e);

// 不好的日志
log.info("开始爬取");  // 缺少关键信息
log.error("错误");      // 没有错误上下文
```

### 3. 日志存储
- **控制台输出**: 开发环境
- **文件输出**: 生产环境,按日期切割
- **日志归档**: 保留最近30天日志

---

## 十三、文档规范

### 1. 代码注释
```java
/**
 * 爬取指定分类的产品数据
 *
 * @param catalogId 分类ID
 * @param pageSize 每页大小
 * @return 产品列表
 * @throws CrawlerException 爬取失败时抛出
 */
public List<Product> crawlProducts(String catalogId, int pageSize) {
    // 实现逻辑
}
```

### 2. API文档
- **Swagger**: 后端使用Swagger生成API文档
- **注释**: 所有公开API添加注释说明

### 3. README文档
- **项目介绍**: 项目背景、技术栈
- **快速开始**: 环境配置、启动步骤
- **功能列表**: 主要功能说明
- **开发指南**: 开发规范、目录结构

---

## 十四、监控与运维

### 1. 系统监控
- **健康检查**: `/actuator/health`端点
- **性能指标**: CPU、内存、线程池
- **业务指标**: 爬取成功率、数据量统计

### 2. 日志监控
- **错误日志**: 监控ERROR级别日志
- **慢查询**: 监控SQL慢查询
- **爬虫异常**: 监控爬虫失败率

### 3. 告警机制
- **系统告警**: CPU>80%、内存>90%
- **业务告警**: 爬取成功率<70%
- **错误告警**: ERROR日志频繁出现

---

## 十五、版本管理

### 1. 分支策略
- **master**: 生产环境分支
- **develop**: 开发分支
- **feature/xxx**: 功能分支
- **bugfix/xxx**: Bug修复分支
- **hotfix/xxx**: 紧急修复分支

### 2. 版本号规范
- **格式**: `v主版本.次版本.修订号` (如`v1.2.3`)
- **主版本**: 重大功能变更
- **次版本**: 新增功能
- **修订号**: Bug修复

### 3. 发布流程
1. 代码合并到develop分支
2. 测试通过后合并到master
3. 打tag标记版本号
4. 部署到生产环境
5. 监控系统运行状态

---

## 十六、常见问题处理

### 1. 爬虫相关
**问题**: 爬取失败率高
**解决**: 检查限流设置、网络状态、重试次数

**问题**: 数据重复
**解决**: 检查唯一索引、爬虫幂等性

**问题**: 内存溢出
**解决**: 使用批量处理、流式处理、调整JVM参数

### 2. 数据库相关
**问题**: 查询慢
**解决**: 添加索引、优化SQL、分表分库

**问题**: 连接池耗尽
**解决**: 增加连接池大小、检查连接泄漏

### 3. 前端相关
**问题**: 页面卡顿
**解决**: 虚拟滚动、懒加载、防抖节流

**��题**: 白屏
**解决**: 检查路由配置、API调用、控制台错误

---

## 十七、开发工具推荐

### 1. 后端工具
- **IDE**: IntelliJ IDEA Ultimate
- **数据库工具**: DataGrip、Navicat
- **API测试**: Postman、Apifox
- **性能分析**: JProfiler、VisualVM

### 2. 前端工具
- **IDE**: VSCode + Volar插件
- **调试工具**: Vue DevTools
- **性能分析**: Chrome DevTools
- **包管理**: pnpm (推荐)

### 3. 通用工具
- **版本管理**: Git + GitHub/GitLab
- **接口文档**: Swagger、Apifox
- **团队协作**: 飞书、钉钉
- **项目管理**: Jira、禅道

---

## 十八、学习资源

### 1. 官方文档
- **Spring Boot**: https://spring.io/projects/spring-boot
- **Vue 3**: https://cn.vuejs.org/
- **MyBatis Plus**: https://baomidou.com/
- **Ant Design Vue**: https://antdv.com/

### 2. 技术社区
- **掘金**: https://juejin.cn/
- **Stack Overflow**: https://stackoverflow.com/
- **GitHub**: https://github.com/

---

## 总结

本规则文档是立创商城爬虫系统的开发指南,涵盖了架构设计、代码规范、安全性、性能优化等方方面面。所有开发工作必须严格遵守本规则,确保代码质量和系统稳定性。

**核心原则**: 代码简洁、逻辑清晰、安全可靠、性能优异

---

## 十九、P0功能完成记录与经验总结

### ✅ 已完成功能 (2025-11-21)

#### P0-1: 爬虫选区UI升级
**状态**: ✅ 已完成

**实现内容**:
- 新增`CategoryTreeSelector.vue`组件，支持一级/二级/三级树形折叠结构
- DashboardV3.vue集成树形选择器
- 默认状态：一级分类默认不全选，避免误操作

**关键文件**:
- `lcsc-frontend/src/components/CategoryTreeSelector.vue`
- `lcsc-frontend/src/views/DashboardV3.vue`

---

#### P0-2: 爬虫选择记忆功能
**状态**: ✅ 已完成

**实现内容**:
- 使用localStorage保存用户选择的分类ID
- 页面加载时自动恢复上次选择
- 新增"清除记忆"按钮

**关键文件**:
- `lcsc-frontend/src/views/DashboardV3.vue` (STORAGE_KEY常量、handleTreeSelectionChange方法)

**⚠️ 遇到的Bug及修复**:
- **问题**: 重启后端后，之前保存的分类无法创建爬虫任务
- **根因**: 分类同步使用DELETE+INSERT导致自增ID重置，前端保存的旧ID失效
- **修复方案**:
  1. 给`catalog_id`字段添加UNIQUE索引
  2. CategorySyncService改用UPSERT模式（基于catalog_id判断是否存在）
- **教训**: **业务标识符(catalog_id)应该有唯一索引，数据同步应使用UPSERT而非DELETE+INSERT**

---

#### P0-3: 三级分类兼容性修复
**状态**: ✅ 已完成

**实现内容**:
- 数据库新增`category_level3_codes`表
- CategorySyncService支持递归解析三级分类
- CrawlerTaskQueueService智能识别分类级别（二级/三级）
- CategoryCrawlerWorkerPool支持三级分类爬取
- Product实体新增`category_level3_id`和`category_level3_name`字段
- 前端CategoryManagement.vue新增三级分类管理Tab
- ProductService.enrichCategoryNames方法支持三级分类名称填充

**关键文件**:
- `lcsc-crawler/src/main/java/com/lcsc/entity/CategoryLevel3Code.java`
- `lcsc-crawler/src/main/java/com/lcsc/service/crawler/v3/CategorySyncService.java`
- `lcsc-crawler/src/main/java/com/lcsc/service/crawler/v3/CrawlerTaskQueueService.java`
- `lcsc-crawler/src/main/java/com/lcsc/service/crawler/v3/CategoryCrawlerWorkerPool.java`
- `lcsc-crawler/src/main/java/com/lcsc/entity/Product.java`
- `lcsc-frontend/src/views/CategoryManagement.vue`

**数据库变更**:
```sql
-- 新增三级分类表
CREATE TABLE category_level3_codes (...)

-- products表新增字段
ALTER TABLE products ADD COLUMN category_level3_id INT;
ALTER TABLE products ADD COLUMN category_level3_name VARCHAR(100);

-- 分类表添加catalog_id唯一索引
ALTER TABLE category_level1_codes ADD UNIQUE KEY uk_catalog_id (catalog_id);
ALTER TABLE category_level2_codes ADD UNIQUE KEY uk_catalog_id (catalog_id);
```

---

### 🐛 Bug修复记录

#### Bug-1: 产品图片渲染显示占位符
**问题描述**: 产品管理页面部分产品图片无法渲染，显示SVG占位符

**根因分析**:
- 数据库中`product_image_url_big`字段值为`"https:null"`
- 立创API返回的某些产品图片URL字段是字符串`"null"`
- `normalizeAssetUrl`方法未过滤这种情况，直接拼接成`"https:null"`

**修复方案**:
```java
// CategoryCrawlerWorkerPool.java - normalizeAssetUrl方法
// 过滤掉包含"null"字符串的URL
if (trimmed.equalsIgnoreCase("null") || trimmed.contains(":null")) {
    return null;
}
```

**数据修复SQL**:
```sql
UPDATE products
SET product_image_url_big = NULL, image_name = NULL
WHERE product_image_url_big LIKE '%:null%' OR product_image_url_big = 'null';
```

**教训**: **处理外部API数据时，要考虑各种边界情况，包括字符串"null"**

---

### 📝 开发经验总结

#### 1. 数据同步设计原则
- **使用UPSERT而非DELETE+INSERT**: 保持主键ID稳定，避免关联数据失效
- **业务标识符添加唯一索引**: 如`catalog_id`，便于基于业务ID查询和更新
- **区分自增ID和业务ID**: 自增ID用于内部关联，业务ID用于外部标识

#### 2. 外部API数据处理
- **字符串"null"检查**: API可能返回字符串"null"而非null对象
- **URL格式验证**: 检查URL是否有效，避免无效URL进入数据库
- **数据清洗**: 在保存前对数据进行清洗和验证

#### 3. 前端状态持久化
- **localStorage保存业务ID**: 如果保存数据库自增ID，需确保后端ID稳定
- **状态恢复验证**: 恢复状态时验证数据有效性，处理数据失效情况

#### 4. 数据库Schema变更
- **SQL脚本统一管理**: 所有变更写入`lcsc_full_schema.sql`
- **数据库操作由用户手动执行**: 避免自动执行敏感操作
- **考虑数据迁移**: 新增字段时考虑现有数据的兼容性

---

### 📋 待完成功能 (P0剩余)

| 序号 | 功能 | 状态 | 备注 |
|-----|------|------|------|
| P0-4 | 突破5000条列表限制 | ⏳ 待开发 | 通过筛选参数切片 |
| P0-5 | 分类名称持久化修复 | ⏳ 待开发 | source_name + custom_name |
| P0-6 | 价格阶梯扩展(6级) | ⏳ 待开发 | 数据库+解析逻辑 |
| P0-7 | 图片命名与逻辑重构 | ⏳ 待开发 | 编号_图类.jpg |
| P0-8 | PDF命名与开关 | ⏳ 待开发 | 产品编号.pdf + 开关配置 |

---

*最后更新时间: 2025-11-21*
*文档版本: v1.1*
