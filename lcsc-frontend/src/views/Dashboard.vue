<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <a-row :gutter="[20, 20]" class="mb-4">
      <a-col :span="6">
        <a-card class="stats-card" :hoverable="true">
          <div class="stats-content">
            <div class="stats-icon products">
              <ShoppingOutlined />
            </div>
            <div class="stats-text">
              <div class="stats-number">{{ statistics.totalProducts || 0 }}</div>
              <div class="stats-label">总产品数</div>
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stats-card" :hoverable="true">
          <div class="stats-content">
            <div class="stats-icon categories">
              <AppstoreOutlined />
            </div>
            <div class="stats-text">
              <div class="stats-number">{{ statistics.totalLevel1Categories || 0 }}</div>
              <div class="stats-label">一级分类</div>
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stats-card" :hoverable="true">
          <div class="stats-content">
            <div class="stats-icon shops">
              <ShopOutlined />
            </div>
            <div class="stats-text">
              <div class="stats-number">{{ statistics.totalShops || 0 }}</div>
              <div class="stats-label">店铺数量</div>
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stats-card" :hoverable="true">
          <div class="stats-content">
            <div class="stats-icon images">
              <PictureOutlined />
            </div>
            <div class="stats-text">
              <div class="stats-number">{{ statistics.totalImageLinks || 0 }}</div>
              <div class="stats-label">图片链接</div>
            </div>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 详细统计和系统状态 -->
    <a-row :gutter="[20, 20]">
      <a-col :span="8">
        <a-card class="chart-card">
          <template #title>
            <div class="card-header">
              <span>产品分布</span>
              <a-button type="text" @click="refreshStatistics">
                <template #icon>
                  <ReloadOutlined />
                </template>
                刷新
              </a-button>
            </div>
          </template>
          <div class="chart-container">
            <Doughnut 
              :data="stockChartData" 
              :options="stockChartOptions"
              v-if="statistics.totalProducts > 0"
            />
            <div v-else class="no-data">
              <a-empty description="暂无数据" :image-size="60" />
            </div>
          </div>
        </a-card>
      </a-col>

      <a-col :span="8">
        <a-card class="chart-card">
          <template #title>
            <div class="card-header">
              <span>爬虫状态监控</span>
              <a-button type="text" @click="refreshCrawlerStatus">
                <template #icon>
                  <MonitorOutlined />
                </template>
                刷新
              </a-button>
            </div>
          </template>
          <div class="crawler-status">
            <div class="status-indicator-wrapper">
              <div class="status-circle" :class="{ 
                'status-running': crawlerStatus.isRunning,
                'status-stopped': !crawlerStatus.isRunning 
              }">
                <div class="status-pulse" v-if="crawlerStatus.isRunning"></div>
              </div>
              <div class="status-text">
                <div class="status-title">爬虫系统</div>
                <div class="status-desc">{{ crawlerStatus.isRunning ? '运行中' : '已停止' }}</div>
              </div>
            </div>
            <div class="queue-mini-stats">
              <div class="mini-stat">
                <span class="mini-label">待处理</span>
                <span class="mini-value pending">{{ crawlerStatus.queueStatus?.pending || 0 }}</span>
              </div>
              <div class="mini-stat">
                <span class="mini-label">处理中</span>
                <span class="mini-value processing">{{ crawlerStatus.queueStatus?.processing || 0 }}</span>
              </div>
              <div class="mini-stat">
                <span class="mini-label">已完成</span>
                <span class="mini-value completed">{{ crawlerStatus.queueStatus?.completed || 0 }}</span>
              </div>
              <div class="mini-stat">
                <span class="mini-label">失败</span>
                <span class="mini-value failed">{{ crawlerStatus.queueStatus?.failed || 0 }}</span>
              </div>
            </div>
          </div>
        </a-card>
      </a-col>

      <a-col :span="8">
        <a-card class="chart-card">
          <template #title>
            <div class="card-header">
              <span>系统状态</span>
              <a-button type="text" @click="checkSystemHealth">
                <template #icon>
                  <CheckCircleOutlined />
                </template>
                检查
              </a-button>
            </div>
          </template>
          <div class="system-status">
            <div class="status-item">
              <div class="status-indicator" :class="{ 'status-online': systemHealth }"></div>
              <div class="status-text">
                <div class="status-title">后端服务</div>
                <div class="status-desc">{{ systemHealth ? '运行正常' : '连接异常' }}</div>
              </div>
            </div>
            <div class="status-item">
              <div class="status-indicator status-online"></div>
              <div class="status-text">
                <div class="status-title">前端应用</div>
                <div class="status-desc">运行正常</div>
              </div>
            </div>
            <div class="status-item">
              <div class="status-indicator" :class="{ 'status-online': databaseConnected }"></div>
              <div class="status-text">
                <div class="status-title">数据库连接</div>
                <div class="status-desc">{{ databaseConnected ? '连接正常' : '连接异常' }}</div>
              </div>
            </div>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 数据可视化图表 -->
    <a-row :gutter="[20, 20]" class="mt-4">
      <a-col :span="12">
        <a-card class="chart-card">
          <template #title>
            <div class="card-header">
              <span>分类产品统计</span>
              <a-button type="text" @click="refreshCategoryStats">
                <template #icon>
                  <BarChartOutlined />
                </template>
                刷新
              </a-button>
            </div>
          </template>
          <div class="chart-container">
            <Bar 
              :data="categoryChartData" 
              :options="categoryChartOptions"
              v-if="categoryStats.length > 0"
            />
            <div v-else class="no-data">
              <a-empty description="暂无分类统计数据" :image-size="60" />
            </div>
          </div>
        </a-card>
      </a-col>

      <a-col :span="12">
        <a-card class="chart-card">
          <template #title>
            <div class="card-header">
              <span>近期爬取趋势</span>
              <a-button type="text" @click="refreshTrendData">
                <template #icon>
                  <LineChartOutlined />
                </template>
                刷新
              </a-button>
            </div>
          </template>
          <div class="chart-container">
            <Line 
              :data="trendChartData" 
              :options="trendChartOptions"
              v-if="trendData.length > 0"
            />
            <div v-else class="no-data">
              <a-empty description="暂无趋势数据" :image-size="60" />
            </div>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 快速操作 -->
    <a-row :gutter="[20, 20]" class="mt-4">
      <a-col :span="24">
        <a-card>
          <template #title>
            <div class="card-header">
              <span>快速操作</span>
            </div>
          </template>
          <div class="quick-actions">
            <a-button type="primary" @click="goToPage('/products')">
              <template #icon>
                <ShoppingOutlined />
              </template>
              产品管理
            </a-button>
            <a-button type="default" @click="goToPage('/shops')" class="btn-success">
              <template #icon>
                <ShopOutlined />
              </template>
              店铺管理
            </a-button>
            <a-button type="default" @click="goToPage('/categories')" class="btn-warning">
              <template #icon>
                <AppstoreOutlined />
              </template>
              分类管理
            </a-button>
            <a-button type="default" @click="goToPage('/images')" class="btn-info">
              <template #icon>
                <PictureOutlined />
              </template>
              图片管理
            </a-button>
            <a-button danger @click="goToPage('/crawler')">
              <template #icon>
                <CloudDownloadOutlined />
              </template>
              爬虫控制
            </a-button>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 系统信息 -->
    <a-row :gutter="[20, 20]" class="mt-4">
      <a-col :span="24">
        <a-card>
          <template #title>
            <div class="card-header">
              <span>系统信息</span>
            </div>
          </template>
          <div class="system-info">
            <a-descriptions :column="2" bordered>
              <a-descriptions-item label="系统版本">v1.0.0</a-descriptions-item>
              <a-descriptions-item label="构建时间">{{ buildTime }}</a-descriptions-item>
              <a-descriptions-item label="技术栈">Vue3 + Ant Design Vue + TypeScript</a-descriptions-item>
              <a-descriptions-item label="后端框架">Spring Boot + MyBatis Plus</a-descriptions-item>
              <a-descriptions-item label="数据库">MySQL 8.0</a-descriptions-item>
              <a-descriptions-item label="部署环境">开发环境</a-descriptions-item>
            </a-descriptions>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { 
  ShoppingOutlined,
  AppstoreOutlined,
  ShopOutlined,
  PictureOutlined,
  ReloadOutlined,
  MonitorOutlined,
  CloudDownloadOutlined,
  CheckCircleOutlined,
  BarChartOutlined,
  LineChartOutlined
} from '@ant-design/icons-vue'
import { 
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend,
  ArcElement
} from 'chart.js'
import { Bar, Line, Doughnut } from 'vue-chartjs'
import { getSystemStatistics, getSystemHealth } from '@/api/system'
import { crawlerApi } from '@/api/crawler'

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend,
  ArcElement
)

