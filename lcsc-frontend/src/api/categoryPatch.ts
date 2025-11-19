import { api } from '@/utils/request'

// 分类爬取结果类型
export interface CategoryCrawlResult {
  success: boolean
  totalLevel1Categories: number
  totalLevel2Categories: number
  createdLevel1: number
  createdLevel2: number
  processed: number
  failed: number
  skipped: number
  durationMs: number
  errorMessage?: string
}

// 分类组合类型
export interface CategoryPair {
  level1Name: string
  level2Name: string
}

// ========== 分类补丁 API ==========

// 一键补丁所有分类（从产品数据中提取）
export const patchAllCategoriesFromProducts = (): Promise<CategoryCrawlResult> => {
  return api.post('/category-crawler/patch-all-from-products', null, { 
    timeout: 300000 // 5分钟超时
  })
}

// 爬取官方完整分类
export const crawlAllCategories = (): Promise<CategoryCrawlResult> => {
  return api.post('/category-crawler/crawl-all', null, { 
    timeout: 300000 // 5分钟超时
  })
}

// 手动批量处理分类
export const patchProcessCategories = (categoryPairs: CategoryPair[]): Promise<CategoryCrawlResult> => {
  return api.post('/category-crawler/patch-process', categoryPairs, { 
    timeout: 180000 // 3分钟超时
  })
}

// 爬取指定一级分类
export const crawlLevel1Category = (categoryName: string): Promise<CategoryCrawlResult> => {
  return api.post('/category-crawler/crawl-level1', null, { 
    params: { categoryName },
    timeout: 120000 // 2分钟超时
  })
}

// 从产品数据中批量补丁（分批处理）
export const patchFromProducts = (batchSize: number = 1000, offset: number = 0): Promise<CategoryCrawlResult> => {
  return api.post('/category-crawler/patch-from-products', null, { 
    params: { batchSize, offset },
    timeout: 240000 // 4分钟超时
  })
}

// 手动添加单个分类
export const addSingleCategory = (level1Name: string, level2Name: string): Promise<string> => {
  return api.post('/category-crawler/add-single', null, { params: { level1Name, level2Name } })
}

// 获取分类统计信息
export const getCategoryStatistics = (): Promise<Record<string, any>> => {
  return api.get('/category-crawler/statistics')
}

// 获取爬虫状态
export const getCrawlerStatus = (): Promise<Record<string, any>> => {
  return api.get('/category-crawler/status')
}

// 清空分类缓存
export const clearCategoryCache = (): Promise<string> => {
  return api.post('/category-persistence/clear-cache')
}

// 获取缓存统计
export const getCacheStats = (): Promise<{ level1CacheSize: number, level2CacheSize: number }> => {
  return api.get('/category-persistence/cache-stats')
}