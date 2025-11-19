<template>
  <el-card class="data-monitor">
    <template #header>
      <div class="panel-header">
        <h3>数据变化监控</h3>
        <div class="monitor-controls">
          <el-switch 
            v-model="isMonitoring" 
            active-text="监控中" 
            inactive-text="已暂停"
            @change="toggleMonitoring"
          />
          <el-button size="small" @click="refreshAll">刷新数据</el-button>
        </div>
      </div>
    </template>

    <!-- 数据统计卡片 -->
    <div class="stats-grid">
      <div class="stat-item" :class="{ 'stat-highlight': productStats.changed }">
        <div class="stat-icon">
          <el-icon><Goods /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">
            {{ productStats.total }}
            <span v-if="productStats.change !== 0" class="change-indicator" :class="productStats.change > 0 ? 'positive' : 'negative'">
              {{ productStats.change > 0 ? '+' : '' }}{{ productStats.change }}
            </span>
          </div>
          <div class="stat-label">产品总数</div>
          <div class="stat-trend">今日新增: {{ productStats.todayAdded }}</div>
        </div>
      </div>

      <div class="stat-item" :class="{ 'stat-highlight': categoryStats.changed }">
        <div class="stat-icon">
          <el-icon><Menu /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">
            {{ categoryStats.total }}
            <span v-if="categoryStats.change !== 0" class="change-indicator" :class="categoryStats.change > 0 ? 'positive' : 'negative'">
              {{ categoryStats.change > 0 ? '+' : '' }}{{ categoryStats.change }}
            </span>
          </div>
          <div class="stat-label">分类总数</div>
          <div class="stat-trend">一级: {{ categoryStats.level1 }} / 二级: {{ categoryStats.level2 }}</div>
        </div>
      </div>

      <div class="stat-item" :class="{ 'stat-highlight': shopStats.changed }">
        <div class="stat-icon">
          <el-icon><Shop /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">
            {{ shopStats.total }}
            <span v-if="shopStats.change !== 0" class="change-indicator" :class="shopStats.change > 0 ? 'positive' : 'negative'">
              {{ shopStats.change > 0 ? '+' : '' }}{{ shopStats.change }}
            </span>
          </div>
          <div class="stat-label">店铺总数</div>
          <div class="stat-trend">活跃: {{ shopStats.active }}</div>
        </div>
      </div>

      <div class="stat-item" :class="{ 'stat-highlight': imageStats.changed }">
        <div class="stat-icon">
          <el-icon><Picture /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">
            {{ imageStats.total }}
            <span v-if="imageStats.change !== 0" class="change-indicator" :class="imageStats.change > 0 ? 'positive' : 'negative'">
              {{ imageStats.change > 0 ? '+' : '' }}{{ imageStats.change }}
            </span>
          </div>
          <div class="stat-label">图片总数</div>
          <div class="stat-trend">今日新增: {{ imageStats.todayAdded }}</div>
        </div>
      </div>
    </div>

    <!-- 任务队列状态 -->
    <div class="queue-status">
      <h4>任务队列状态</h4>
      <div class="queue-stats">
        <div class="queue-item">
          <el-progress 
            :percentage="queueProgress" 
            :color="getQueueProgressColor()" 
            :stroke-width="8"
          />
          <div class="queue-info">
            <span>队列进度: {{ queueStats.completed }}/{{ queueStats.total }}</span>
            <span class="queue-detail">等待: {{ queueStats.waiting }} | 处理中: {{ queueStats.processing }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 最近变化记录 -->
    <div class="recent-changes">
      <h4>最近数据变化</h4>
      <div class="changes-list">
        <div v-if="recentChanges.length === 0" class="empty-changes">
          <el-empty description="暂无数据变化" :image-size="60" />
        </div>
        <div v-else>
          <div 
            v-for="change in recentChanges" 
            :key="`${change.type}-${change.timestamp}`"
            class="change-item"
            :class="`change-${change.type}`"
          >
            <div class="change-icon">
              <el-icon v-if="change.action === 'add'"><Plus /></el-icon>
              <el-icon v-else-if="change.action === 'update'"><Edit /></el-icon>
              <el-icon v-else><Delete /></el-icon>
            </div>
            <div class="change-content">
              <div class="change-title">{{ change.title }}</div>
              <div class="change-details">{{ change.details }}</div>
              <div class="change-time">{{ formatTime(change.timestamp) }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 实时数据变化趋势 -->
    <div class="trend-chart">
      <h4>数据变化趋势</h4>
      <div class="trend-container" ref="trendChart">
        <!-- 简单的趋势显示 -->
        <div class="trend-stats">
          <div class="trend-item">
            <span class="trend-label">最近1小时新增产品:</span>
            <span class="trend-value">{{ trendData.hourlyProducts }}</span>
          </div>
          <div class="trend-item">
            <span class="trend-label">平均处理速度:</span>
            <span class="trend-value">{{ trendData.processingRate }}/分钟</span>
          </div>
          <div class="trend-item">
            <span class="trend-label">成功率:</span>
            <span class="trend-value">{{ trendData.successRate }}%</span>
          </div>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { ShoppingOutlined, MenuOutlined, ShopOutlined, PictureOutlined, PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { crawlerApi } from '../api/crawler'

interface StatItem {
  total: number
  change: number
  changed: boolean
  todayAdded?: number
  level1?: number
  level2?: number
  active?: number
}

interface QueueStats {
  total: number
  completed: number
  waiting: number
  processing: number
}

interface DataChange {
  type: string
  action: 'add' | 'update' | 'delete'
  title: string
  details: string
  timestamp: string
}

interface TrendData {
  hourlyProducts: number
  processingRate: number
  successRate: number
}

const isMonitoring = ref(true)
const productStats = ref<StatItem>({ total: 0, change: 0, changed: false, todayAdded: 0 })
const categoryStats = ref<StatItem>({ total: 0, change: 0, changed: false, level1: 0, level2: 0 })
const shopStats = ref<StatItem>({ total: 0, change: 0, changed: false, active: 0 })
const imageStats = ref<StatItem>({ total: 0, change: 0, changed: false, todayAdded: 0 })
const queueStats = ref<QueueStats>({ total: 0, completed: 0, waiting: 0, processing: 0 })
const recentChanges = ref<DataChange[]>([])
const trendData = ref<TrendData>({ hourlyProducts: 0, processingRate: 0, successRate: 95 })

let updateTimer: number | null = null
let highlightTimer: number | null = null

const queueProgress = computed(() => {
  if (queueStats.value.total === 0) return 0
  return Math.round((queueStats.value.completed / queueStats.value.total) * 100)
})

const getQueueProgressColor = () => {
  const progress = queueProgress.value
  if (progress < 30) return '#F56C6C'
  if (progress < 70) return '#E6A23C'
  return '#67C23A'
}

const formatTime = (timestamp: string) => {
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return date.toLocaleString()
}

const fetchProductStats = async () => {
  try {
    const response = await crawlerApi.getProductStatistics()
    const data = response.data || response
    const newTotal = data.total || 0
    const newTodayAdded = data.todayAdded || 0
    
    const oldTotal = productStats.value.total
    const change = newTotal - oldTotal
    
    productStats.value = {
      total: newTotal,
      change: change,
      changed: change !== 0,
      todayAdded: newTodayAdded
    }
    
    if (change !== 0) {
      addChange('product', change > 0 ? 'add' : 'delete', 
        `产品数据变化`, 
        `${change > 0 ? '新增' : '减少'}了 ${Math.abs(change)} 个产品`)
    }
    
  } catch (error) {
    console.error('Failed to fetch product stats:', error)
  }
}

const fetchCategoryStats = async () => {
  try {
    const [level1Response, level2Response] = await Promise.all([
      crawlerApi.getCategoryLevel1List(),
      crawlerApi.getCategoryLevel2List()
    ])
    
    const level1Data = level1Response.data || []
    const level2Data = level2Response.data || []
    const level1Count = level1Data.length || 0
    const level2Count = level2Data.length || 0
    const total = level1Count + level2Count
    
    const oldTotal = categoryStats.value.total
    const change = total - oldTotal
    
    categoryStats.value = {
      total: total,
      change: change,
      changed: change !== 0,
      level1: level1Count,
      level2: level2Count
    }
    
  } catch (error) {
    console.error('Failed to fetch category stats:', error)
    // 设置默认值
    categoryStats.value = {
      total: 0,
      change: 0,
      changed: false,
      level1: 0,
      level2: 0
    }
  }
}

const fetchShopStats = async () => {
  try {
    const response = await crawlerApi.getShopList()
    const shops = response.data || []
    const total = shops.length
    const active = shops.filter((shop: any) => shop.status === 'active').length
    
    const oldTotal = shopStats.value.total
    const change = total - oldTotal
    
    shopStats.value = {
      total: total,
      change: change,
      changed: change !== 0,
      active: active
    }
    
  } catch (error) {
    console.error('Failed to fetch shop stats:', error)
  }
}

const fetchImageStats = async () => {
  try {
    const response = await crawlerApi.getImageStatistics()
    const data = response.data || response
    const newTotal = data.total || 0
    const newTodayAdded = 0  // 暂时设为0，因为API没有返回今日新增数据
    
    const oldTotal = imageStats.value.total
    const change = newTotal - oldTotal
    
    imageStats.value = {
      total: newTotal,
      change: change,
      changed: change !== 0,
      todayAdded: newTodayAdded
    }
    
  } catch (error) {
    console.error('Failed to fetch image stats:', error)
  }
}

const fetchQueueStats = async () => {
  try {
    const response = await crawlerApi.getQueueDetails()
    const data = response.data || {}
    
    queueStats.value = {
      total: data.total || 0,
      completed: data.completed || 0,
      waiting: data.waiting || 0,
      processing: data.processing || 0
    }
    
  } catch (error) {
    console.error('Failed to fetch queue stats:', error)
  }
}

const fetchTrendData = async () => {
  try {
    const response = await crawlerApi.getRealTimeStats()
    const data = response.data || {}
    
    trendData.value = {
      hourlyProducts: data.hourlyProducts || 0,
      processingRate: data.processingRate || 0,
      successRate: data.successRate || 95
    }
    
  } catch (error) {
    console.error('Failed to fetch trend data:', error)
  }
}

const addChange = (type: string, action: 'add' | 'update' | 'delete', title: string, details: string) => {
  const change: DataChange = {
    type,
    action,
    title,
    details,
    timestamp: new Date().toISOString()
  }
  
  recentChanges.value.unshift(change)
  
  // 只保留最近20条变化记录
  if (recentChanges.value.length > 20) {
    recentChanges.value = recentChanges.value.slice(0, 20)
  }
}

const refreshAll = async () => {
  await Promise.all([
    fetchProductStats(),
    fetchCategoryStats(), 
    fetchShopStats(),
    fetchImageStats(),
    fetchQueueStats(),
    fetchTrendData()
  ])
  message.success('数据已刷新')
}

const toggleMonitoring = (value: boolean) => {
  if (value) {
    startMonitoring()
  } else {
    stopMonitoring()
  }
}

const clearHighlights = () => {
  productStats.value.changed = false
  categoryStats.value.changed = false
  shopStats.value.changed = false
  imageStats.value.changed = false
}

const startMonitoring = () => {
  refreshAll()
  updateTimer = window.setInterval(() => {
    if (isMonitoring.value) {
      refreshAll()
    }
  }, 5000) // 每5秒更新一次
  
  // 清除高亮状态
  highlightTimer = window.setInterval(clearHighlights, 3000)
}

const stopMonitoring = () => {
  if (updateTimer) {
    clearInterval(updateTimer)
    updateTimer = null
  }
  if (highlightTimer) {
    clearInterval(highlightTimer)
    highlightTimer = null
  }
}

onMounted(() => {
  if (isMonitoring.value) {
    startMonitoring()
  }
})

onUnmounted(() => {
  stopMonitoring()
})
</script>

<style scoped lang="scss">
.data-monitor {
  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    
    h3 {
      margin: 0;
    }
    
    .monitor-controls {
      display: flex;
      gap: 12px;
      align-items: center;
    }
  }
  
  .stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 16px;
    margin-bottom: 24px;
    
    .stat-item {
      display: flex;
      align-items: center;
      padding: 16px;
      border: 1px solid #e4e7ed;
      border-radius: 8px;
      background: #ffffff;
      transition: all 0.3s;
      
      &.stat-highlight {
        border-color: #409EFF;
        background: rgba(64, 158, 255, 0.05);
        transform: scale(1.02);
        box-shadow: 0 2px 12px rgba(64, 158, 255, 0.15);
      }
      
      .stat-icon {
        font-size: 32px;
        color: #409EFF;
        margin-right: 16px;
      }
      
      .stat-content {
        flex: 1;
        
        .stat-value {
          font-size: 24px;
          font-weight: bold;
          color: #303133;
          line-height: 1;
          margin-bottom: 4px;
          
          .change-indicator {
            font-size: 14px;
            margin-left: 8px;
            
            &.positive {
              color: #67C23A;
            }
            
            &.negative {
              color: #F56C6C;
            }
          }
        }
        
        .stat-label {
          font-size: 12px;
          color: #909399;
          margin-bottom: 4px;
        }
        
        .stat-trend {
          font-size: 11px;
          color: #606266;
        }
      }
    }
  }
  
  .queue-status,
  .recent-changes,
  .trend-chart {
    margin-bottom: 24px;
    
    h4 {
      margin: 0 0 12px 0;
      color: #303133;
      font-size: 14px;
    }
  }
  
  .queue-stats {
    .queue-item {
      .queue-info {
        display: flex;
        justify-content: space-between;
        margin-top: 8px;
        font-size: 12px;
        color: #606266;
        
        .queue-detail {
          color: #909399;
        }
      }
    }
  }
  
  .changes-list {
    max-height: 300px;
    overflow-y: auto;
    
    .empty-changes {
      text-align: center;
      color: #909399;
    }
    
    .change-item {
      display: flex;
      align-items: flex-start;
      padding: 8px 0;
      border-bottom: 1px solid #f0f0f0;
      
      &:last-child {
        border-bottom: none;
      }
      
      .change-icon {
        font-size: 16px;
        margin-right: 12px;
        margin-top: 2px;
        
        .change-add & {
          color: #67C23A;
        }
        
        .change-update & {
          color: #E6A23C;
        }
        
        .change-delete & {
          color: #F56C6C;
        }
      }
      
      .change-content {
        flex: 1;
        
        .change-title {
          font-size: 13px;
          color: #303133;
          font-weight: 500;
          margin-bottom: 2px;
        }
        
        .change-details {
          font-size: 12px;
          color: #606266;
          margin-bottom: 2px;
        }
        
        .change-time {
          font-size: 11px;
          color: #909399;
        }
      }
    }
  }
  
  .trend-stats {
    display: flex;
    justify-content: space-around;
    
    .trend-item {
      text-align: center;
      
      .trend-label {
        display: block;
        font-size: 12px;
        color: #909399;
        margin-bottom: 4px;
      }
      
      .trend-value {
        font-size: 16px;
        font-weight: bold;
        color: #409EFF;
      }
    }
  }
}

@media (max-width: 768px) {
  .data-monitor {
    .stats-grid {
      grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      gap: 12px;
      
      .stat-item {
        .stat-icon {
          font-size: 24px;
          margin-right: 12px;
        }
        
        .stat-value {
          font-size: 18px;
        }
      }
    }
    
    .trend-stats {
      flex-direction: column;
      gap: 12px;
    }
  }
}
</style>