const router = useRouter()

// 响应式数据
const statistics = reactive({
  totalProducts: 0,
  productsWithStock: 0,
  productsWithoutStock: 0,
  totalLevel1Categories: 0,
  totalLevel2Categories: 0,
  totalShops: 0,
  totalImageLinks: 0
})

const systemHealth = ref(false)
const databaseConnected = ref(false)
const buildTime = ref(new Date().toLocaleString('zh-CN'))

// 爬虫状态数据
const crawlerStatus = reactive({
  isRunning: false,
  queueStatus: {
    pending: 0,
    processing: 0,
    completed: 0,
    failed: 0
  }
})

// 图表数据
const categoryStats = ref([])
const trendData = ref([])

// 定时器
let statusUpdateTimer: number | null = null

// 图表配置
const stockChartData = computed(() => ({
  labels: ['有库存', '无库存'],
  datasets: [{
    data: [statistics.productsWithStock || 0, statistics.productsWithoutStock || 0],
    backgroundColor: ['#52c41a', '#ff4d4f'],
    borderColor: ['#52c41a', '#ff4d4f'],
    borderWidth: 2
  }]
}))

const stockChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      position: 'bottom' as const,
      labels: {
        padding: 20,
        font: {
          size: 12
        }
      }
    }
  }
}

