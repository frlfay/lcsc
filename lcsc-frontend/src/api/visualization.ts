import { api } from '@/utils/request'

// 监控数据类型定义
export interface PerformanceMetrics {
  timestamp: string
  totalProducts: number
  crawlRate: number
  successRate: number
  errorRate: number
  avgResponseTime: number
  memoryUsage: number
  cpuUsage: number
  queueSize: number
  activeTaskCount: number
}

export interface ErrorDistribution {
  errorType: string
  count: number
  percentage: number
}

export interface CategoryStats {
  categoryName: string
  productCount: number
  crawlProgress: number
  avgSuccessRate: number
}

export interface MemoryStats {
  maxMemory: number
  totalMemory: number
  usedMemory: number
  freeMemory: number
  usageRatio: number
  heapUsed: number
  heapMax: number
  nonHeapUsed: number
  nonHeapMax: number
}

export interface TaskSchedulerStats {
  queueSize: number
  completedTasks: number
  runningTasks: number
  pendingTasks: number
  failedTasks: number
  averageExecutionTime: number
  throughput: number
  lastScheduleTime: number
}

export interface CrawlerMetrics {
  totalRequests: number
  successfulRequests: number
  failedRequests: number
  rateLimitHits: number
  averageResponseTime: number
  requestsPerSecond: number
  errorRates: Map<string, number>
  performanceHistory: PerformanceMetrics[]
}

export interface SystemHealthInfo {
  overall: 'HEALTHY' | 'WARNING' | 'CRITICAL'
  memoryStatus: string
  taskSchedulerStatus: string
  crawlerStatus: string
  databaseStatus: string
  redisStatus: string
  lastCheckTime: string
  details: Record<string, any>
}

// API基础路径
const API_BASE = '/v2/monitoring'

// 获取实时性能指标
export const getPerformanceMetrics = (): Promise<PerformanceMetrics> => {
  return api.get(`${API_BASE}/performance/current`)
}

// 获取历史性能数据
export const getPerformanceHistory = (timeRange: string = '1h'): Promise<PerformanceMetrics[]> => {
  return api.get(`${API_BASE}/performance/history`, {
    params: { timeRange }
  })
}

// 获取错误分布统计
export const getErrorDistribution = (timeRange: string = '24h'): Promise<ErrorDistribution[]> => {
  return api.get(`${API_BASE}/errors/distribution`, {
    params: { timeRange }
  })
}

// 获取分类爬取统计
export const getCategoryStatistics = (): Promise<CategoryStats[]> => {
  return api.get(`${API_BASE}/categories/stats`)
}

// 获取内存使用统计
export const getMemoryStatistics = (): Promise<MemoryStats> => {
  return api.get(`${API_BASE}/memory/stats`)
}

// 获取任务调度统计
export const getTaskSchedulerStatistics = (): Promise<TaskSchedulerStats> => {
  return api.get(`${API_BASE}/scheduler/stats`)
}

// 获取爬虫指标统计
export const getCrawlerMetrics = (): Promise<CrawlerMetrics> => {
  return api.get(`${API_BASE}/crawler/metrics`)
}

// 获取系统健康状态
export const getSystemHealth = (): Promise<SystemHealthInfo> => {
  return api.get(`${API_BASE}/health/status`)
}

// 获取系统关键指标概览
export const getSystemOverview = (): Promise<{
  totalProducts: number
  todayAdded: number
  activeTasks: number
  successRate: number
  avgResponseTime: number
  memoryUsage: number
  queueSize: number
  errorCount: number
}> => {
  return api.get(`${API_BASE}/overview`)
}

// 获取API响应时间统计
export const getApiResponseTimes = (timeRange: string = '1h'): Promise<{
  endpoint: string
  avgResponseTime: number
  minResponseTime: number
  maxResponseTime: number
  requestCount: number
  errorCount: number
}[]> => {
  return api.get(`${API_BASE}/api/response-times`, {
    params: { timeRange }
  })
}

// 获取数据库连接池统计
export const getDatabaseStats = (): Promise<{
  activeConnections: number
  idleConnections: number
  totalConnections: number
  maxConnections: number
  waitingRequests: number
  connectionUsageRate: number
}> => {
  return api.get(`${API_BASE}/database/stats`)
}

