import axios, { AxiosRequestConfig, AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import type { ApiResponse } from '@/types'

// 从环境变量获取API地址
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

const request = axios.create({
  baseURL: `${API_BASE_URL}/api`,
  timeout: 60000, // 默认超时时间调整为60秒
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  <T>(response: AxiosResponse<ApiResponse<T>>): T => {
    const { data } = response
    
    // 检查是否为标准的API响应格式
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === 200) {
        // 如果 data.data 包含 success 字段，返回完整的 data.data 对象
        // 这是为了兼容爬虫控制器返回的自定义响应格式
        if (data.data && typeof data.data === 'object' && 'success' in data.data) {
          return data.data
        }
        return data.data
      } else {
        const errorMsg = data.message || '请求失败'
        message.error(errorMsg)
        throw new Error(errorMsg)
      }
    } 
    // 如果后端直接返回数据（没有包装格式）
    else if (response.status === 200) {
      return data as T
    } 
    // 其他情况视为错误
    else {
      const errorMsg = '请求失败'
      message.error(errorMsg)
      throw new Error(errorMsg)
    }
  },
  error => {
    let errorMsg = '网络错误'
    
    if (error.response) {
      // 服务器响应错误
      const { status, data } = error.response
      if (data && data.message) {
        errorMsg = data.message
      } else {
        switch (status) {
          case 400:
            errorMsg = '请求参数错误'
            break
          case 401:
            errorMsg = '未授权访问'
            break
          case 403:
            errorMsg = '禁止访问'
            break
          case 404:
            errorMsg = '请求的资源不存在'
            break
          case 500:
            errorMsg = '服务器内部错误'
            break
          default:
            errorMsg = `请求失败（${status}）`
        }
      }
    } else if (error.request) {
      // 网络连接错误或超时
      if (error.code === 'ECONNABORTED') {
        errorMsg = '请求超时，请稍后重试。大型操作可能需要较长时间处理。'
      } else {
        errorMsg = '网络连接失败，请检查网络连接'
      }
    }
    
    message.error(errorMsg)
    return Promise.reject(error)
  }
)

// 类型安全的请求方法
export const api = {
  get: <T = any>(url: string, config?: AxiosRequestConfig): Promise<T> => {
    return request.get(url, config)
  },
  
  post: <T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> => {
    return request.post(url, data, config)
  },
  
  put: <T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> => {
    return request.put(url, data, config)
  },
  
  delete: <T = any>(url: string, config?: AxiosRequestConfig): Promise<T> => {
    return request.delete(url, config)
  }
}

export default request
