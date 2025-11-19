<template>
  <el-card class="log-panel">
    <template #header>
      <div class="panel-header">
        <h3>实时日志</h3>
        <div class="log-controls">
          <el-select v-model="selectedLevel" placeholder="日志级别" size="small" style="width: 100px;">
            <el-option label="全部" value="" />
            <el-option label="INFO" value="INFO" />
            <el-option label="DEBUG" value="DEBUG" />
            <el-option label="ERROR" value="ERROR" />
          </el-select>
          <el-select v-model="selectedTaskId" placeholder="任务ID" size="small" style="width: 120px;" clearable>
            <el-option label="全部任务" value="" />
            <el-option 
              v-for="taskId in activeTasks" 
              :key="taskId" 
              :label="`任务 ${taskId}`" 
              :value="taskId" 
            />
          </el-select>
          <el-button size="small" @click="clearLogs">清空日志</el-button>
          <el-button size="small" :type="isAutoScroll ? 'primary' : 'default'" @click="toggleAutoScroll">
            {{ isAutoScroll ? '停止滚动' : '自动滚动' }}
          </el-button>
        </div>
      </div>
    </template>

    <div class="log-content" ref="logContainer">
      <div v-if="displayLogs.length === 0" class="empty-state">
        <el-empty description="暂无日志数据" :image-size="60" />
      </div>
      <div v-else class="log-list">
        <div 
          v-for="(log, index) in displayLogs" 
          :key="`${log.id}-${index}`"
          class="log-item"
          :class="[`log-level-${log.level.toLowerCase()}`, { 'log-new': log.isNew }]"
        >
          <div class="log-header">
            <el-tag :type="getLevelTagType(log.level)" size="small">{{ log.level }}</el-tag>
            <span class="log-time">{{ formatTime(log.createTime) }}</span>
            <span v-if="log.taskId" class="log-task-id">任务: {{ log.taskId }}</span>
            <span v-if="log.step" class="log-step">步骤: {{ log.step }}</span>
          </div>
          <div class="log-message">{{ log.message }}</div>
          <div v-if="log.details" class="log-details">{{ log.details }}</div>
        </div>
      </div>
    </div>

    <div class="log-footer">
      <div class="log-stats">
        <span>总计: {{ displayLogs.length }} 条</span>
        <span v-if="errorCount > 0" class="error-count">错误: {{ errorCount }} 条</span>
        <span class="update-time">最后更新: {{ lastUpdateTime }}</span>
      </div>
      <div class="log-actions">
        <el-button size="small" @click="scrollToBottom">滚动到底部</el-button>
        <el-button size="small" @click="refreshLogs">刷新</el-button>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { message } from 'ant-design-vue'
import { getTaskLogsPage, getActiveTasksStatus } from '../api/tasklog'

interface TaskLog {
  id: string
  taskId: string
  level: string
  message: string
  details?: string
  step?: string
  createTime: string
  isNew?: boolean
}

const logs = ref<TaskLog[]>([])
const selectedLevel = ref('')
const selectedTaskId = ref('')
const activeTasks = ref<string[]>([])
const isAutoScroll = ref(true)
const logContainer = ref<HTMLElement>()
const lastUpdateTime = ref('')

let updateTimer: number | null = null
let newLogTimeout: number | null = null

const displayLogs = computed(() => {
  return logs.value
    .filter(log => {
      if (selectedLevel.value && log.level !== selectedLevel.value) return false
      if (selectedTaskId.value && log.taskId !== selectedTaskId.value) return false
      return true
    })
    .slice(-500) // 只显示最近500条日志
})

const errorCount = computed(() => {
  return displayLogs.value.filter(log => log.level === 'ERROR').length
})

const getLevelTagType = (level: string) => {
  switch (level) {
    case 'ERROR': return 'danger'
    case 'WARN': return 'warning'
    case 'INFO': return 'primary'
    case 'DEBUG': return 'info'
    default: return 'info'
  }
}

const formatTime = (timestamp: string) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString()
}

