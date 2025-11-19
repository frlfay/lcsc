<template>
  <div class="data-visualization">
    <!-- 主仪表盘 -->
    <div class="dashboard-header">
      <h2>立创爬虫数据分析仪表盘</h2>
      <div class="dashboard-controls">
        <a-range-picker
          v-model:value="timeRange"
          show-time
          format="YYYY-MM-DD HH:mm:ss"
          :placeholder="['开始时间', '结束时间']"
          @change="onTimeRangeChange"
          size="small"
        />
        <a-button @click="refreshData" :loading="loading" type="primary" size="small">
          <template #icon><ReloadOutlined /></template>
          刷新数据
        </a-button>
        <a-button @click="exportData" size="small">
          <template #icon><DownloadOutlined /></template>
          导出报告
        </a-button>
      </div>
    </div>

    <!-- 核心指标卡片组 -->
    <div class="metrics-grid">
      <a-card class="metric-card" :hoverable="true">
        <div class="metric-item">
          <div class="metric-icon success">
            <LineChartOutlined />
          </div>
          <div class="metric-content">
            <div class="metric-value">{{ formatNumber(totalProducts) }}</div>
            <div class="metric-label">累计产品数</div>
            <div class="metric-trend" :class="productTrend.type">
              <ArrowUpOutlined v-if="productTrend.type === 'up'" />
              <ArrowDownOutlined v-else />
              {{ productTrend.value }}%
            </div>
          </div>
        </div>
      </a-card>

      <a-card class="metric-card" :hoverable="true">
        <div class="metric-item">
          <div class="metric-icon info">
            <ClockCircleOutlined />
          </div>
          <div class="metric-content">
            <div class="metric-value">{{ formatNumber(totalApiCalls) }}</div>
            <div class="metric-label">API调用次数</div>
            <div class="metric-trend" :class="apiTrend.type">
              <ArrowUpOutlined v-if="apiTrend.type === 'up'" />
              <ArrowDownOutlined v-else />
              {{ apiTrend.value }}%
            </div>
          </div>
        </div>
      </a-card>

      <a-card class="metric-card" :hoverable="true">
        <div class="metric-item">
          <div class="metric-icon warning">
            <ExclamationCircleOutlined />
          </div>
          <div class="metric-content">
            <div class="metric-value">{{ successRate }}%</div>
            <div class="metric-label">成功率</div>
            <div class="metric-trend" :class="successTrend.type">
              <ArrowUpOutlined v-if="successTrend.type === 'up'" />
              <ArrowDownOutlined v-else />
              {{ successTrend.value }}%
            </div>
          </div>
        </div>
      </a-card>

      <a-card class="metric-card" :hoverable="true">
        <div class="metric-item">
          <div class="metric-icon danger">
            <MonitorOutlined />
          </div>
          <div class="metric-content">
            <div class="metric-value">{{ averageResponseTime }}ms</div>
            <div class="metric-label">平均响应时间</div>
            <div class="metric-trend" :class="responseTrend.type">
              <ArrowUpOutlined v-if="responseTrend.type === 'up'" />
              <ArrowDownOutlined v-else />
              {{ responseTrend.value }}%
            </div>
          </div>
        </div>
      </a-card>
    </div>

    <!-- 图表展示区域 -->
    <div class="charts-container">
      <a-row :gutter="24">
        <a-col :span="12">
          <a-card title="产品爬取趋势" :hoverable="true">
            <template #extra>
              <a-radio-group v-model:value="productChartPeriod" size="small" @change="updateProductChart">
                <a-radio-button value="24h">24小时</a-radio-button>
                <a-radio-button value="7d">7天</a-radio-button>
                <a-radio-button value="30d">30天</a-radio-button>
              </a-radio-group>
            </template>
            <div ref="productChart" class="chart-container"></div>
          </a-card>
        </a-col>
        
        <a-col :span="12">
          <a-card title="API响应时间分布" :hoverable="true">
            <template #extra>
              <a-select v-model:value="responseTimeEndpoint" size="small" @change="updateResponseTimeChart">
                <a-select-option value="all">所有端点</a-select-option>
                <a-select-option value="catalog">分类列表</a-select-option>
                <a-select-option value="param">筛选条件</a-select-option>
                <a-select-option value="product">产品列表</a-select-option>
              </a-select>
            </template>
            <div ref="responseTimeChart" class="chart-container"></div>
          </a-card>
        </a-col>
      </a-row>

      <a-row :gutter="24" style="margin-top: 24px;">
        <a-col :span="8">
          <a-card title="错误类型分布" :hoverable="true">
            <div ref="errorTypeChart" class="chart-container"></div>
          </a-card>
        </a-col>
        
        <a-col :span="8">
          <a-card title="分类爬取进度" :hoverable="true">
            <div ref="categoryProgressChart" class="chart-container"></div>
          </a-card>
        </a-col>
        
        <a-col :span="8">
          <a-card title="系统资源使用" :hoverable="true">
            <div ref="systemResourceChart" class="chart-container"></div>
          </a-card>
        </a-col>
      </a-row>
    </div>

    <!-- 实时数据表格 -->
    <div class="data-table-section">
      <a-card title="实时监控数据" :hoverable="true">
        <template #extra>
          <div class="table-controls">
            <a-input
              v-model:value="searchKeyword"
              placeholder="搜索..."
              size="small"
              style="width: 200px;"
              allow-clear
            >
              <template #prefix>
                <SearchOutlined />
              </template>
            </a-input>
            <a-select v-model:value="statusFilter" size="small" style="margin-left: 8px; width: 120px;">
              <a-select-option value="">所有状态</a-select-option>
              <a-select-option value="running">运行中</a-select-option>
              <a-select-option value="completed">已完成</a-select-option>
              <a-select-option value="failed">失败</a-select-option>
            </a-select>
          </div>
        </template>
        
        <a-table 
          :data-source="filteredTableData" 
          :columns="tableColumns"
          :loading="tableLoading"
          :pagination="{
            current: currentPage,
            pageSize: pageSize,
            total: totalRecords,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total: number) => `共 ${total} 条记录`,
            onChange: handleCurrentPageChange,
            onShowSizeChange: handlePageSizeChange
          }"
          @change="handleTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'timestamp'">
              {{ formatTime(record.timestamp) }}
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="getStatusTagColor(record.status)">
                {{ record.status }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'responseTime'">
              <span :class="getResponseTimeClass(record.responseTime)">
                {{ record.responseTime }}ms
              </span>
            </template>
            <template v-else-if="column.key === 'errorMessage'">
              <span v-if="record.errorMessage" class="error-message">
                {{ record.errorMessage }}
              </span>
              <span v-else class="success-message">成功</span>
            </template>
          </template>
        </a-table>
      </a-card>
    </div>

    <!-- 性能洞察面板 -->
    <div class="insights-panel">
      <a-card title="性能洞察" :hoverable="true">
        <div class="insights-content">
          <div class="insight-item" v-for="insight in performanceInsights" :key="insight.id">
            <div class="insight-icon" :class="insight.severity">
              <InfoCircleOutlined v-if="insight.severity === 'info'" />
              <ExclamationCircleOutlined v-else-if="insight.severity === 'warning'" />
              <CloseCircleOutlined v-else />
            </div>
            <div class="insight-content">
              <div class="insight-title">{{ insight.title }}</div>
              <div class="insight-description">{{ insight.description }}</div>
              <div class="insight-suggestion" v-if="insight.suggestion">
                <strong>建议:</strong> {{ insight.suggestion }}
              </div>
            </div>
          </div>
        </div>
      </a-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed, nextTick } from 'vue'
