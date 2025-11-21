import { api } from '@/utils/request'

export interface AdvancedExportRequest {
  shopIds?: number[]
  categoryIds?: number[]
  brands?: string[]
  productCodes?: string[]
  keyword?: string
  discountRate?: number
  includeImageLinks?: boolean
  includeLadderPrices?: boolean
}

export interface ExportPreview {
  productCount: number
  shopCount: number
  shops: string[]
  categoryCount: number
  brandCount: number
}

// 获取导出预览
export const getExportPreview = (request: AdvancedExportRequest): Promise<ExportPreview> => {
  return api.post('/export/preview', request)
}

// 执行高级导出
export const exportAdvanced = async (request: AdvancedExportRequest): Promise<void> => {
  const response = await api.post('/export/advanced', request, {
    responseType: 'blob'
  })

  // 创建下载链接
  const blob = new Blob([response as any], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url

  // 生成文件名
  const date = new Date().toISOString().slice(0, 10).replace(/-/g, '')
  link.download = `产品导出_${date}.xlsx`

  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