const fetchLogs = async () => {
  try {
    const response = await getTaskLogsPage({
      page: 1,
      size: 100,
      level: selectedLevel.value || undefined,
      taskId: selectedTaskId.value || undefined
    })
    
    // 正确解析API响应数据结构
    const data = response.data || response
    const newLogs = data.records || []
    const existingIds = new Set(logs.value.map(log => log.id))
    
    // 标记新日志并转换数据格式
    const convertedLogs = newLogs.map((apiLog: any) => ({
      id: String(apiLog.id || apiLog.taskId + '-' + Date.now()),
      taskId: apiLog.taskId || '',
      level: apiLog.level || 'INFO',
      message: apiLog.message || '',
      details: apiLog.extraData ? (typeof apiLog.extraData === 'string' ? apiLog.extraData : JSON.stringify(apiLog.extraData)) : undefined,
      step: apiLog.step || '',
      createTime: apiLog.createTime || new Date().toISOString(),
      isNew: !existingIds.has(String(apiLog.id))
    }))
    
    // 只添加新的日志
    const newLogsToAdd = convertedLogs.filter((log: any) => !existingIds.has(log.id))
    logs.value = [...newLogsToAdd, ...logs.value]
    
    // 限制日志数量，避免内存溢出
    if (logs.value.length > 1000) {
      logs.value = logs.value.slice(0, 800)
    }
    
    lastUpdateTime.value = new Date().toLocaleTimeString()
    
    // 自动滚动到底部
    if (isAutoScroll.value && newLogsToAdd.length > 0) {
      nextTick(scrollToBottom)
    }
    
    // 清除新日志标记
    if (newLogTimeout) clearTimeout(newLogTimeout)
    newLogTimeout = window.setTimeout(() => {
      logs.value.forEach(log => {
        if (log.isNew) log.isNew = false
      })
    }, 2000)
    
  } catch (error) {
    console.error('Failed to fetch logs:', error)
  }
}

const fetchActiveTasks = async () => {
  try {
    const response = await getActiveTasksStatus()
    const data = response.data || response || {}
    // 从活跃任务数据中提取任务ID列表
    activeTasks.value = Object.keys(data) || []
  } catch (error) {
    console.error('Failed to fetch active tasks:', error)
    activeTasks.value = []
  }
}

const clearLogs = () => {
  logs.value = []
  message.success('日志已清空')
}

const toggleAutoScroll = () => {
  isAutoScroll.value = !isAutoScroll.value
  if (isAutoScroll.value) {
    nextTick(scrollToBottom)
  }
}

const scrollToBottom = () => {
  if (logContainer.value) {
    logContainer.value.scrollTop = logContainer.value.scrollHeight
  }
}

const refreshLogs = () => {
  logs.value = []
  fetchLogs()
}

const startPolling = () => {
  fetchLogs()
  fetchActiveTasks()
  updateTimer = window.setInterval(() => {
    fetchLogs()
    fetchActiveTasks()
  }, 2000)
}

const stopPolling = () => {
  if (updateTimer) {
    clearInterval(updateTimer)
    updateTimer = null
  }
  if (newLogTimeout) {
    clearTimeout(newLogTimeout)
    newLogTimeout = null
  }
}

// 监听筛选条件变化，重新获取日志
watch([selectedLevel, selectedTaskId], () => {
  logs.value = []
  fetchLogs()
})