import * as echarts from 'echarts'
import { 
  ReloadOutlined, DownloadOutlined, LineChartOutlined, ClockCircleOutlined, 
  ExclamationCircleOutlined, MonitorOutlined, ArrowUpOutlined, ArrowDownOutlined, 
  SearchOutlined, InfoCircleOutlined, CloseCircleOutlined
} from '@ant-design/icons-vue'
import { visualizationApi } from '@/api/visualization'
import { message } from 'ant-design-vue'
import type { ErrorDistribution, CategoryStats } from '@/api/visualization'

interface PerformanceMetrics {
  totalProducts: number
  requestCount: number
  successRate: number
  avgResponseTime: number
}

interface TableDataItem {
  id: number
  timestamp: string
  operation: string
  endpoint: string
  status: string
  responseTime: number
  dataCount: number
  errorMessage: string | null
}

interface InsightItem {
  id: number
  severity: string
  title: string
  description: string
  suggestion: string | null
}

// 响应式数据
const loading = ref(false)
const tableLoading = ref(false)
const timeRange = ref([])
const searchKeyword = ref('')
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const totalRecords = ref(0)

// 图表周期选择
const productChartPeriod = ref('24h')
const responseTimeEndpoint = ref('all')

// 核心指标数据
const totalProducts = ref(0)
const totalApiCalls = ref(0)
const successRate = ref(0)
const averageResponseTime = ref(0)

