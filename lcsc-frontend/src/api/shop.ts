import { api } from '@/utils/request'
import type { Shop, PageResult } from '@/types'

// 分页查询店铺
export const getShopPage = (params: {
  current?: number
  size?: number
  shopName?: string
  shippingTemplateId?: string
}): Promise<PageResult<Shop>> => {
  return api.get('/shops/page', { params })
}

// 获取所有店铺列表
export const getShopList = (): Promise<Shop[]> => {
  return api.get('/shops/list')
}

// 根据ID查询店铺
export const getShopById = (id: number): Promise<Shop> => {
  return api.get(`/shops/${id}`)
}

// 根据店铺名称查询店铺
export const getShopByName = (shopName: string): Promise<Shop> => {
  return api.get(`/shops/name/${shopName}`)
}

// 根据运费模板ID查询店铺
export const getShopByTemplateId = (shippingTemplateId: string): Promise<Shop> => {
  return api.get(`/shops/template/${shippingTemplateId}`)
}

// 新增店铺
export const addShop = (data: Shop): Promise<void> => {
  return api.post('/shops', data)
}

// 更新店铺
export const updateShop = (id: number, data: Shop): Promise<void> => {
  return api.put(`/shops/${id}`, data)
}

// 删除店铺
export const deleteShop = (id: number): Promise<void> => {
  return api.delete(`/shops/${id}`)
}

// 批量删除店铺
export const deleteShopBatch = (ids: number[]): Promise<void> => {
  return api.delete('/shops/batch', { data: ids })
}

// 获取所有店铺列表（别名）
export const getAllShops = getShopList