const categoryChartData = computed(() => ({
  labels: categoryStats.value.map((item: any) => item.name || '未知'),
  datasets: [{
    label: '产品数量',
    data: categoryStats.value.map((item: any) => item.count || 0),
    backgroundColor: [
      'rgba(54, 162, 235, 0.8)',
      'rgba(255, 99, 132, 0.8)',
      'rgba(255, 205, 86, 0.8)',
      'rgba(75, 192, 192, 0.8)',
      'rgba(153, 102, 255, 0.8)',
      'rgba(255, 159, 64, 0.8)'
    ],
    borderColor: [
      'rgba(54, 162, 235, 1)',
      'rgba(255, 99, 132, 1)',
      'rgba(255, 205, 86, 1)',
      'rgba(75, 192, 192, 1)',
      'rgba(153, 102, 255, 1)',
      'rgba(255, 159, 64, 1)'
    ],
    borderWidth: 1
  }]
}))

const categoryChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      display: false
    }
  },
  scales: {
    y: {
      beginAtZero: true
    }
  }
}

const trendChartData = computed(() => ({
  labels: trendData.value.map((item: any) => item.date || ''),
  datasets: [{
    label: '爬取数量',
    data: trendData.value.map((item: any) => item.count || 0),
    borderColor: 'rgb(75, 192, 192)',
    backgroundColor: 'rgba(75, 192, 192, 0.2)',
    tension: 0.4
  }]
}))

const trendChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      display: false
    }
  },
  scales: {
    y: {
      beginAtZero: true
    }
  }
}

// 方法
const fetchStatistics = async () => {
  try {
    const data = await getSystemStatistics()
    Object.assign(statistics, data)
    databaseConnected.value = true
  } catch (error) {
    console.error('获取统计数据失败:', error)
    message.error('获取统计数据失败')
    databaseConnected.value = false
  }
}

const checkSystemHealth = async () => {
  try {
    await getSystemHealth()
    systemHealth.value = true
    message.success('系统运行正常')
  } catch (error) {
    systemHealth.value = false
    message.error('系统连接异常')
  }
}

const refreshStatistics = () => {
  fetchStatistics()
  message.success('统计数据已刷新')
}

const getStockPercentage = (value: number) => {
  const total = statistics.totalProducts
  if (total === 0) return 0
  return Math.round((value / total) * 100)
}

const refreshCrawlerStatus = async () => {
  try {
    const status = await crawlerApi.getStatus()
    Object.assign(crawlerStatus, status)
  } catch (error) {
    console.error('获取爬虫状态失败:', error)
  }
}

const refreshCategoryStats = async () => {
  try {
    // 模拟分类统计数据，实际应该从后端API获取
    categoryStats.value = [
      { name: '电阻', count: statistics.totalProducts * 0.3 },
      { name: '电容', count: statistics.totalProducts * 0.25 },
      { name: '芯片', count: statistics.totalProducts * 0.2 },
      { name: '传感器', count: statistics.totalProducts * 0.15 },
      { name: '其他', count: statistics.totalProducts * 0.1 }
    ]
  } catch (error) {
    console.error('获取分类统计失败:', error)
  }
}

const refreshTrendData = async () => {
  try {
    // 模拟趋势数据，实际应该从后端API获取
    const dates = []
    const counts = []
    for (let i = 6; i >= 0; i--) {
      const date = new Date()
      date.setDate(date.getDate() - i)
      dates.push(date.toLocaleDateString('zh-CN'))
      counts.push(Math.floor(Math.random() * 1000) + 500)
    }
    trendData.value = dates.map((date, index) => ({
      date,
      count: counts[index]
    }))
  } catch (error) {
    console.error('获取趋势数据失败:', error)
  }
}