// 趋势数据
const productTrend = ref({ type: 'up', value: 0 })
const apiTrend = ref({ type: 'up', value: 0 })
const successTrend = ref({ type: 'up', value: 0 })
const responseTrend = ref({ type: 'down', value: 0 })

// 表格数据
const tableData = ref<TableDataItem[]>([])
const performanceInsights = ref<InsightItem[]>([])

// 图表实例
let productChart: echarts.ECharts | null = null
let responseTimeChart: echarts.ECharts | null = null
let errorTypeChart: echarts.ECharts | null = null
let categoryProgressChart: echarts.ECharts | null = null
let systemResourceChart: echarts.ECharts | null = null

// 表格列定义
const tableColumns = [
  {
    title: '时间',
    dataIndex: 'timestamp',
    key: 'timestamp',
    width: 180,
    sorter: true
  },
  {
    title: '操作',
    dataIndex: 'operation',
    key: 'operation',
    width: 120
  },
  {
    title: '端点',
    dataIndex: 'endpoint',
    key: 'endpoint',
    width: 150
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    width: 100
  },
  {
    title: '响应时间',
    dataIndex: 'responseTime',
    key: 'responseTime',
    width: 120,
    sorter: true
  },
  {
    title: '数据量',
    dataIndex: 'dataCount',
    key: 'dataCount',
    width: 100,
    sorter: true
  },
  {
    title: '错误信息',
    dataIndex: 'errorMessage',
    key: 'errorMessage',
    ellipsis: true
  }
]

// 计算属性
const filteredTableData = computed(() => {
  let data = tableData.value
  
  if (searchKeyword.value) {
    data = data.filter((item: any) => 
      item.operation.includes(searchKeyword.value) ||
      item.endpoint.includes(searchKeyword.value) ||
      (item.errorMessage && item.errorMessage.includes(searchKeyword.value))
    )
  }
  
  if (statusFilter.value) {
    data = data.filter((item: any) => item.status === statusFilter.value)
  }
  
  return data
})

// 时间范围快捷选项
const timeRangeShortcuts = [
  {
    text: '最近1小时',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000)
      return [start, end]
    }
  },
  {
    text: '最近6小时',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 6)
      return [start, end]
    }
  },
  {
    text: '最近24小时',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24)
      return [start, end]
    }
  }
]

// 方法定义
const formatNumber = (num: number) => {
  return num.toLocaleString()
}

const formatTime = (timestamp: string) => {
  return new Date(timestamp).toLocaleString()
}

const getStatusTagColor = (status: string) => {
  const colorMap: Record<string, string> = {
    'running': 'processing',
    'completed': 'success',
    'failed': 'error'
  }
  return colorMap[status] || 'default'
}

const getResponseTimeClass = (responseTime: number) => {
  if (responseTime < 100) return 'fast-response'
  if (responseTime < 500) return 'normal-response'
  return 'slow-response'
}

const refreshData = async () => {
  loading.value = true
  try {
    await Promise.all([
      fetchMetrics(),
      fetchTableData(),
      fetchInsights()
    ])
    updateAllCharts()
  } catch (error) {
    message.error('刷新数据失败')
  } finally {
    loading.value = false
  }
}

const fetchMetrics = async () => {
  try {
    const response = await visualizationApi.getPerformanceMetrics() as any
    totalProducts.value = response.totalProducts || 0
    totalApiCalls.value = response.requestCount || response.totalApiCalls || 0
    successRate.value = response.successRate || 0
    averageResponseTime.value = response.avgResponseTime || response.averageResponseTime || 0
    
    // 更新趋势数据 - 使用模拟数据
    productTrend.value = { type: 'up', value: 12.5 }
    apiTrend.value = { type: 'up', value: 8.3 }
    successTrend.value = { type: 'down', value: 2.1 }
    responseTrend.value = { type: 'down', value: 5.2 }
  } catch (error) {
    console.error('获取指标数据失败:', error)
  }
}