onMounted(() => {
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped lang="scss">
.log-panel {
  height: 600px;
  display: flex;
  flex-direction: column;
  
  :deep(.el-card__body) {
    flex: 1;
    display: flex;
    flex-direction: column;
    padding: 0;
  }
  
  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 0;
    
    h3 {
      margin: 0;
    }
    
    .log-controls {
      display: flex;
      gap: 8px;
      align-items: center;
    }
  }
  
  .log-content {
    flex: 1;
    overflow-y: auto;
    background: #1e1e1e;
    color: #d4d4d4;
    border-radius: 4px;
    margin: 16px;
    
    .empty-state {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 100%;
      color: #909399;
    }
    
    .log-list {
      padding: 12px;
    }
    
    .log-item {
      margin-bottom: 12px;
      padding: 12px 16px;
      border-radius: 8px;
      border-left: 4px solid #409EFF;
      background: rgba(64, 158, 255, 0.08);
      transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
      position: relative;
      transform: translateX(0);
      
      &:hover {
        transform: translateX(4px);
        box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
      }
      
      &.log-new {
        background: rgba(64, 158, 255, 0.2);
        box-shadow: 0 0 16px rgba(64, 158, 255, 0.4);
        animation: slideInRight 0.6s ease-out, pulseGlow 2s ease-in-out;
      }
      
      &.log-level-error {
        border-left-color: #F56C6C;
        background: rgba(245, 108, 108, 0.08);
        
        &:hover {
          box-shadow: 0 4px 12px rgba(245, 108, 108, 0.2);
        }
        
        &.log-new {
          background: rgba(245, 108, 108, 0.2);
          box-shadow: 0 0 16px rgba(245, 108, 108, 0.4);
        }
      }
      
      &.log-level-warn {
        border-left-color: #E6A23C;
        background: rgba(230, 162, 60, 0.08);
        
        &:hover {
          box-shadow: 0 4px 12px rgba(230, 162, 60, 0.2);
        }
        
        &.log-new {
          background: rgba(230, 162, 60, 0.2);
          box-shadow: 0 0 16px rgba(230, 162, 60, 0.4);
        }
      }
      
      &.log-level-success {
        border-left-color: #67C23A;
        background: rgba(103, 194, 58, 0.08);
        
        &:hover {
          box-shadow: 0 4px 12px rgba(103, 194, 58, 0.2);
        }
        
        &.log-new {
          background: rgba(103, 194, 58, 0.2);
          box-shadow: 0 0 16px rgba(103, 194, 58, 0.4);
        }
      }
      
      &.log-level-debug {
        border-left-color: #909399;
        background: rgba(144, 147, 153, 0.08);
        
        &:hover {
          box-shadow: 0 4px 12px rgba(144, 147, 153, 0.2);
        }
        
        &.log-new {
          background: rgba(144, 147, 153, 0.2);
          box-shadow: 0 0 16px rgba(144, 147, 153, 0.4);
        }
      }
    }
    
    .log-header {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 8px;
      font-size: 12px;
      
      .log-time {
        color: #909399;
        font-family: 'Monaco', 'Menlo', monospace;
        background: rgba(144, 147, 153, 0.1);
        padding: 2px 6px;
        border-radius: 4px;
        font-size: 11px;
      }
      
      .log-task-id {
        color: #1890ff;
        background: rgba(24, 144, 255, 0.1);
        padding: 2px 8px;
        border-radius: 12px;
        font-size: 11px;
        font-weight: 500;
        border: 1px solid rgba(24, 144, 255, 0.2);
      }
      
      .log-step {
        color: #67C23A;
        background: rgba(103, 194, 58, 0.1);
        padding: 2px 8px;
        border-radius: 12px;
        font-size: 11px;
        font-weight: 500;
        border: 1px solid rgba(103, 194, 58, 0.2);
      }
    }
    
    .log-message {
      font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
      font-size: 13px;
      line-height: 1.4;
      white-space: pre-wrap;
      word-break: break-all;
    }
    
    .log-details {
      margin-top: 4px;
      font-size: 12px;
      color: #909399;
      font-style: italic;
    }
  }
  
  .log-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
    background: #f8f9fa;
    border-top: 1px solid #e4e7ed;
    
    .log-stats {
      display: flex;
      gap: 16px;
      font-size: 12px;
      color: #909399;
      
      .error-count {
        color: #F56C6C;
        font-weight: 500;
      }
    }
    
    .log-actions {
      display: flex;
      gap: 8px;
    }
  }
}

@keyframes slideInRight {
  0% { 
    transform: translateX(100%);
    opacity: 0;
  }
  100% { 
    transform: translateX(0);
    opacity: 1;
  }
}

@keyframes pulseGlow {
  0%, 100% { 
    box-shadow: 0 0 16px rgba(64, 158, 255, 0.4);
  }
  50% { 
    box-shadow: 0 0 24px rgba(64, 158, 255, 0.6);
  }
}

@keyframes newLogFlash {
  0% { transform: translateX(-4px); }
  50% { transform: translateX(2px); }
  100% { transform: translateX(0); }
}

@media (max-width: 768px) {
  .log-panel {
    height: 400px;
    
    .panel-header {
      flex-direction: column;
      gap: 12px;
      align-items: stretch;
      
      .log-controls {
        justify-content: space-between;
        flex-wrap: wrap;
        gap: 8px;
      }
    }
    
    .log-footer {
      flex-direction: column;
      gap: 12px;
      align-items: stretch;
      
      .log-stats {
        justify-content: center;
      }
    }
  }
}
</style>