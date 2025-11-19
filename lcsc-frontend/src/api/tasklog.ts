import { api } from '@/utils/request'

export interface TaskLog {
  id: number
  taskId: string
  level: string
  step: string
  message: string
  progress: number | null
  extraData: string | null
  createTime: string
}

export interface TaskLogPageParams {
  page?: number
  size?: number
  taskId?: string
  level?: string
}

export interface TaskLogPageResult {
  records: TaskLog[]
  total: number
  size: number
  current: number
  pages: number
}

export interface TaskProgressInfo {
  taskId: string
  progress: number
  latestStep?: string
  latestMessage?: string
  latestTime?: string
}

export interface TaskLogStats {
  activeTasksCount: number
  totalLogs: string | number
  errorCount: string | number
  successCount: string | number
}

/**
 * 分页查询任务日志 (从Redis)
 */
export function getTaskLogsPage(params: TaskLogPageParams = {}) {
  return api.get<TaskLogPageResult>('/task-logs/page', { params })
}

/**
 * 从数据库分页查询任务日志 (实时更新使用)
 */
export function getTaskLogsFromDB(params: TaskLogPageParams = {}) {
  return api.get<TaskLogPageResult>('/task-logs/db/page', { params })
}

/**
 * 获取指定任务的最新日志
 */
export function getLatestTaskLogs(taskId: string, limit: number = 50) {
  return api.get<TaskLog[]>(`/task-logs/latest/${taskId}`, {
    params: { limit }
  })
}

/**
 * 获取任务当前进度
 */
export function getTaskProgress(taskId: string) {
  return api.get<TaskProgressInfo>(`/task-logs/progress/${taskId}`)
}

/**
 * 获取所有活跃任务的状态
 */
export function getActiveTasksStatus() {
  return api.get<Record<string, TaskLog>>('/task-logs/active-tasks')
}

/**
 * 手动记录任务日志（用于测试）
 */
export function logTask(logData: {
  taskId: string
  level?: string
  step?: string
  message: string
  progress?: number
}) {
  return api.post<string>('/task-logs/log', logData)
}

/**
 * 清理过期日志
 */
export function cleanupExpiredLogs(daysToKeep: number = 7) {
  return api.post<{
    deletedCount: number
    daysToKeep: number
    message: string
  }>('/task-logs/cleanup', null, {
    params: { daysToKeep }
  })
}

/**
 * 获取任务日志统计信息
 */
export function getTaskLogStats() {
  return api.get<TaskLogStats>('/task-logs/stats')
}

/**
 * 根据任务ID删除日志
 */
export function deleteTaskLogs(taskId: string) {
  return api.delete<string>(`/task-logs/${taskId}`)
}

// 常用的日志级别和步骤常量
export const LOG_LEVELS = {
  INFO: 'INFO',
  WARN: 'WARN', 
  ERROR: 'ERROR',
  DEBUG: 'DEBUG',
  SUCCESS: 'SUCCESS'
} as const

export const TASK_STEPS = {
  INIT: 'INIT',
  CRAWLING: 'CRAWLING',
  PARSING: 'PARSING', 
  SAVING: 'SAVING',
  EXPORTING: 'EXPORTING',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED',
  CANCELLED: 'CANCELLED'
} as const

export type LogLevel = typeof LOG_LEVELS[keyof typeof LOG_LEVELS]
export type TaskStep = typeof TASK_STEPS[keyof typeof TASK_STEPS]