const fetchTableData = async () => {
  tableLoading.value = true
  try {
    // 使用现有API获取数据并转换格式
    const response = await visualizationApi.getApiResponseTimes('24h')
    const tableDataFormatted = response.map((item: any, index: number) => ({
      id: index + 1,
      timestamp: new Date().toISOString(),
      operation: getOperationName(item.endpoint),
      endpoint: item.endpoint,
      status: item.errorCount === 0 ? 'completed' : 'failed',
      responseTime: Math.round(item.avgResponseTime),
      dataCount: item.requestCount,
      errorMessage: item.errorCount > 0 ? `错误次数: ${item.errorCount}` : null
    }))
    
    tableData.value = tableDataFormatted
    totalRecords.value = tableDataFormatted.length
  } catch (error) {
    console.error('获取表格数据失败:', error)
    // 使用模拟数据作为后备
    generateMockTableData()
  } finally {
    tableLoading.value = false
  }
}

const fetchInsights = async () => {
  try {
    const response = await visualizationApi.getOptimizationSuggestions() as any[]
    // 转换API响应格式为组件需要的格式
    const formattedInsights: InsightItem[] = response.map((item: any, index: number) => ({
      id: index + 1,
      severity: item.priority?.toLowerCase() || 'info',
      title: item.title || item.category || '性能建议',
      description: item.description || '',
      suggestion: item.actionItems?.join('; ') || item.estimatedImpact || null
    }))
    performanceInsights.value = formattedInsights
  } catch (error) {
    console.error('获取性能洞察失败:', error)
    // 使用模拟数据
    performanceInsights.value = [
      {
        id: 1,
        severity: 'warning',
        title: '内存使用率较高',
        description: '当前内存使用率达到78%，建议优化内存管理',
        suggestion: '考虑增加批处理间隔或减少缓存大小'
      },
      {
        id: 2,
        severity: 'info',
        title: 'API响应时间稳定',
        description: '平均响应时间245ms，处于正常范围内',
        suggestion: null
      }
    ]
  }
}

const updateAllCharts = () => {
  nextTick(() => {
    updateProductChart()
    updateResponseTimeChart()
    updateErrorTypeChart()
    updateCategoryProgressChart()
    updateSystemResourceChart()
  })
}

const updateProductChart = async () => {
  if (!productChart) return
  
  try {
    // 生成模拟数据
    const data = generateProductChartData(productChartPeriod.value)
    const option = {
      tooltip: {
        trigger: 'axis'
      },
      xAxis: {
        type: 'category',
        data: data.categories
      },
      yAxis: {
        type: 'value'
      },
      series: [{
        data: data.values,
        type: 'line',
        smooth: true,
        areaStyle: {}
      }]
    }
    productChart.setOption(option)
  } catch (error) {
    console.error('更新产品趋势图表失败:', error)
  }
}

const updateResponseTimeChart = async () => {
  if (!responseTimeChart) return
  
  try {
    // 生成模拟数据
    const data = generateResponseTimeData(responseTimeEndpoint.value)
    const option = {
      tooltip: {
        trigger: 'item'
      },
      series: [{
        type: 'pie',
        radius: '50%',
        data: data
      }]
    }
    responseTimeChart.setOption(option)
  } catch (error) {
    console.error('更新响应时间图表失败:', error)
  }
}

const updateErrorTypeChart = async () => {
  if (!errorTypeChart) return
  
  try {
    const response = await visualizationApi.getErrorDistribution()
    const option = {
      tooltip: {
        trigger: 'item'
      },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        data: response || []
      }]
    }
    errorTypeChart.setOption(option)
  } catch (error) {
    console.error('更新错误类型图表失败:', error)
  }
}

const updateCategoryProgressChart = async () => {
  if (!categoryProgressChart) return
  
  try {
    const response = await visualizationApi.getCategoryStatistics()
    const option = {
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        }
      },
      xAxis: {
        type: 'category',
        data: response.map((item: any) => item.categoryName) || []
      },
      yAxis: {
        type: 'value'
      },
      series: [{
        data: response.map((item: any) => item.completionRate) || [],
        type: 'bar'
      }]
    }
    categoryProgressChart.setOption(option)
  } catch (error) {
    console.error('更新分类进度图表失败:', error)
  }
}

