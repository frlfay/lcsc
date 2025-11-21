import { api } from '@/utils/request'
import type { ImageLink, PageResult, ImageLinkImportResult } from '@/types'

// 分页查询图片链接
export const getImageLinkPage = (params: {
  current?: number
  size?: number
  imageName?: string
  shopId?: number
  imageLink?: string
}): Promise<PageResult<ImageLink>> => {
  return api.get('/image-links/page', { params })
}

// 根据店铺ID查询图片链接列表
export const getImageLinkListByShopId = (shopId: number): Promise<ImageLink[]> => {
  return api.get(`/image-links/shop/${shopId}`)
}

// 根据图片名称查询所有店铺的链接
export const getImageLinkListByImageName = (imageName: string): Promise<ImageLink[]> => {
  return api.get(`/image-links/image/${imageName}`)
}

// 根据ID查询图片链接
export const getImageLinkById = (id: number): Promise<ImageLink> => {
  return api.get(`/image-links/${id}`)
}

// 新增图片链接
export const addImageLink = (data: ImageLink): Promise<void> => {
  return api.post('/image-links', data)
}

// 更新图片链接
export const updateImageLink = (id: number, data: ImageLink): Promise<void> => {
  return api.put(`/image-links/${id}`, data)
}

// 删除图片链接
export const deleteImageLink = (id: number): Promise<void> => {
  return api.delete(`/image-links/${id}`)
}

// 批量删除图片链接
export const deleteImageLinkBatch = (ids: number[]): Promise<void> => {
  return api.delete('/image-links/batch', { data: ids })
}

// 批量保存或更新图片链接
export const saveOrUpdateImageLinkBatch = (imageLinks: ImageLink[]): Promise<void> => {
  return api.post('/image-links/batch', imageLinks)
}

// 统计指定店铺的图片数量
export const countImageLinkByShopId = (shopId: number): Promise<number> => {
  return api.get(`/image-links/shop/${shopId}/count`)
}

// 删除指定店铺的所有图片链接
export const deleteImageLinkByShopId = (shopId: number): Promise<void> => {
  return api.delete(`/image-links/shop/${shopId}`)
}

// 导入Excel文件
export const importImageLinksFromExcel = (file: File): Promise<ImageLinkImportResult> => {
  const formData = new FormData()
  formData.append('file', file)

  return api.post('/image-links/import', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 下载导入模板
export const downloadImportTemplate = (): void => {
  const url = `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}/api/image-links/import-template`
  const link = document.createElement('a')
  link.href = url
  link.download = 'image_link_import_template.xlsx'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}
