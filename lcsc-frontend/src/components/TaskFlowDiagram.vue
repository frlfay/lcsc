<template>
  <div class="task-flow-diagram">
    <div class="flow-header">
      <div class="flow-title">
        <h3>任务执行流程</h3>
        <el-tag v-if="taskInfo" :type="getTaskStatusType(taskInfo.status)" size="large">
          {{ taskInfo.status || '未知状态' }}
        </el-tag>
      </div>
      <div class="flow-controls">
        <el-button @click="refreshFlow" :loading="loading" size="small" type="primary">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button @click="toggleAutoRefresh" size="small" :type="autoRefresh ? 'warning' : 'info'">
          <el-icon><Clock /></el-icon>
          {{ autoRefresh ? '停止自动刷新' : '自动刷新' }}
        </el-button>
      </div>
    </div>

    <!-- 任务基本信息 -->
    <div v-if="taskInfo" class="task-info-panel">
      <el-descriptions :column="4" size="small" border>
        <el-descriptions-item label="任务ID">{{ taskInfo.taskId }}</el-descriptions-item>
        <el-descriptions-item label="任务类型">{{ taskInfo.taskType || '未知' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatDateTime(taskInfo.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="执行时长">{{ formatDuration(taskInfo.duration) }}</el-descriptions-item>
        <el-descriptions-item label="总步骤">{{ taskInfo.totalSteps || 0 }}</el-descriptions-item>
        <el-descriptions-item label="已完成">{{ taskInfo.completedSteps || 0 }}</el-descriptions-item>
        <el-descriptions-item label="错误数">{{ taskInfo.errorCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="重试次数">{{ taskInfo.retryCount || 0 }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <!-- 流程图主体 -->
    <div class="flow-diagram-container" ref="flowContainer">
      <div v-if="loading && flowSteps.length === 0" class="loading-container">
        <el-loading text="正在加载任务流程..." />
      </div>
      
      <div v-else-if="flowSteps.length === 0" class="empty-flow">
        <el-empty description="暂无流程数据">
          <el-button type="primary" @click="refreshFlow">重新加载</el-button>
        </el-empty>
      </div>
      
      <div v-else class="flow-steps">
        <div 
          v-for="(step, index) in flowSteps" 
          :key="step.id || index"
          class="flow-step"
          :class="getStepClasses(step)"
          @click="selectStep(step)"
        >
          <!-- 步骤图标和连接线 -->
          <div class="step-connector" v-if="index > 0">
            <div class="connector-line" :class="getPreviousStepStatus(index)"></div>
            <div class="connector-arrow"></div>
          </div>
          
          <!-- 步骤内容 -->
          <div class="step-content">
            <div class="step-header">
              <div class="step-icon" :class="getStepIconClass(step)">
                <el-icon>
                  <component :is="getStepIcon(step)" />
                </el-icon>
              </div>
              <div class="step-info">
                <div class="step-title">{{ getStepTitle(step.step) }}</div>
                <div class="step-subtitle">{{ step.message }}</div>
              </div>
              <div class="step-meta">
                <div class="step-time">{{ formatTime(step.createTime) }}</div>
                <div v-if="step.durationMs" class="step-duration">{{ step.durationMs }}ms</div>
              </div>
            </div>
            
            <!-- 进度条 -->
            <div v-if="step.progress !== null && step.progress !== undefined" class="step-progress">
              <el-progress 
                :percentage="step.progress" 
                :status="getProgressStatus(step)"
                :stroke-width="6"
                :show-text="true"
              >
                <template #default="{ percentage }">
                  <span class="progress-text">{{ percentage }}%</span>
                </template>
              </el-progress>
            </div>
            
            <!-- 步骤详情（可折叠） -->
            <div v-if="selectedStep?.id === step.id" class="step-details">
              <el-divider />
              <el-descriptions :column="2" size="small">
                <el-descriptions-item label="序列号">{{ step.sequenceOrder || 0 }}</el-descriptions-item>
                <el-descriptions-item label="日志级别">
                  <el-tag :type="getLevelType(step.level)" size="small">{{ step.level }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item v-if="step.errorCode" label="错误代码">
                  <el-tag type="danger" size="small">{{ step.errorCode }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item v-if="step.retryCount > 0" label="重试次数">
                  {{ step.retryCount }}
                </el-descriptions-item>
              </el-descriptions>
              
              <!-- 元数据显示 -->
              <div v-if="step.metadata" class="step-metadata">
                <h4>执行详情</h4>
                <pre class="metadata-content">{{ formatMetadata(step.metadata) }}</pre>
              </div>
              
              <!-- 错误信息 -->
              <div v-if="step.level === 'ERROR' && step.extraData" class="step-error">
                <h4>错误详情</h4>
                <pre class="error-content">{{ step.extraData }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 子任务流程 -->
    <div v-if="childTasks.length > 0" class="child-tasks">
      <el-divider>
        <span class="child-tasks-title">子任务 ({{ childTasks.length }})</span>
      </el-divider>
      <div class="child-tasks-list">
        <div 
          v-for="childTask in childTasks" 
          :key="childTask.taskId"
          class="child-task-item"
          @click="$emit('selectChildTask', childTask.taskId)"
        >
          <el-card shadow="hover" class="child-task-card">
            <div class="child-task-header">
              <span class="child-task-id">{{ childTask.taskId }}</span>
              <el-tag :type="getTaskStatusType(childTask.status)" size="small">
                {{ childTask.status }}
              </el-tag>
            </div>
            <div class="child-task-progress" v-if="childTask.progress !== undefined">
              <el-progress 
                :percentage="childTask.progress || 0" 
                :stroke-width="4"
                :show-text="false"
              />
            </div>
          </el-card>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import {
  SyncOutlined,
  ClockCircleOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  CloseCircleOutlined,
  InfoCircleOutlined,
  DownloadOutlined,
  UploadOutlined,
  ToolOutlined,
  CheckOutlined,
  CloseOutlined
} from '@ant-design/icons-vue'

// Props
interface Props {
  taskId: string
  autoRefreshInterval?: number
}

const props = withDefaults(defineProps<Props>(), {
  autoRefreshInterval: 3000
})

// Emits
const emit = defineEmits<{
  selectChildTask: [taskId: string]
  stepSelected: [step: any]
}>()

// 响应式数据
const loading = ref(false)
const autoRefresh = ref(false)
const flowSteps = ref<any[]>([])
const selectedStep = ref<any>(null)
const taskInfo = ref<any>(null)
const childTasks = ref<any[]>([])

// 定时器
let refreshTimer: NodeJS.Timeout | null = null

// 步骤图标映射
const stepIcons = {
  'INIT': InfoCircleOutlined,
  'FETCHING_CATALOGS': DownloadOutlined,
  'FETCHING_MANUFACTURERS': DownloadOutlined,
  'FETCHING_PACKAGES': DownloadOutlined,
  'FETCHING_PRODUCTS': DownloadOutlined,
  'PROCESSING_CATALOG': ToolOutlined,
  'PROCESSING_MANUFACTURER': ToolOutlined,
  'PROCESSING_PACKAGE': ToolOutlined,
  'PARSING_PRODUCTS': ToolOutlined,
  'SAVING_PRODUCTS': UploadOutlined,
  'COMPLETED': CheckOutlined,
  'FAILED': CloseOutlined,
  'ERROR': CloseCircleOutlined
}

// 步骤标题映射
const stepTitles = {
  'INIT': '初始化',
  'FETCHING_CATALOGS': '获取目录',
  'FETCHING_MANUFACTURERS': '获取制造商',
  'FETCHING_PACKAGES': '获取封装',
  'FETCHING_PRODUCTS': '获取产品',
  'PROCESSING_CATALOG': '处理目录',
  'PROCESSING_MANUFACTURER': '处理制造商',
  'PROCESSING_PACKAGE': '处理封装',
  'PARSING_PRODUCTS': '解析产品',
  'SAVING_PRODUCTS': '保存产品',
  'COMPLETED': '已完成',
  'FAILED': '失败'
}

// 计算属性
const flowContainer = ref<HTMLElement>()

// 方法
const refreshFlow = async () => {
  if (!props.taskId) return
  
  loading.value = true
  try {
    // 这里应该调用API获取任务流程数据
    // const response = await getTaskFlow(props.taskId)
    // flowSteps.value = response.data.flow || []
    // taskInfo.value = response.data.taskInfo
    // childTasks.value = response.data.childTasks || []
    
    // 模拟数据，实际应该从API获取
    await simulateApiCall()
  } catch (error) {
    console.error('获取任务流程失败:', error)
  } finally {
    loading.value = false
  }
}

const simulateApiCall = async () => {
  // 模拟API调用延迟
  await new Promise(resolve => setTimeout(resolve, 500))
  
  // 模拟流程数据
  flowSteps.value = [
    {
      id: 1,
      step: 'INIT',
      message: '开始爬取目录数据 - catalogId: 123',
      level: 'INFO',
      progress: 100,
      createTime: new Date(Date.now() - 300000).toISOString(),
      durationMs: 50,
      sequenceOrder: 1
    },
    {
      id: 2,
      step: 'FETCHING_MANUFACTURERS',
      message: '正在获取制造商列表...',
      level: 'INFO',
      progress: 100,
      createTime: new Date(Date.now() - 250000).toISOString(),
      durationMs: 1200,
      sequenceOrder: 2,
      metadata: JSON.stringify({
        catalogId: 123,
        manufacturerCount: 15,
        manufacturerIds: [1, 2, 3, 4, 5]
      })
    },
    {
      id: 3,
      step: 'PROCESSING_MANUFACTURER',
      message: '正在处理制造商 1 (1/15)',
      level: 'INFO',
      progress: 60,
      createTime: new Date(Date.now() - 200000).toISOString(),
      durationMs: null,
      sequenceOrder: 3
    }
  ]
  
  taskInfo.value = {
    taskId: props.taskId,
    taskType: 'CATALOG_CRAWL',
    status: 'RUNNING',
    startTime: new Date(Date.now() - 300000).toISOString(),
    duration: 300000,
    totalSteps: 50,
    completedSteps: 25,
    errorCount: 2,
    retryCount: 1
  }
}

const selectStep = (step: any) => {
  selectedStep.value = selectedStep.value?.id === step.id ? null : step
  emit('stepSelected', step)
}

const toggleAutoRefresh = () => {
  autoRefresh.value = !autoRefresh.value
  if (autoRefresh.value) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
}

const startAutoRefresh = () => {
  if (refreshTimer) return
  refreshTimer = setInterval(refreshFlow, props.autoRefreshInterval)
}

const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

// 样式计算方法
const getStepClasses = (step: any) => {
  const classes = []
  if (step.level === 'ERROR') classes.push('step-error')
  if (step.level === 'SUCCESS') classes.push('step-success')
  if (step.level === 'WARN') classes.push('step-warning')
  if (selectedStep.value?.id === step.id) classes.push('step-selected')
  if (step.durationMs === null) classes.push('step-running')
  return classes
}

const getStepIconClass = (step: any) => {
  const classes = ['step-icon-base']
  if (step.level === 'ERROR') classes.push('icon-error')
  if (step.level === 'SUCCESS') classes.push('icon-success')
  if (step.level === 'WARN') classes.push('icon-warning')
  if (step.durationMs === null) classes.push('icon-running')
  return classes
}

const getStepIcon = (step: any) => {
  return stepIcons[step.step as keyof typeof stepIcons] || InfoCircleOutlined
}

const getStepTitle = (stepCode: string) => {
  return stepTitles[stepCode as keyof typeof stepTitles] || stepCode
}

const getPreviousStepStatus = (index: number) => {
  if (index === 0) return ''
  const prevStep = flowSteps.value[index - 1]
  if (prevStep.level === 'ERROR') return 'connector-error'
  if (prevStep.level === 'SUCCESS') return 'connector-success'
  return 'connector-normal'
}

const getProgressStatus = (step: any) => {
  if (step.level === 'ERROR') return 'exception'
  if (step.level === 'SUCCESS') return 'success'
  return undefined
}

const getTaskStatusType = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'warning'
    default: return 'info'
  }
}

const getLevelType = (level: string) => {
  switch (level?.toUpperCase()) {
    case 'ERROR': return 'danger'
    case 'SUCCESS': return 'success'
    case 'WARN': return 'warning'
    default: return 'info'
  }
}

// 格式化方法
const formatTime = (timeStr: string) => {
  return new Date(timeStr).toLocaleTimeString('zh-CN')
}

const formatDateTime = (timeStr: string) => {
  if (!timeStr) return '-'
  return new Date(timeStr).toLocaleString('zh-CN')
}

const formatDuration = (duration: number) => {
  if (!duration) return '-'
  const seconds = Math.floor(duration / 1000)
  const minutes = Math.floor(seconds / 60)
  if (minutes > 0) {
    return `${minutes}分${seconds % 60}秒`
  }
  return `${seconds}秒`
}

const formatMetadata = (metadataStr: string) => {
  try {
    return JSON.stringify(JSON.parse(metadataStr), null, 2)
  } catch {
    return metadataStr
  }
}

// 生命周期
onMounted(() => {
  refreshFlow()
})

onUnmounted(() => {
  stopAutoRefresh()
})

// 监听taskId变化
watch(() => props.taskId, (newTaskId) => {
  if (newTaskId) {
    refreshFlow()
  }
})
</script>

<style scoped>
.task-flow-diagram {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.flow-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.flow-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.flow-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.flow-controls {
  display: flex;
  gap: 8px;
}

.task-info-panel {
  padding: 16px 20px;
  background: #f8f9fa;
  border-bottom: 1px solid #e9ecef;
}

.flow-diagram-container {
  padding: 20px;
  min-height: 400px;
}

.loading-container, .empty-flow {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

.flow-steps {
  position: relative;
}

.flow-step {
  position: relative;
  margin-bottom: 24px;
  transition: all 0.3s ease;
  cursor: pointer;
}

.flow-step:hover {
  transform: translateY(-2px);
}

.flow-step.step-selected {
  box-shadow: 0 4px 20px rgba(64, 158, 255, 0.3);
}

.step-connector {
  position: absolute;
  top: -24px;
  left: 32px;
  height: 24px;
  width: 2px;
}

.connector-line {
  width: 2px;
  height: 20px;
  background: #ddd;
}

.connector-line.connector-success {
  background: #67c23a;
}

.connector-line.connector-error {
  background: #f56c6c;
}

.connector-arrow {
  width: 0;
  height: 0;
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  border-top: 6px solid #ddd;
  margin-left: -3px;
}

.step-content {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.flow-step.step-error .step-content {
  border-color: #f56c6c;
  background: #fef0f0;
}

.flow-step.step-success .step-content {
  border-color: #67c23a;
  background: #f0f9ff;
}

.flow-step.step-warning .step-content {
  border-color: #e6a23c;
  background: #fdf6ec;
}

.flow-step.step-running .step-content {
  border-color: #409eff;
  background: #ecf5ff;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% { box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2); }
  50% { box-shadow: 0 2px 20px rgba(64, 158, 255, 0.4); }
  100% { box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2); }
}

.step-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.step-icon-base {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: white;
  background: #909399;
  flex-shrink: 0;
}

.step-icon-base.icon-success {
  background: #67c23a;
}

.step-icon-base.icon-error {
  background: #f56c6c;
}

.step-icon-base.icon-warning {
  background: #e6a23c;
}

.step-icon-base.icon-running {
  background: #409eff;
  animation: rotate 2s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.step-info {
  flex: 1;
}

.step-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.step-subtitle {
  font-size: 14px;
  color: #606266;
}

.step-meta {
  text-align: right;
  font-size: 12px;
  color: #909399;
}

.step-time {
  font-weight: 600;
}

.step-duration {
  margin-top: 2px;
}

.step-progress {
  margin-top: 12px;
}

.progress-text {
  font-size: 12px;
  font-weight: 600;
}

.step-details {
  margin-top: 16px;
}

.step-metadata, .step-error {
  margin-top: 12px;
}

.step-metadata h4, .step-error h4 {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: #303133;
}

.metadata-content, .error-content {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
}

.error-content {
  background: #fef0f0;
  color: #f56c6c;
}

.child-tasks {
  margin-top: 20px;
  padding: 0 20px 20px;
}

.child-tasks-title {
  font-size: 14px;
  color: #606266;
}

.child-tasks-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.child-task-item {
  cursor: pointer;
}

.child-task-card {
  transition: all 0.3s ease;
}

.child-task-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.child-task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.child-task-id {
  font-size: 12px;
  color: #909399;
  font-family: 'Consolas', 'Monaco', monospace;
}

.child-task-progress {
  margin-top: 8px;
}
</style>