const updateSystemResourceChart = async () => {
  if (!systemResourceChart) return
  
  try {
    // 生成模拟系统资源数据
    const labels = []
    const cpu = []
    const memory = []
    const disk = []
    
    for (let i = 0; i < 24; i++) {
      labels.push(`${i}:00`)
      cpu.push(Math.floor(Math.random() * 30) + 40)
      memory.push(Math.floor(Math.random() * 20) + 60)
      disk.push(Math.floor(Math.random() * 15) + 30)
    }
    
    const option = {
      tooltip: {
        trigger: 'axis'
      },
      legend: {
        data: ['CPU', '内存', '磁盘']
      },
      xAxis: {
        type: 'category',
        data: labels
      },
      yAxis: {
        type: 'value',
        max: 100
      },
      series: [
        {
          name: 'CPU',
          type: 'line',
          data: cpu
        },
        {
          name: '内存',
          type: 'line',
          data: memory
        },
        {
          name: '磁盘',
          type: 'line',
          data: disk
        }
      ]
    }
    systemResourceChart.setOption(option)
  } catch (error) {
    console.error('更新系统资源图表失败:', error)
  }
}

const exportData = () => {
  message.info('导出功能开发中...')
}

const onTimeRangeChange = (dates: any) => {
  if (dates && dates.length === 2) {
    refreshData()
  }
}

const handleTableChange = (pagination: any, filters: any, sorter: any) => {
  currentPage.value = pagination.current
  pageSize.value = pagination.pageSize
  fetchTableData()
}

const handleCurrentPageChange = (page: number) => {
  currentPage.value = page
  fetchTableData()
}

const handlePageSizeChange = (current: number, size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchTableData()
}

// 初始化图表
const initCharts = () => {
  nextTick(() => {
    const productChartRef = document.querySelector('.chart-container') as HTMLElement
    if (productChartRef) {
      productChart = echarts.init(productChartRef)
    }
    
    const responseTimeChartRef = document.querySelectorAll('.chart-container')[1] as HTMLElement
    if (responseTimeChartRef) {
      responseTimeChart = echarts.init(responseTimeChartRef)
    }
    
    const errorTypeChartRef = document.querySelectorAll('.chart-container')[2] as HTMLElement
    if (errorTypeChartRef) {
      errorTypeChart = echarts.init(errorTypeChartRef)
    }
    
    const categoryProgressChartRef = document.querySelectorAll('.chart-container')[3] as HTMLElement
    if (categoryProgressChartRef) {
      categoryProgressChart = echarts.init(categoryProgressChartRef)
    }
    
    const systemResourceChartRef = document.querySelectorAll('.chart-container')[4] as HTMLElement
    if (systemResourceChartRef) {
      systemResourceChart = echarts.init(systemResourceChartRef)
    }
    
    updateAllCharts()
  })
}

// 辅助函数
const getOperationName = (endpoint: string): string => {
  const operationMap: Record<string, string> = {
    '/api/products': '产品爬取',
    '/api/shops': '店铺管理',
    '/api/visualization': '数据可视化',
    '/api/auth': '用户认证'
  }
  return operationMap[endpoint] || '未知操作'
}

const generateMockTableData = () => {
  const mockData: TableDataItem[] = []
  for (let i = 1; i <= 10; i++) {
    mockData.push({
      id: i,
      timestamp: new Date(Date.now() - Math.random() * 86400000).toISOString(),
      operation: ['产品爬取', '店铺管理', '数据分析'][Math.floor(Math.random() * 3)],
      endpoint: ['/api/products', '/api/shops', '/api/visualization'][Math.floor(Math.random() * 3)],
      status: Math.random() > 0.2 ? 'completed' : 'failed',
      responseTime: Math.floor(Math.random() * 500) + 100,
      dataCount: Math.floor(Math.random() * 1000) + 50,
      errorMessage: Math.random() > 0.8 ? '网络超时' : null
    })
  }
  tableData.value = mockData
  totalRecords.value = mockData.length
}

const generateProductChartData = (period: string) => {
  const categories = []
  const values = []
  const count = period === '7d' ? 7 : period === '30d' ? 30 : 24
  
  for (let i = 0; i < count; i++) {
    if (period === '24h') {
      categories.push(`${i}:00`)
    } else {
      categories.push(`${i + 1}日`)
    }
    values.push(Math.floor(Math.random() * 100) + 50)
  }
  
  return { categories, values }
}

