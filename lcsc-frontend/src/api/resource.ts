import { api } from '@/utils/request'

export interface ResourceFolder {
  productCode: string
  imageCount: number
  pdfCount: number
  lastModified: number
}

export interface PaginatedResponse<T> {
  records: T[]
  total: number
  current: number
  size: number
}

/**
 * 资源浏览器API
 */
export const resourceApi = {
  /**
   * 分页获取产品资源文件夹
   */
  getFolders(current: number, size: number): Promise<PaginatedResponse<ResourceFolder>> {
    return api.get<PaginatedResponse<ResourceFolder>>('/resources/folders', {
      params: { current, size }
    })
  }
}