// 获取Redis连接统计
export const getRedisStats = (): Promise<{
  connectedClients: number
  usedMemory: number
  keyspaceHits: number
  keyspaceMisses: number
  hitRate: number
  evictedKeys: number
  queueSizes: Record<string, number>
}> => {
  return api.get(`${API_BASE}/redis/stats`)
}

// 获取任务执行统计（按时间段）
export const getTaskExecutionStats = (timeRange: string = '24h'): Promise<{
  timeSlot: string
  completedTasks: number
  failedTasks: number
  avgExecutionTime: number
  throughput: number
}[]> => {
  return api.get(`${API_BASE}/tasks/execution-stats`, {
    params: { timeRange }
  })
}

// 获取爬虫频率控制统计
export const getRateLimitStats = (): Promise<{
  endpoint: string
  currentRate: number
  maxRate: number
  requestCount: number
  blockedRequests: number
  avgWaitTime: number
}[]> => {
  return api.get(`${API_BASE}/rate-limit/stats`)
}

// 获取批量数据处理统计
export const getBatchProcessingStats = (): Promise<{
  currentQueueSize: number
  currentCacheSize: number
  totalProcessed: number
  duplicatesSkipped: number
  batchesProcessed: number
  lastFlushTime: number
  timeSinceLastFlush: number
}> => {
  return api.get(`${API_BASE}/batch-processing/stats`)
}

// 获取网络请求统计
export const getNetworkStats = (): Promise<{
  totalRequests: number
  successfulRequests: number
  timeoutRequests: number
  connectionErrors: number
  avgResponseSize: number
  totalDataTransferred: number
  requestsPerEndpoint: Record<string, number>
}> => {
  return api.get(`${API_BASE}/network/stats`)
}

// 获取JVM性能统计
export const getJvmStats = (): Promise<{
  heapMemory: {
    used: number
    max: number
    committed: number
    usagePercentage: number
  }
  nonHeapMemory: {
    used: number
    max: number
    committed: number
  }
  gcStats: {
    collectionCount: number
    collectionTime: number
    lastGcDuration: number
  }
  threadStats: {
    totalThreads: number
    peakThreads: number
    activeThreads: number
  }
  classLoading: {
    loadedClasses: number
    unloadedClasses: number
    totalLoaded: number
  }
}> => {
  return api.get(`${API_BASE}/jvm/stats`)
}

// 重置监控统计
export const resetMonitoringStats = (): Promise<{
  success: boolean
  message: string
}> => {
  return api.post(`${API_BASE}/reset`)
}

// 导出监控报告
export const exportMonitoringReport = (format: 'json' | 'excel' | 'pdf' = 'json'): Promise<Blob> => {
  return api.get(`${API_BASE}/export/report`, {
    params: { format },
    responseType: 'blob'
  })
}

// 获取告警列表
export const getAlerts = (level?: 'INFO' | 'WARNING' | 'ERROR' | 'CRITICAL'): Promise<{
  id: string
  level: string
  title: string
  message: string
  timestamp: string
  resolved: boolean
  category: string
}[]> => {
  return api.get(`${API_BASE}/alerts`, {
    params: level ? { level } : undefined
  })
}

// 标记告警为已解决
export const resolveAlert = (alertId: string): Promise<{
  success: boolean
  message: string
}> => {
  return api.post(`${API_BASE}/alerts/${alertId}/resolve`)
}

// 获取推荐优化建议
export const getOptimizationSuggestions = (): Promise<{
  category: string
  priority: 'HIGH' | 'MEDIUM' | 'LOW'
  title: string
  description: string
  actionItems: string[]
  estimatedImpact: string
}[]> => {
  return api.get(`${API_BASE}/suggestions`)
}

// 统一导出可视化API
export const visualizationApi = {
  // 核心指标
  getPerformanceMetrics,
  getPerformanceHistory,
  getSystemOverview,
  getSystemHealth,
  
  // 统计数据
  getErrorDistribution,
  getCategoryStatistics,
  getMemoryStatistics,
  getTaskSchedulerStatistics,
  getCrawlerMetrics,
  
  // 详细分析
  getApiResponseTimes,
  getDatabaseStats,
  getRedisStats,
  getTaskExecutionStats,
  getRateLimitStats,
  getBatchProcessingStats,
  getNetworkStats,
  getJvmStats,
  
  // 管理功能
  resetMonitoringStats,
  exportMonitoringReport,
  getAlerts,
  resolveAlert,
  getOptimizationSuggestions
}

export default visualizationApi