const generateResponseTimeData = (endpoint: string) => {
  return [
    { name: '< 100ms', value: Math.floor(Math.random() * 30) + 20 },
    { name: '100-300ms', value: Math.floor(Math.random() * 40) + 30 },
    { name: '300-500ms', value: Math.floor(Math.random() * 20) + 10 },
    { name: '> 500ms', value: Math.floor(Math.random() * 10) + 5 }
  ]
}

// 生命周期
onMounted(() => {
  initCharts()
  refreshData()
  
  // 定时刷新数据
  const interval = setInterval(() => {
    fetchTableData()
    fetchMetrics()
  }, 30000) // 30秒刷新一次
  
  onUnmounted(() => {
    clearInterval(interval)
    productChart?.dispose()
    responseTimeChart?.dispose()
    errorTypeChart?.dispose()
    categoryProgressChart?.dispose()
    systemResourceChart?.dispose()
  })
})
</script>

<style scoped>
.data-visualization {
  padding: 24px;
  background: #f5f5f5;
  min-height: 100vh;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 16px 24px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.dashboard-header h2 {
  margin: 0;
  color: #1890ff;
  font-size: 24px;
  font-weight: 600;
}

.dashboard-controls {
  display: flex;
  gap: 12px;
  align-items: center;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
  margin-bottom: 24px;
}

.metric-card {
  border-radius: 8px;
  overflow: hidden;
}

.metric-item {
  display: flex;
  align-items: center;
  padding: 8px;
}

.metric-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-right: 16px;
}

.metric-icon.success {
  background: linear-gradient(135deg, #52c41a, #73d13d);
  color: white;
}

.metric-icon.info {
  background: linear-gradient(135deg, #1890ff, #40a9ff);
  color: white;
}

.metric-icon.warning {
  background: linear-gradient(135deg, #faad14, #ffc53d);
  color: white;
}

.metric-icon.danger {
  background: linear-gradient(135deg, #ff4d4f, #ff7875);
  color: white;
}

.metric-content {
  flex: 1;
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #262626;
  line-height: 1;
  margin-bottom: 4px;
}

.metric-label {
  font-size: 14px;
  color: #8c8c8c;
  margin-bottom: 8px;
}

.metric-trend {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
}

.metric-trend.up {
  color: #52c41a;
}

.metric-trend.down {
  color: #ff4d4f;
}

.charts-container {
  margin-bottom: 24px;
}

.chart-container {
  width: 100%;
  height: 300px;
}

.data-table-section {
  margin-bottom: 24px;
}

.table-controls {
  display: flex;
  gap: 8px;
  align-items: center;
}

.fast-response {
  color: #52c41a;
  font-weight: 500;
}

.normal-response {
  color: #faad14;
  font-weight: 500;
}

.slow-response {
  color: #ff4d4f;
  font-weight: 500;
}

.error-message {
  color: #ff4d4f;
}

.success-message {
  color: #52c41a;
}

.insights-panel {
  margin-bottom: 24px;
}

.insights-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.insight-item {
  display: flex;
  align-items: flex-start;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
  border-left: 4px solid #d9d9d9;
}

.insight-item .insight-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
  font-size: 14px;
}

.insight-item .insight-icon.info {
  background: #e6f7ff;
  color: #1890ff;
  border-color: #1890ff;
}

.insight-item .insight-icon.warning {
  background: #fff7e6;
  color: #faad14;
  border-color: #faad14;
}

.insight-item .insight-icon.error {
  background: #fff2f0;
  color: #ff4d4f;
  border-color: #ff4d4f;
}

.insight-content {
  flex: 1;
}

.insight-title {
  font-weight: 600;
  color: #262626;
  margin-bottom: 4px;
}

.insight-description {
  color: #595959;
  font-size: 14px;
  margin-bottom: 8px;
}

.insight-suggestion {
  color: #8c8c8c;
  font-size: 12px;
}

@media (max-width: 768px) {
  .dashboard-header {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }
  
  .dashboard-controls {
    justify-content: center;
  }
  
  .metrics-grid {
    grid-template-columns: 1fr;
  }
}
</style>