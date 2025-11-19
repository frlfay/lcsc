// 产品信息类型
export interface Product {
  id?: number
  productCode: string
  categoryLevel1Id: number
  categoryLevel2Id: number
  brand?: string
  model?: string
  packageName?: string
  pdfFilename?: string
  pdfLocalPath?: string
  imageName?: string
  imageLocalPath?: string
  totalStockQuantity?: number
  briefDescription?: string
  tierPrices?: string
  tierPricesLastUpdate?: string
  tierPricesManualEdit?: boolean
  detailedParameters?: string
  createdAt?: string
  updatedAt?: string
  lastCrawledAt?: string

  // 扩展字段
  categoryLevel1Name?: string
  categoryLevel2Name?: string
  productImageUrlBig?: string
  pdfUrl?: string
  ladderPrice1Quantity?: number
  ladderPrice1Price?: number
  ladderPrice2Quantity?: number
  ladderPrice2Price?: number
  ladderPrice3Quantity?: number
  ladderPrice3Price?: number
  ladderPrice4Quantity?: number
  ladderPrice4Price?: number
  ladderPrice5Quantity?: number
  ladderPrice5Price?: number
  parametersText?: string
  productImagesInfo?: string
  mainImageLocalPath?: string
  stockStatus?: 'IN_STOCK' | 'OUT_OF_STOCK' | 'LOW_STOCK'
  priceRange?: {
    min: number
    max: number
    currency: string
  }
  images?: string[]
  specifications?: Record<string, string>
  datasheetUrl?: string
}

// 店铺信息类型
export interface Shop {
  id?: number
  shopName: string
  shippingTemplateId: string
  createdAt?: string
  updatedAt?: string
}

// 一级分类类型
export interface CategoryLevel1Code {
  id?: number
  categoryLevel1Name: string
  categoryCode?: string
  createdAt?: string
  updatedAt?: string
}

// 二级分类类型
export interface CategoryLevel2Code {
  id?: number
  categoryLevel2Name: string
  categoryLevel1Id: number
  shopCategoryCodes?: string
  createdAt?: string
  updatedAt?: string
  // V3 爬虫相关字段
  crawlStatus?: 'pending' | 'processing' | 'completed' | 'failed'
  crawlProgress?: number
  lastCrawlTime?: string
  totalProducts?: number
  crawledProducts?: number
  currentPage?: number
  errorMessage?: string
  // 临时UI状态字段
  _crawling?: boolean
}

// 图片链接类型
export interface ImageLink {
  id?: number
  imageName: string
  shopId: number
  imageLink: string
  createdAt?: string
  updatedAt?: string
}

// 资源文件类型
export interface ResourceFile {
  filename: string
  url: string
  size: number
  lastModified: number
  category: 'image' | 'pdf'
  type: string
}

// 产品资源类型
export interface ProductResources {
  all: ResourceFile[]
  images: ResourceFile[]
  pdfs: ResourceFile[]
  total: number
}

// 资源链接类型（扩展图片链接，支持PDF等）
export interface ResourceLink {
  id?: number
  resourceName: string
  shopId: number
  resourceLink: string
  resourceType: 'image' | 'pdf' | 'document'
  fileExtension?: string
  createdAt?: string
  updatedAt?: string
}

// 分页结果类型
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// API响应类型
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

// 爬虫相关类型定义
export interface CrawlerTask {
  taskId: string
  taskType: 'PRODUCT_CRAWL' | 'CATALOG_CRAWL' | 'CATEGORY_CRAWL' | 'BATCH_CRAWL' | 'EXCEL_EXPORT'
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'STOPPED'
  createTime: string
  updateTime?: string
  details: Record<string, any>
  progress?: number
  message?: string
}

// 爬虫状态类型
export interface CrawlerStatus {
  status: 'running' | 'stopped' | 'error'
  runningTasks: number
  totalTasks: number
  canStartNewTask: boolean
}

// 爬虫配置类型
export interface CrawlerConfig {
  delay: number
  timeout: number
  maxRetry: number
  threadPoolSize: number
  autoRetry: boolean
  saveImages: boolean
  userAgent?: string
  headless?: boolean
}

// 任务统计类型
export interface TaskCounts {
  PENDING: number
  RUNNING: number
  COMPLETED: number
  FAILED: number
  STOPPED: number
  TOTAL: number
}

// 目录相关类型
export interface CatalogItem {
  catalogId: number
  catalogName: string
  catalogNameEn?: string
  parentId?: number
  level: number
  hasChildren: boolean
  children?: CatalogItem[]
}

// 产品搜索条件类型
export interface ProductSearchParams {
  current?: number
  size?: number
  productCode?: string
  brand?: string
  model?: string
  packageName?: string
  categoryLevel1Id?: number
  categoryLevel2Id?: number
  hasStock?: boolean
  sortBy?: 'createdAt' | 'updatedAt' | 'productCode' | 'brand'
  sortOrder?: 'asc' | 'desc'
}

// 统计信息类型
export interface Statistics {
  totalProducts: number
  productsWithStock: number
  productsWithoutStock: number
  totalCategories?: number
  totalBrands?: number
  lastCrawlTime?: string
}

// 表格列配置类型
export interface TableColumn {
  prop: string
  label: string
  width?: number
  minWidth?: number
  fixed?: boolean | 'left' | 'right'
  sortable?: boolean | 'custom'
  formatter?: (row: any, column: any, cellValue: any) => string
  type?: 'selection' | 'index' | 'expand'
}

// 表单验证规则类型
export interface FormRule {
  required?: boolean
  message?: string
  trigger?: 'blur' | 'change'
  min?: number
  max?: number
  pattern?: RegExp
  validator?: (rule: any, value: any, callback: Function) => void
}

// 通用列表查询参数
export interface ListQuery {
  current: number
  size: number
  keyword?: string
  [key: string]: any
}

// 文件上传相关类型
export interface UploadFile {
  name: string
  size: number
  type: string
  url?: string
  status?: 'ready' | 'uploading' | 'success' | 'fail'
  percentage?: number
}

// 导航菜单类型
export interface MenuItem {
  path: string
  name: string
  component?: string
  meta?: {
    title: string
    icon?: string
    requiresAuth?: boolean
    hidden?: boolean
  }
  children?: MenuItem[]
}

// WebSocket消息类型
export interface TaskLogMessage {
  id: number
  taskId: string
  level: string
  step: string
  message: string
  progress: number | null
  extraData: string | null
  createTime: string
}

export interface SystemStatusMessage {
  type: 'SYSTEM_STATUS'
  data: {
    isRunning: boolean
    isPaused: boolean
    queueStatus: {
      pending: number
      processing: number
      completed: number
      failed: number
      timestamp: string
    }
    timestamp: string
  }
}

export interface StatisticsMessage {
  type: 'STATISTICS'
  data: {
    totalTasks: number
    completedTasks: number
    failedTasks: number
    completionRate: number
    failureRate: number
    timestamp: string
  }
}

export interface ErrorMessage {
  type: 'ERROR'
  data: {
    taskId?: string
    errorType: string
    message: string
    timestamp: string
  }
}
