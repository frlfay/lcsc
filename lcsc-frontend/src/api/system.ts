import { api } from '@/utils/request'

// 系统健康检查
export const getSystemHealth = (): Promise<string> => {
  return api.get('/system/health')
}

// 获取系统统计信息
export const getSystemStatistics = (): Promise<{
  totalProducts: number
  productsWithStock: number
  productsWithoutStock: number
  totalLevel1Categories: number
  totalLevel2Categories: number
  totalShops: number
  totalImageLinks: number
}> => {
  return api.get('/system/statistics')
}

// 获取API接口列表
export const getApiList = (): Promise<Record<string, any>> => {
  return api.get('/system/apis')
}
