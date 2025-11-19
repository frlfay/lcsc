import { api } from '@/utils/request'
import type { CategoryLevel1Code, CategoryLevel2Code, PageResult } from '@/types'

// ========== 一级分类 API ==========

// 分页查询一级分类
export const getCategoryLevel1Page = (params: {
  current?: number
  size?: number
  categoryName?: string
  categoryCode?: string
}): Promise<PageResult<CategoryLevel1Code>> => {
  return api.get('/categories/level1/page', { params })
}

// 获取所有一级分类列表
export const getCategoryLevel1List = (): Promise<CategoryLevel1Code[]> => {
  return api.get('/categories/level1/list')
}

// 根据ID查询一级分类
export const getCategoryLevel1ById = (id: number): Promise<CategoryLevel1Code> => {
  return api.get(`/categories/level1/${id}`)
}

// 新增一级分类
export const addCategoryLevel1 = (data: CategoryLevel1Code): Promise<void> => {
  return api.post('/categories/level1', data)
}

// 更新一级分类
export const updateCategoryLevel1 = (id: number, data: CategoryLevel1Code): Promise<void> => {
  return api.put(`/categories/level1/${id}`, data)
}

// 删除一级分类
export const deleteCategoryLevel1 = (id: number): Promise<void> => {
  return api.delete(`/categories/level1/${id}`)
}

// 批量删除一级分类
export const deleteCategoryLevel1Batch = (ids: number[]): Promise<void> => {
  return api.delete('/categories/level1/batch', { data: ids })
}

// ========== 二级分类 API ==========

// 分页查询二级分类
export const getCategoryLevel2Page = (params: {
  current?: number
  size?: number
  categoryName?: string
  categoryLevel1Id?: number
}): Promise<PageResult<CategoryLevel2Code>> => {
  return api.get('/categories/level2/page', { params })
}

// 根据一级分类ID查询二级分类列表
export const getCategoryLevel2ListByLevel1Id = (categoryLevel1Id: number): Promise<CategoryLevel2Code[]> => {
  return api.get(`/categories/level2/list/${categoryLevel1Id}`)
}

// 根据ID查询二级分类
export const getCategoryLevel2ById = (id: number): Promise<CategoryLevel2Code> => {
  return api.get(`/categories/level2/${id}`)
}

// 新增二级分类
export const addCategoryLevel2 = (data: CategoryLevel2Code): Promise<void> => {
  return api.post('/categories/level2', data)
}

// 更新二级分类
export const updateCategoryLevel2 = (id: number, data: CategoryLevel2Code): Promise<void> => {
  return api.put(`/categories/level2/${id}`, data)
}

// 删除二级分类
export const deleteCategoryLevel2 = (id: number): Promise<void> => {
  return api.delete(`/categories/level2/${id}`)
}

// 批量删除二级分类
export const deleteCategoryLevel2Batch = (ids: number[]): Promise<void> => {
  return api.delete('/categories/level2/batch', { data: ids })
}

// 获取店铺分类码
export const getShopCategoryCode = (categoryLevel2Id: number, shopId: number): Promise<string> => {
  return api.get(`/categories/level2/${categoryLevel2Id}/shop/${shopId}/code`)
}

// 更新店铺分类码
export const updateShopCategoryCode = (categoryLevel2Id: number, shopId: number, categoryCode: string): Promise<void> => {
  return api.put(`/categories/level2/${categoryLevel2Id}/shop/${shopId}/code`, { categoryCode })
}
