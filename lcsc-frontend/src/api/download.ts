import { api } from '@/utils/request'

export interface DownloadStatus {
  isRunning: boolean
  pending: number
  processing: number
  completedImages: number
  completedPdfs: number
  failed: number
}

/**
 * 下载服务API
 */
export const downloadApi = {
  /**
   * 获取下载服务的实时状态
   */
  getStatus(): Promise<DownloadStatus> {
    return api.get<DownloadStatus>('/downloads/status')
  }
}