const startPolling = () => {
  statusUpdateTimer = window.setInterval(async () => {
    try {
      await Promise.all([
        refreshCrawlerStatus(),
        checkSystemHealth()
      ])
    } catch (error) {
      console.error('定时更新失败:', error)
    }
  }, 5000)
}

const stopPolling = () => {
  if (statusUpdateTimer) {
    clearInterval(statusUpdateTimer)
    statusUpdateTimer = null
  }
}

const goToPage = (path: string) => {
  router.push(path)
}

// 生命周期
onMounted(async () => {
  await fetchStatistics()
  await checkSystemHealth()
  await refreshCrawlerStatus()
  await refreshCategoryStats()
  await refreshTrendData()
  
  // 启动定时轮询
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.dashboard {
  padding: 0;
}

.mb-4 {
  margin-bottom: 16px;
}

.mt-4 {
  margin-top: 16px;
}

.stats-card {
  transition: all 0.3s ease;
  border-radius: 8px;
  overflow: hidden;
}

.stats-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.stats-content {
  display: flex;
  align-items: center;
  padding: 20px;
}

.stats-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  font-size: 24px;
  color: white;
}

.stats-icon.products {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stats-icon.categories {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.stats-icon.shops {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.stats-icon.images {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
}

.stats-text {
  flex: 1;
}

.stats-number {
  font-size: 32px;
  font-weight: bold;
  color: #262626;
  line-height: 1;
}

.stats-label {
  font-size: 14px;
  color: #8c8c8c;
  margin-top: 4px;
}

.chart-card {
  min-height: 280px;
}

.chart-container {
  height: 200px;
  position: relative;
}

.no-data {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

.crawler-status {
  padding: 16px 0;
}

.status-indicator-wrapper {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
  gap: 16px;
}

.status-circle {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  transition: all 0.3s ease;
}

.status-circle.status-running {
  background: linear-gradient(135deg, #52c41a, #73d13d);
  box-shadow: 0 0 20px rgba(82, 196, 26, 0.4);
}

.status-circle.status-stopped {
  background: linear-gradient(135deg, #ff4d4f, #ff7875);
}

.status-pulse {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: rgba(82, 196, 26, 0.4);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  100% {
    transform: scale(1.4);
    opacity: 0;
  }
}

.queue-mini-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.mini-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #f0f0f0;
}

.mini-label {
  font-size: 11px;
  color: #8c8c8c;
  margin-bottom: 4px;
}

.mini-value {
  font-size: 16px;
  font-weight: 600;
}

.mini-value.pending {
  color: #1890ff;
}

.mini-value.processing {
  color: #faad14;
}

.mini-value.completed {
  color: #52c41a;
}

.mini-value.failed {
  color: #ff4d4f;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.stock-stats {
  display: flex;
  justify-content: space-around;
  margin-bottom: 24px;
}

.stock-item {
  text-align: center;
}

.stock-label {
  font-size: 14px;
  color: #8c8c8c;
  margin-bottom: 8px;
}

.stock-value {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 4px;
}

.stock-value.stock-available {
  color: #52c41a;
}

.stock-value.stock-unavailable {
  color: #ff4d4f;
}

.stock-percentage {
  font-size: 12px;
  color: #8c8c8c;
}

.stock-progress {
  margin-top: 16px;
}

.progress-text {
  text-align: center;
  margin-top: 8px;
  font-size: 14px;
  color: #595959;
}

.system-status {
  padding: 16px 0;
}

.status-item {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}

.status-indicator {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background-color: #ff4d4f;
  margin-right: 12px;
}

.status-indicator.status-online {
  background-color: #52c41a;
}

.status-text {
  flex: 1;
}

.status-title {
  font-size: 14px;
  color: #262626;
  font-weight: 500;
}

.status-desc {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 2px;
}

.quick-actions {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.quick-actions .ant-btn {
  flex: 1;
  min-width: 120px;
  height: 40px;
}

.btn-success {
  border-color: #52c41a;
  color: #52c41a;
}

.btn-success:hover {
  border-color: #73d13d;
  color: #73d13d;
}

.btn-warning {
  border-color: #faad14;
  color: #faad14;
}

.btn-warning:hover {
  border-color: #ffc53d;
  color: #ffc53d;
}

.btn-info {
  border-color: #1890ff;
  color: #1890ff;
}

.btn-info:hover {
  border-color: #40a9ff;
  color: #40a9ff;
}

.system-info {
  padding: 16px 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .stats-content {
    padding: 16px;
  }
  
  .stats-icon {
    width: 50px;
    height: 50px;
    font-size: 20px;
  }
  
  .stats-number {
    font-size: 24px;
  }
  
  .quick-actions {
    flex-direction: column;
  }
  
  .quick-actions .ant-btn {
    min-width: auto;
  }
}
</style>
