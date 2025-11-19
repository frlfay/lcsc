<template>
  <div class="progress-dashboard">
    <!-- 总体进度概览 -->
    <div class="dashboard-header">
      <h3>任务执行仪表板</h3>
      <div class="header-controls">
        <el-switch
          v-model="autoUpdate"
          @change="toggleAutoUpdate"
          active-text="自动更新"
          inactive-text="手动更新"
          size="small"
        />
        <el-button @click="refreshData" :loading="loading" size="small" type="primary">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 核心指标卡片 -->
    <div class="metrics-grid">
      <!-- 总体进度 -->
      <el-card class="metric-card overall-progress" shadow="hover">
        <div class="metric-header">
          <h4>总体进度</h4>
          <el-tag :type="getOverallStatusType(overallProgress.status)" size="small">
            {{ overallProgress.status }}
          </el-tag>
        </div>
        <div class="metric-content">
          <div class="progress-circle">
            <el-progress 
              type="circle" 
              :percentage="overallProgress.percentage"
              :width="120"
              :stroke-width="12"
              :status="getProgressStatus(overallProgress.status)"
            >
              <template #default="{ percentage }">
                <span class="progress-value">{{ percentage }}%</span>
              </template>
            </el-progress>
          </div>
          <div class="progress-details">
            <div class="detail-item">
              <span class="detail-label">已完成步骤</span>
              <span class="detail-value">{{ overallProgress.completedSteps }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">总步骤数</span>
              <span class="detail-value">{{ overallProgress.totalSteps }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">预计完成</span>
              <span class="detail-value">{{ formatETA(overallProgress.eta) }}</span>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 执行速度 -->
      <el-card class="metric-card execution-speed" shadow="hover">
        <div class="metric-header">
          <h4>执行性能</h4>
          <el-tooltip content="每分钟处理的步骤数">
            <el-icon class="help-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="metric-content">
          <div class="speed-display">
            <div class="speed-value">{{ performanceMetrics.stepsPerMinute }}</div>
            <div class="speed-unit">步骤/分钟</div>
          </div>
          <div class="speed-chart">
            <div class="chart-bars">
              <div 
                v-for="(value, index) in performanceMetrics.recentSpeeds" 
                :key="index"
                class="chart-bar"
                :style="{ height: `${(value / Math.max(...performanceMetrics.recentSpeeds)) * 40}px` }"
              ></div>
            </div>
          </div>
          <div class="performance-details">
            <div class="detail-row">
              <span>平均响应时间</span>
              <span>{{ performanceMetrics.avgResponseTime }}ms</span>
            </div>
            <div class="detail-row">
              <span>成功率</span>
              <span class="success-rate">{{ performanceMetrics.successRate }}%</span>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 资源使用 -->
      <el-card class="metric-card resource-usage" shadow="hover">
        <div class="metric-header">
          <h4>系统资源</h4>
        </div>
        <div class="metric-content">
          <div class="resource-item">
            <div class="resource-label">
              <span>线程池使用率</span>
              <span class="resource-value">{{ resourceMetrics.threadPoolUsage }}%</span>
            </div>
            <el-progress 
              :percentage="resourceMetrics.threadPoolUsage"
              :stroke-width="8"
              :show-text="false"
              :color="getResourceColor(resourceMetrics.threadPoolUsage)"
            />
          </div>
          <div class="resource-item">
            <div class="resource-label">
              <span>队列负载</span>
              <span class="resource-value">{{ resourceMetrics.queueLoad }}%</span>
            </div>
            <el-progress 
              :percentage="resourceMetrics.queueLoad"
              :stroke-width="8"
              :show-text="false"
              :color="getResourceColor(resourceMetrics.queueLoad)"
            />
          </div>
          <div class="resource-item">
            <div class="resource-label">
              <span>网络使用</span>
              <span class="resource-value">{{ resourceMetrics.networkUsage }}%</span>
            </div>
            <el-progress 
              :percentage="resourceMetrics.networkUsage"
              :stroke-width="8"
              :show-text="false"
              :color="getResourceColor(resourceMetrics.networkUsage)"
            />
          </div>
        </div>
      </el-card>

      <!-- 错误统计 -->
      <el-card class="metric-card error-stats" shadow="hover">
        <div class="metric-header">
          <h4>错误统计</h4>
          <el-badge :value="errorStats.totalErrors" :hidden="errorStats.totalErrors === 0" type="danger">
            <el-icon class="error-icon"><Warning /></el-icon>
          </el-badge>
        </div>
        <div class="metric-content">
          <div class="error-summary">
            <div class="error-total">
              <span class="error-count">{{ errorStats.totalErrors }}</span>
              <span class="error-label">总错误数</span>
            </div>
            <div class="error-rate">
              <span class="rate-value" :class="{ 'high-error-rate': errorStats.errorRate > 5 }">
                {{ errorStats.errorRate }}%
              </span>
              <span class="rate-label">错误率</span>
            </div>
          </div>
          <div class="error-types">
            <div 
              v-for="errorType in errorStats.topErrorTypes" 
              :key="errorType.type"
              class="error-type-item"
            >
              <span class="error-type-name">{{ errorType.type }}</span>
              <div class="error-type-bar">
                <div 
                  class="error-type-fill"
                  :style="{ width: `${(errorType.count / errorStats.totalErrors) * 100}%` }"
                ></div>
                <span class="error-type-count">{{ errorType.count }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 详细进度条 -->
    <div class="detailed-progress">
      <el-card shadow="hover">
        <div class="card-header">
          <h4>详细执行进度</h4>
          <div class="progress-legend">
            <span class="legend-item">
              <div class="legend-color completed"></div>已完成
            </span>
            <span class="legend-item">
              <div class="legend-color running"></div>执行中
            </span>
            <span class="legend-item">
              <div class="legend-color pending"></div>待执行
            </span>
            <span class="legend-item">
              <div class="legend-color error"></div>错误
            </span>
          </div>
        </div>
        <div class="progress-breakdown">
          <div 
            v-for="stage in progressBreakdown" 
            :key="stage.name"
            class="stage-progress"
          >
            <div class="stage-header">
              <span class="stage-name">{{ stage.name }}</span>
              <span class="stage-status">{{ stage.completedTasks }}/{{ stage.totalTasks }}</span>
            </div>
            <div class="stage-bar">
              <div class="progress-segments">
                <div 
                  v-for="segment in stage.segments"
                  :key="segment.id"
                  class="progress-segment"
                  :class="segment.status"
                  :style="{ width: `${segment.width}%` }"
                  :title="`${segment.name}: ${segment.status}`"
                ></div>
              </div>
              <div class="stage-percentage">{{ stage.percentage }}%</div>
            </div>
            <div class="stage-details">
              <span class="stage-time">预计: {{ stage.estimatedTime }}</span>
              <span class="stage-speed">{{ stage.currentSpeed }} 个/分钟</span>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 实时活动日志 -->
    <div class="activity-log">
      <el-card shadow="hover">
        <div class="card-header">
          <h4>实时活动</h4>
          <div class="log-controls">
            <el-button @click="clearActivityLog" size="small" type="warning">
              <el-icon><Delete /></el-icon>
              清空日志
            </el-button>
            <el-switch
              v-model="autoScrollLog"
              active-text="自动滚动"
              size="small"
            />
          </div>
        </div>
        <div class="activity-content" ref="activityLogContainer">
          <div v-if="activityLog.length === 0" class="empty-log">
            <el-empty description="暂无活动日志" />
          </div>
          <div v-else class="log-entries">
            <div 
              v-for="entry in activityLog" 
              :key="entry.id"
              class="log-entry"
              :class="entry.level"
            >
              <div class="log-time">{{ formatTime(entry.timestamp) }}</div>
              <div class="log-content">
                <span class="log-source">[{{ entry.source }}]</span>
                <span class="log-message">{{ entry.message }}</span>
              </div>
              <div class="log-details" v-if="entry.details">
                <span class="log-detail-text">{{ entry.details }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { 
  SyncOutlined, 
  QuestionCircleOutlined, 
  ExclamationCircleOutlined, 
  DeleteOutlined 
} from '@ant-design/icons-vue'

// 响应式数据
const loading = ref(false)
const autoUpdate = ref(true)
const autoScrollLog = ref(true)

// 总体进度
const overallProgress = reactive({
  percentage: 68,
  status: 'RUNNING',
  completedSteps: 342,
  totalSteps: 500,
  eta: new Date(Date.now() + 15 * 60000) // 15分钟后
})

// 性能指标
const performanceMetrics = reactive({
  stepsPerMinute: 28,
  avgResponseTime: 245,
  successRate: 96.5,
  recentSpeeds: [25, 30, 28, 32, 26, 29, 31, 28, 27, 30] // 最近10分钟的速度
})

// 资源使用情况
const resourceMetrics = reactive({
  threadPoolUsage: 75,
  queueLoad: 45,
  networkUsage: 32
})

// 错误统计
const errorStats = reactive({
  totalErrors: 12,
  errorRate: 2.4,
  topErrorTypes: [
    { type: 'NETWORK_ERROR', count: 7 },
    { type: 'PARSE_ERROR', count: 3 },
    { type: 'TIMEOUT_ERROR', count: 2 }
  ]
})

// 进度详细分解
const progressBreakdown = ref([
  {
    name: '获取目录列表',
    totalTasks: 50,
    completedTasks: 50,
    percentage: 100,
    estimatedTime: '已完成',
    currentSpeed: 0,
    segments: [
      { id: 1, name: '目录1', status: 'completed', width: 20 },
      { id: 2, name: '目录2', status: 'completed', width: 20 },
      { id: 3, name: '目录3', status: 'completed', width: 20 },
      { id: 4, name: '目录4', status: 'completed', width: 20 },
      { id: 5, name: '目录5', status: 'completed', width: 20 }
    ]
  },
  {
    name: '处理制造商数据',
    totalTasks: 200,
    completedTasks: 136,
    percentage: 68,
    estimatedTime: '8分钟',
    currentSpeed: 15,
    segments: [
      { id: 1, name: '制造商批次1', status: 'completed', width: 25 },
      { id: 2, name: '制造商批次2', status: 'completed', width: 25 },
      { id: 3, name: '制造商批次3', status: 'running', width: 25 },
      { id: 4, name: '制造商批次4', status: 'pending', width: 15 },
      { id: 5, name: '制造商批次5', status: 'pending', width: 10 }
    ]
  },
  {
    name: '产品数据爬取',
    totalTasks: 1500,
    completedTasks: 420,
    percentage: 28,
    estimatedTime: '25分钟',
    currentSpeed: 32,
    segments: [
      { id: 1, name: '产品批次1', status: 'completed', width: 15 },
      { id: 2, name: '产品批次2', status: 'completed', width: 10 },
      { id: 3, name: '产品批次3', status: 'running', width: 8 },
      { id: 4, name: '产品批次4', status: 'error', width: 5 },
      { id: 5, name: '产品批次5', status: 'pending', width: 62 }
    ]
  }
])

// 活动日志
const activityLog = ref([
  {
    id: 1,
    timestamp: new Date(),
    level: 'info',
    source: 'CrawlerService',
    message: '开始处理制造商数据批次3',
    details: '预计包含45个制造商'
  },
  {
    id: 2,
    timestamp: new Date(Date.now() - 30000),
    level: 'success',
    source: 'ProductParser',
    message: '成功解析产品数据',
    details: '当前页面25个产品，累计1250个'
  },
  {
    id: 3,
    timestamp: new Date(Date.now() - 60000),
    level: 'warning',
    source: 'NetworkService',
    message: 'API响应时间较慢',
    details: '响应时间: 3.2秒，超过阈值'
  }
])

const activityLogContainer = ref<HTMLElement>()

// 定时器
let updateTimer: NodeJS.Timeout | null = null

// 方法
const refreshData = async () => {
  loading.value = true
  try {
    // 模拟API调用
    await new Promise(resolve => setTimeout(resolve, 500))
    // 这里应该调用真实的API来获取数据
    simulateDataUpdate()
  } catch (error) {
    console.error('刷新数据失败:', error)
  } finally {
    loading.value = false
  }
}

const simulateDataUpdate = () => {
  // 模拟数据更新
  overallProgress.percentage = Math.min(100, overallProgress.percentage + Math.random() * 5)
  overallProgress.completedSteps += Math.floor(Math.random() * 10)
  
  performanceMetrics.stepsPerMinute = Math.floor(25 + Math.random() * 10)
  performanceMetrics.avgResponseTime = Math.floor(200 + Math.random() * 100)
  
  // 添加新的活动日志
  if (Math.random() > 0.7) {
    addActivityLog()
  }
}

const addActivityLog = () => {
  const messages = [
    '完成产品数据保存',
    '开始处理新的封装类型',
    '网络连接恢复正常',
    'API响应时间优化',
    '数据解析完成'
  ]
  
  const levels = ['info', 'success', 'warning']
  const sources = ['CrawlerService', 'ProductParser', 'NetworkService', 'DatabaseService']
  
  const newLog = {
    id: Date.now(),
    timestamp: new Date(),
    level: levels[Math.floor(Math.random() * levels.length)],
    source: sources[Math.floor(Math.random() * sources.length)],
    message: messages[Math.floor(Math.random() * messages.length)],
    details: Math.random() > 0.5 ? '详细信息...' : ''
  }
  
  activityLog.value.unshift(newLog)
  
  // 限制日志数量
  if (activityLog.value.length > 50) {
    activityLog.value = activityLog.value.slice(0, 50)
  }
  
  // 自动滚动
  if (autoScrollLog.value) {
    nextTick(() => {
      if (activityLogContainer.value) {
        activityLogContainer.value.scrollTop = 0
      }
    })
  }
}

const clearActivityLog = () => {
  activityLog.value = []
}

const toggleAutoUpdate = (enabled: boolean) => {
  if (enabled) {
    startAutoUpdate()
  } else {
    stopAutoUpdate()
  }
}

const startAutoUpdate = () => {
  if (updateTimer) return
  updateTimer = setInterval(() => {
    simulateDataUpdate()
  }, 3000)
}

const stopAutoUpdate = () => {
  if (updateTimer) {
    clearInterval(updateTimer)
    updateTimer = null
  }
}

// 样式计算方法
const getOverallStatusType = (status: string) => {
  switch (status) {
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'warning'
    default: return 'info'
  }
}

const getProgressStatus = (status: string) => {
  switch (status) {
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'exception'
    default: return undefined
  }
}

const getResourceColor = (percentage: number) => {
  if (percentage >= 80) return '#f56c6c'
  if (percentage >= 60) return '#e6a23c'
  return '#67c23a'
}

// 格式化方法
const formatETA = (eta: Date) => {
  const now = new Date()
  const diff = eta.getTime() - now.getTime()
  if (diff <= 0) return '即将完成'
  
  const minutes = Math.floor(diff / 60000)
  if (minutes < 60) return `${minutes}分钟`
  
  const hours = Math.floor(minutes / 60)
  const remainingMinutes = minutes % 60
  return `${hours}小时${remainingMinutes}分钟`
}

const formatTime = (time: Date) => {
  return time.toLocaleTimeString('zh-CN', { 
    hour: '2-digit', 
    minute: '2-digit', 
    second: '2-digit' 
  })
}

// 生命周期
onMounted(() => {
  refreshData()
  if (autoUpdate.value) {
    startAutoUpdate()
  }
})

onUnmounted(() => {
  stopAutoUpdate()
})
</script>

<style scoped>
.progress-dashboard {
  background: #f5f7fa;
  min-height: 100vh;
  padding: 20px;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  background: white;
  padding: 20px 24px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.dashboard-header h3 {
  margin: 0;
  color: #303133;
  font-size: 24px;
  font-weight: 600;
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 16px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.metric-card {
  transition: all 0.3s ease;
}

.metric-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.metric-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.metric-header h4 {
  margin: 0;
  color: #303133;
  font-size: 16px;
  font-weight: 600;
}

.help-icon {
  color: #909399;
  cursor: help;
}

.metric-content {
  min-height: 120px;
}

/* 总体进度卡片 */
.overall-progress .metric-content {
  display: flex;
  align-items: center;
  gap: 24px;
}

.progress-circle {
  flex-shrink: 0;
}

.progress-value {
  font-size: 18px;
  font-weight: 700;
  color: #409eff;
}

.progress-details {
  flex: 1;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
}

.detail-label {
  color: #606266;
}

.detail-value {
  font-weight: 600;
  color: #303133;
}

/* 执行速度卡片 */
.execution-speed .metric-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.speed-display {
  text-align: center;
}

.speed-value {
  font-size: 32px;
  font-weight: 700;
  color: #67c23a;
  line-height: 1;
}

.speed-unit {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.speed-chart {
  height: 40px;
  display: flex;
  align-items: end;
}

.chart-bars {
  display: flex;
  justify-content: space-between;
  align-items: end;
  width: 100%;
  height: 40px;
  gap: 2px;
}

.chart-bar {
  background: linear-gradient(to top, #409eff, #66b1ff);
  width: 8px;
  border-radius: 2px 2px 0 0;
  min-height: 4px;
}

.performance-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
}

.success-rate {
  font-weight: 600;
  color: #67c23a;
}

/* 资源使用卡片 */
.resource-usage .metric-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.resource-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.resource-label {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
}

.resource-value {
  font-weight: 600;
}

/* 错误统计卡片 */
.error-stats .metric-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.error-summary {
  display: flex;
  justify-content: space-around;
}

.error-total, .error-rate {
  text-align: center;
}

.error-count, .rate-value {
  display: block;
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
}

.error-count {
  color: #f56c6c;
}

.rate-value {
  color: #e6a23c;
}

.rate-value.high-error-rate {
  color: #f56c6c;
}

.error-label, .rate-label {
  font-size: 12px;
  color: #909399;
}

.error-types {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.error-type-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.error-type-name {
  font-size: 12px;
  color: #606266;
  min-width: 80px;
}

.error-type-bar {
  flex: 1;
  height: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  position: relative;
  display: flex;
  align-items: center;
}

.error-type-fill {
  height: 100%;
  background: #f56c6c;
  border-radius: 8px;
  transition: width 0.3s ease;
}

.error-type-count {
  position: absolute;
  right: 4px;
  font-size: 10px;
  color: #909399;
  font-weight: 600;
}

/* 详细进度部分 */
.detailed-progress {
  margin-bottom: 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.card-header h4 {
  margin: 0;
  color: #303133;
  font-size: 18px;
  font-weight: 600;
}

.progress-legend {
  display: flex;
  gap: 16px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
}

.legend-color {
  width: 12px;
  height: 12px;
  border-radius: 2px;
}

.legend-color.completed { background: #67c23a; }
.legend-color.running { background: #409eff; }
.legend-color.pending { background: #e4e7ed; }
.legend-color.error { background: #f56c6c; }

.progress-breakdown {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.stage-progress {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stage-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stage-name {
  font-weight: 600;
  color: #303133;
}

.stage-status {
  font-size: 12px;
  color: #909399;
}

.stage-bar {
  position: relative;
  height: 24px;
  display: flex;
  align-items: center;
}

.progress-segments {
  flex: 1;
  height: 12px;
  display: flex;
  border-radius: 6px;
  overflow: hidden;
  background: #e4e7ed;
}

.progress-segment {
  height: 100%;
  transition: all 0.3s ease;
}

.progress-segment.completed { background: #67c23a; }
.progress-segment.running { 
  background: #409eff;
  animation: pulse-segment 2s infinite;
}
.progress-segment.pending { background: #e4e7ed; }
.progress-segment.error { background: #f56c6c; }

@keyframes pulse-segment {
  0% { opacity: 1; }
  50% { opacity: 0.7; }
  100% { opacity: 1; }
}

.stage-percentage {
  position: absolute;
  right: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #303133;
}

.stage-details {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}

/* 活动日志部分 */
.activity-log .card-header {
  border-bottom: 1px solid #e4e7ed;
  padding-bottom: 12px;
}

.log-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.activity-content {
  height: 300px;
  overflow-y: auto;
}

.empty-log {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.log-entries {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px 0;
}

.log-entry {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border-radius: 6px;
  border-left: 3px solid transparent;
  transition: all 0.2s ease;
}

.log-entry:hover {
  background: #f5f7fa;
  transform: translateX(2px);
}

.log-entry.info { 
  border-left-color: #409eff; 
  background: #f4f4f5;
}
.log-entry.success { 
  border-left-color: #67c23a; 
  background: #f0f9ff;
}
.log-entry.warning { 
  border-left-color: #e6a23c; 
  background: #fdf6ec;
}
.log-entry.error { 
  border-left-color: #f56c6c; 
  background: #fef0f0;
}

.log-time {
  font-size: 11px;
  color: #909399;
  font-family: 'Consolas', 'Monaco', monospace;
}

.log-content {
  display: flex;
  gap: 8px;
}

.log-source {
  font-size: 12px;
  color: #606266;
  font-weight: 600;
  background: #e4e7ed;
  padding: 2px 6px;
  border-radius: 3px;
  white-space: nowrap;
}

.log-message {
  font-size: 13px;
  color: #303133;
  flex: 1;
}

.log-details {
  margin-left: 12px;
}

.log-detail-text {
  font-size: 11px;
  color: #909399;
  font-style: italic;
}
</style>