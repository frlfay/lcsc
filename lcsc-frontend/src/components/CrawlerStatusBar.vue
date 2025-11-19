<template>
  <div class="crawler-status-bar">
    <div class="status-indicator" :class="statusClass">
      <PlayCircleOutlined v-if="isRunning" />
      <PauseCircleOutlined v-else />
      <span class="status-text">{{ statusText }}</span>
    </div>
    
    <div class="task-counter" v-if="stats">
      <a-badge :count="stats.activeTaskCount" :number-style="{ backgroundColor: stats.activeTaskCount > 0 ? '#1890ff' : '#d9d9d9' }">
        <SettingOutlined />
      </a-badge>
    </div>

    <div class="control-buttons">
      <a-button 
        :type="isRunning ? 'primary' : 'default'" 
        :danger="isRunning"
        size="small" 
        :loading="loading"
        @click="toggleCrawler"
      >
        {{ isRunning ? '停止' : '启动' }}
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { PlayCircleOutlined, PauseCircleOutlined, SettingOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { crawlerApi } from '../api/crawler'

interface CrawlerStats {
  status: string
  activeTaskCount: number
  todayCount: number
  queueSize: number
}

const stats = ref<CrawlerStats | null>(null)
const loading = ref(false)
let updateTimer: number | null = null

const isRunning = computed(() => stats.value?.status === 'RUNNING')
const statusText = computed(() => {
  if (!stats.value) return '连接中...'
  switch (stats.value.status) {
    case 'RUNNING': return '运行中'
    case 'STOPPED': return '已停止'
    case 'PAUSED': return '已暂停'
    default: return '未知'
  }
})

const statusClass = computed(() => ({
  'status-running': isRunning.value,
  'status-stopped': stats.value?.status === 'STOPPED',
  'status-paused': stats.value?.status === 'PAUSED'
}))

const fetchStats = async () => {
  try {
    const statusRes = await crawlerApi.getStatus()
    
    // 根据后端返回的实际数据结构解析
    const data = statusRes.data || statusRes
    const queueStatus = (data as any).queueStatus || {}
    const workerStatus = (data as any).workerStatus || {}
    
    stats.value = {
      status: (data as any).isRunning ? 'RUNNING' : ((data as any).isPaused ? 'PAUSED' : 'STOPPED'),
      activeTaskCount: queueStatus.processing || 0,
      todayCount: queueStatus.completed || 0,
      queueSize: queueStatus.pending || 0
    }
  } catch (error) {
    console.error('Failed to fetch crawler stats:', error)
    // 设置默认值避免页面错误
    stats.value = {
      status: 'STOPPED',
      activeTaskCount: 0,
      todayCount: 0,
      queueSize: 0
    }
  }
}

const toggleCrawler = async () => {
  loading.value = true
  try {
    if (isRunning.value) {
      await crawlerApi.stop()
      message.success('爬虫已停止')
    } else {
      await crawlerApi.start()
      message.success('爬虫已启动')
    }
    await fetchStats()
  } catch (error) {
    message.error(isRunning.value ? '停止失败' : '启动失败')
  } finally {
    loading.value = false
  }
}

const startPolling = () => {
  fetchStats()
  updateTimer = window.setInterval(fetchStats, 3000)
}

const stopPolling = () => {
  if (updateTimer) {
    clearInterval(updateTimer)
    updateTimer = null
  }
}

onMounted(() => {
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped lang="scss">
.crawler-status-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  height: 100%;

  .status-indicator {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 4px 8px;
    border-radius: 4px;
    font-size: 12px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.3s;

    &.status-running {
      color: #67c23a;
      background: rgba(103, 194, 58, 0.1);
    }

    &.status-stopped {
      color: #909399;
      background: rgba(144, 147, 153, 0.1);
    }

    &.status-paused {
      color: #e6a23c;
      background: rgba(230, 162, 60, 0.1);
    }

    &:hover {
      opacity: 0.8;
    }

    .status-text {
      white-space: nowrap;
    }
  }

  .task-counter {
    display: flex;
    align-items: center;
  }

  .control-buttons {
    .ant-btn {
      padding: 6px 12px;
    }
  }
}

@media (max-width: 768px) {
  .crawler-status-bar {
    gap: 8px;
    padding: 0 8px;

    .status-text {
      display: none;
    }
  }
}
</style>