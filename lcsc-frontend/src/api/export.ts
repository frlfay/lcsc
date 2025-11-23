import { api } from '@/utils/request'

/**
 * 高级导出请求DTO - 淘宝CSV格式
 */
export interface AdvancedExportRequest {
  shopId: number                    // 选择的店铺ID（单选，必填）
  categoryLevel1Id?: number         // 一级分类ID（可选）
  categoryLevel2Id?: number         // 二级分类ID（可选）
  categoryLevel3Id?: number         // 三级分类ID（可选）
  brand?: string                    // 品牌名称（可选，单选）
  hasImage?: boolean                // 是否有图片（可选：true/false/null）
  stockMin?: number                 // 库存最小值（可选）
  stockMax?: number                 // 库存最大值（可选）
  discounts: number[]               // 6级价格折扣配置（百分比，必填，例如：[90, 88, 85, 82, 80, 78]）
}

/**
 * 导出任务项
 */
export interface ExportTaskItem {
  productCode: string               // 产品编号（唯一标识）
  model: string                     // 产品型号
  brand: string                     // 品牌名称
  shopId: number                    // 关联的店铺ID
  shopName: string                  // 店铺名称（用于前端显示）
  discounts: number[]               // 6级价格折扣配置
  addedAt: number                   // 添加时间戳
}

/**
 * 添加任务请求
 */
export interface AddTaskRequest {
  shopId: number
  categoryLevel1Id?: number
  categoryLevel2Id?: number
  categoryLevel3Id?: number
  brand?: string
  hasImage?: boolean
  stockMin?: number
  stockMax?: number
  discounts: number[]
  currentTasks: ExportTaskItem[]    // 当前任务列表
}

/**
 * 添加产品到任务列表（批量添加模式）
 */
export const addToTaskList = (request: AddTaskRequest): Promise<ExportTaskItem[]> => {
  return api.post('/export/add-task', request)
}

/**
 * 导出任务列表为淘宝Excel格式
 */
export const exportTaobaoExcel = async (tasks: ExportTaskItem[]): Promise<void> => {
  const response = await api.post('/export/export-taobao-excel', tasks, {
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
  const now = new Date()
  const dateStr = now.toISOString().slice(0, 10).replace(/-/g, '') + '_' +
                  now.toTimeString().slice(0, 8).replace(/:/g, '')
  link.download = `${dateStr}.xlsx`

  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
