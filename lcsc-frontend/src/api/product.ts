import { api } from '@/utils/request'
import request from '@/utils/request'
import type {
  Product,
  PageResult,
  ProductSearchParams,
  Statistics,
  ResourceFile,
  ProductResources
} from '@/types'

// 分页查询产品
export const getProductPage = (params: ProductSearchParams): Promise<PageResult<Product>> => {
  return api.get<PageResult<Product>>('/products/page', { params })
}

// 根据ID查询产品
export const getProductById = (id: number): Promise<Product> => {
  return api.get<Product>(`/products/${id}`)
}

// 根据产品编号查询产品
export const getProductByCode = (productCode: string): Promise<Product> => {
  return api.get<Product>(`/products/code/${productCode}`)
}

// 新增产品
export const addProduct = (data: Product): Promise<string> => {
  return api.post<string>('/products', data)
}

// 更新产品
export const updateProduct = (id: number, data: Product): Promise<string> => {
  return api.put<string>(`/products/${id}`, data)
}

// 删除产品
export const deleteProduct = (id: number): Promise<string> => {
  return api.delete<string>(`/products/${id}`)
}

// 批量删除产品
export const deleteProductBatch = (ids: number[]): Promise<string> => {
  return api.delete<string>('/products/batch', { data: ids })
}

// 根据品牌查询产品列表
export const getProductListByBrand = (brand: string): Promise<Product[]> => {
  return api.get<Product[]>(`/products/brand/${brand}`)
}

// 根据分类查询产品列表
export const getProductListByCategory = (params: {
  categoryLevel1Id?: number
  categoryLevel2Id?: number
}): Promise<Product[]> => {
  return api.get<Product[]>('/products/category', { params })
}

// 产品统计信息
export const getProductStatistics = (): Promise<Statistics> => {
  return api.get<Statistics>('/products/statistics')
}

// 批量保存或更新产品
export const saveOrUpdateProductBatch = (products: Product[]): Promise<string> => {
  return api.post<string>('/products/batch', products)
}

// 获取产品图片列表
export const getProductImages = (productCode: string): Promise<ResourceFile[]> => {
  return api.get<ResourceFile[]>(`/products/${productCode}/images`)
}

// 获取产品PDF列表
export const getProductPdfs = (productCode: string): Promise<ResourceFile[]> => {
  return api.get<ResourceFile[]>(`/products/${productCode}/pdfs`)
}

// 获取产品所有资源（图片+PDF）
export const getProductResources = (productCode: string): Promise<ProductResources> => {
  return api.get<ProductResources>(`/products/${productCode}/resources`)
}

// 爬取单个产品（这些API需要爬虫模块实现）
export const crawlProduct = (productCode: string): Promise<void> => {
  return request.post('/crawler/crawl-single', { productCode })
}

// 批量爬取产品
export const crawlProductBatch = (productCodes: string[]): Promise<void> => {
  return request.post('/crawler/crawl-batch', { productCodes })
}

// --- 产品导出 API ---

// 导出所有产品到Excel
export const exportAllProductsExcel = () => {
  return request.get<{
    code: number
    message: string
    data: {
      filename: string
      recordCount: number
      message: string
    }
  }>('/products/export/excel/all')
}

// 根据搜索条件导出产品到Excel
export const exportProductsExcel = (params: {
  categoryLevel1Id?: number
  categoryLevel2Id?: number
  brand?: string
  productCode?: string
  model?: string
  hasStock?: boolean
}) => {
  return request.post<{
    code: number
    message: string
    data: {
      filename: string
      recordCount: number
      message: string
    }
  }>('/products/export/excel', params)
}

// 导出产品到CSV
export const exportProductsCSV = (params: {
  categoryLevel1Id?: number
  categoryLevel2Id?: number
  brand?: string
  productCode?: string
  model?: string
  hasStock?: boolean
}) => {
  return request.post<{
    code: number
    message: string
    data: {
      filename: string
      recordCount: number
      message: string
    }
  }>('/products/export/csv', params)
}

// 下载导出文件
export const downloadExportFile = (filename: string) => {
  const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
  const url = `${API_BASE_URL}/api/products/export/download/${filename}`
  window.open(url, '_blank')
}

// --- V3 爬虫相关 API ---

// 获取系统状态
export const getSystemStatus = () => {
  return api.get<{
    code: number
    message: string
    data: {
      categoriesSynced: boolean
      isRunning: boolean
      queueStatus: {
        pending: number
        processing: number
        completed: number
        failed: number
        total: number
      }
      categoryStats: {
        level1Count: number
        level2Count: number
      }
      workerThreadCount: number
      totalProductsInDb: number
    }
  }>('/v3/crawler/status')
}

// 同步分类
export const syncCategories = () => {
  return request.post('/v3/crawler/sync-categories')
}

// 全量爬取
export const startFullCrawl = () => {
  return request.post('/v3/crawler/start-full')
}

// 批量爬取指定分类
export const startBatchCrawl = (catalogIds: number[]) => {
  return request.post('/v3/crawler/start-batch', catalogIds)
}

// 停止爬虫
export const stopCrawler = () => {
  return request.post('/v3/crawler/stop')
}

// 获取所有分类及爬取状态（仅已爬取的分类）
export const getCategoriesWithStatus = () => {
  return api.get<{
    code: number
    message: string
    data: Array<{
      id: number
      categoryLevel1Id: number
      categoryLevel2Name: string
      crawlStatus: 'pending' | 'processing' | 'completed' | 'failed'
      crawlProgress: number
      lastCrawlTime: string
      totalProducts: number
      errorMessage: string
    }>
  }>('/v3/crawler/categories-with-status')
}

// 获取所有分类（包括未爬取的，用于分类选择器）
export const getAllCategories = () => {
  return api.get<Array<{
    id: number
    categoryLevel1Id: number
    categoryLevel2Name: string
    crawlStatus: 'pending' | 'not_started' | 'processing' | 'completed' | 'failed'
    crawlProgress: number
    lastCrawlTime: string | null
    totalProducts: number
    errorMessage: string | null
  }>>('/v3/crawler/all-categories')
}

// 按分类导出
export const exportProductsByCategories = (categoryIds: number[], format: 'excel' | 'csv') => {
  return api.post<{
    filename: string
    recordCount: number
    message: string
  }>('/products/export/by-categories', {
    categoryIds,
    format
  })
}

// 获取存储路径信息
export const getStoragePaths = () => {
  return api.get<{
    code: number
    message: string
    data: {
      basePath: string
      paths: {
        images: {
          path: string
          relativePath: string
          exists: boolean
          fileCount: number
          sizeBytes: number
          sizeMB: number
        }
        pdfs: {
          path: string
          relativePath: string
          exists: boolean
          fileCount: number
          sizeBytes: number
          sizeMB: number
        }
        data: {
          path: string
          relativePath: string
          exists: boolean
        }
        exports: {
          path: string
          relativePath: string
          exists: boolean
        }
      }
      saveImages: boolean
      config: {
        storageBasePath: string
        imageDir: string
        pdfDir: string
        dataDir: string
        exportDir: string
      }
    }
  }>('/v3/crawler/storage-paths')
}
