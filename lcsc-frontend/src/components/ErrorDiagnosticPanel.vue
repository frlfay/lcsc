<template>
  <div class="error-diagnostic-panel">
    <!-- 错误统计卡片 -->
    <div class="error-stats-cards">
      <a-row :gutter="20">
        <a-col :span="6">
          <a-card class="error-stat-card critical">
            <div class="stat-content">
              <div class="stat-icon">
                <ExclamationCircleOutlined />
              </div>
              <div class="stat-info">
                <div class="stat-number">{{ errorStats.criticalErrors }}</div>
                <div class="stat-label">严重错误</div>
              </div>
            </div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="error-stat-card warning">
            <div class="stat-content">
              <div class="stat-icon">
                <InfoCircleOutlined />
              </div>
              <div class="stat-info">
                <div class="stat-number">{{ errorStats.warnings }}</div>
                <div class="stat-label">警告</div>
              </div>
            </div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="error-stat-card retry">
            <div class="stat-content">
              <div class="stat-icon">
                <ReloadOutlined />
              </div>
              <div class="stat-info">
                <div class="stat-number">{{ errorStats.retryableErrors }}</div>
                <div class="stat-label">可重试错误</div>
              </div>
            </div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="error-stat-card recovery">
            <div class="stat-content">
              <div class="stat-icon">
                <CheckCircleOutlined />
              </div>
              <div class="stat-info">
                <div class="stat-number">{{ errorStats.recoveredErrors }}</div>
                <div class="stat-label">已恢复</div>
              </div>
            </div>
          </a-card>
        </a-col>
      </a-row>
    </div>

    <!-- 错误趋势图表 -->
    <a-card class="error-trend-card" style="margin-top: 20px;">
      <template #title>
        <div class="card-header">
          <span>错误趋势分析</span>
          <a-select v-model:value="trendTimeRange" size="small" style="width: 120px;">
            <a-select-option value="1h">最近1小时</a-select-option>
            <a-select-option value="6h">最近6小时</a-select-option>
            <a-select-option value="24h">最近24小时</a-select-option>
            <a-select-option value="7d">最近7天</a-select-option>
          </a-select>
        </div>
      </template>
      <div ref="errorTrendChart" class="error-trend-chart"></div>
    </a-card>

    <!-- 错误详情列表 -->
    <a-card class="error-details-card" style="margin-top: 20px;">
      <template #title>
        <div class="card-header">
          <span>错误详情</span>
          <div class="error-filters">
            <a-select v-model:value="errorTypeFilter" placeholder="错误类型" size="small" allow-clear style="width: 150px;">
              <a-select-option value="TIMEOUT">连接超时</a-select-option>
              <a-select-option value="API_ERROR">API错误</a-select-option>
              <a-select-option value="DATA_PROCESSING_ERROR">数据处理错误</a-select-option>
              <a-select-option value="SYSTEM_ERROR">系统错误</a-select-option>
              <a-select-option value="UNKNOWN_ERROR">未知错误</a-select-option>
            </a-select>
            <a-select v-model:value="severityFilter" placeholder="严重程度" size="small" allow-clear style="margin-left: 10px; width: 120px;">
              <a-select-option value="CRITICAL">严重</a-select-option>
              <a-select-option value="WARNING">警告</a-select-option>
              <a-select-option value="INFO">信息</a-select-option>
            </a-select>
            <a-button type="primary" size="small" @click="refreshErrorList" style="margin-left: 10px;">
              <SyncOutlined /> 刷新
            </a-button>
          </div>
        </div>
      </template>
      
      <el-table :data="errorList" v-loading="loading" height="400">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="error-details-expand">
              <el-descriptions title="错误详情" :column="2" border>
                <el-descriptions-item label="任务ID">{{ row.taskId }}</el-descriptions-item>
                <el-descriptions-item label="错误代码">{{ row.errorCode }}</el-descriptions-item>
                <el-descriptions-item label="重试次数">{{ row.retryCount }}</el-descriptions-item>
                <el-descriptions-item label="持续时间">{{ row.durationMs }}ms</el-descriptions-item>
                <el-descriptions-item label="发生时间" :span="2">
                  {{ formatDateTime(row.startTime) }}
                </el-descriptions-item>
                <el-descriptions-item label="错误消息" :span="2">
                  <pre class="error-message">{{ row.message }}</pre>
                </el-descriptions-item>
                <el-descriptions-item label="元数据" :span="2" v-if="row.metadata">
                  <pre class="error-metadata">{{ formatJSON(row.metadata) }}</pre>
                </el-descriptions-item>
              </el-descriptions>
              
              <div class="error-actions" style="margin-top: 15px;">
                <el-button size="small" type="primary" @click="retryTask(row)" :loading="retryLoading">
                  <el-icon><RefreshRight /></el-icon> 重试任务
                </el-button>
                <el-button size="small" @click="ignoreError(row)">
                  <el-icon><CircleClose /></el-icon> 忽略错误
                </el-button>
                <el-button size="small" type="info" @click="viewTaskFlow(row)">
                  <el-icon><View /></el-icon> 查看任务流程
                </el-button>
              </div>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="taskId" label="任务ID" width="200">
          <template #default="{ row }">
            <el-tag size="small">{{ row.taskId.substring(0, 8) }}...</el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="taskType" label="任务类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTaskTypeColor(row.taskType)" size="small">
              {{ getTaskTypeName(row.taskType) }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="step" label="执行步骤" width="150" />
        
        <el-table-column prop="errorCode" label="错误类型" width="140">
          <template #default="{ row }">
            <el-tag :type="getErrorTypeColor(row.errorCode)" size="small">
              {{ getErrorTypeName(row.errorCode) }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="message" label="错误消息" min-width="200" show-overflow-tooltip />
        
        <el-table-column prop="retryCount" label="重试" width="80" align="center">
          <template #default="{ row }">
            <el-badge :value="row.retryCount" :type="row.retryCount > 3 ? 'danger' : 'warning'">
              <el-icon><RefreshRight /></el-icon>
            </el-badge>
          </template>
        </el-table-column>
        
        <el-table-column prop="startTime" label="发生时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.startTime) }}
          </template>
        </el-table-column>
        
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusColor(row.status)" size="small">
              {{ getStatusName(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.currentPage"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 错误模式分析 -->
    <el-card class="error-pattern-card" style="margin-top: 20px;">
      <template #header>
        <span>错误模式分析</span>
      </template>
      <el-row :gutter="20">
        <el-col :span="12">
          <div class="pattern-chart">
            <h4>错误类型分布</h4>
            <div ref="errorTypeChart" style="height: 300px;"></div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="pattern-insights">
            <h4>智能诊断建议</h4>
            <div class="insight-list">
              <div v-for="insight in diagnosticInsights" :key="insight.id" class="insight-item">
                <div class="insight-icon">
                  <el-icon :color="insight.color"><QuestionFilled /></el-icon>
                </div>
                <div class="insight-content">
                  <div class="insight-title">{{ insight.title }}</div>
                  <div class="insight-description">{{ insight.description }}</div>
                  <div class="insight-actions" v-if="insight.actions">
                    <el-button 
                      v-for="action in insight.actions" 
                      :key="action.name"
                      size="small" 
                      :type="action.type"
                      @click="executeAction(action)"
                    >
                      {{ action.name }}
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { 
  ExclamationCircleOutlined, InfoCircleOutlined, ReloadOutlined, CheckCircleOutlined,
  SyncOutlined, CloseCircleOutlined, EyeOutlined, QuestionCircleOutlined
} from '@ant-design/icons-vue'
import * as echarts from 'echarts'
import { connectWebSocket, disconnectWebSocket } from '../utils/websocket'

export default {
  name: 'ErrorDiagnosticPanel',
  components: {
    ExclamationCircleOutlined, InfoCircleOutlined, ReloadOutlined, CheckCircleOutlined,
    SyncOutlined, CloseCircleOutlined, EyeOutlined, QuestionCircleOutlined
  },
  setup() {
    const loading = ref(false)
    const retryLoading = ref(false)
    
    // 错误统计数据
    const errorStats = reactive({
      criticalErrors: 0,
      warnings: 0,
      retryableErrors: 0,
      recoveredErrors: 0
    })
    
    // 过滤条件
    const errorTypeFilter = ref('')
    const severityFilter = ref('')
    const trendTimeRange = ref('6h')
    
    // 错误列表
    const errorList = ref([])
    const pagination = reactive({
      currentPage: 1,
      pageSize: 20,
      total: 0
    })
    
    // 图表实例
    const errorTrendChart = ref(null)
    const errorTypeChart = ref(null)
    let trendChartInstance = null
    let typeChartInstance = null
    
    // WebSocket连接
    let wsConnection = null
    
    // 诊断建议
    const diagnosticInsights = ref([])
    
    // 初始化错误趋势图表
    const initTrendChart = () => {
      nextTick(() => {
        if (errorTrendChart.value && !trendChartInstance) {
          trendChartInstance = echarts.init(errorTrendChart.value)
          
          const option = {
            title: {
              text: '错误发生趋势',
              left: 0,
              textStyle: { fontSize: 14 }
            },
            tooltip: {
              trigger: 'axis',
              axisPointer: { type: 'cross' }
            },
            legend: {
              data: ['严重错误', '警告', '可重试错误'],
              right: 0
            },
            grid: {
              left: '3%',
              right: '4%',
              bottom: '3%',
              containLabel: true
            },
            xAxis: {
              type: 'time',
              boundaryGap: false
            },
            yAxis: {
              type: 'value',
              name: '错误数量'
            },
            series: [
              {
                name: '严重错误',
                type: 'line',
                data: [],
                itemStyle: { color: '#f56c6c' },
                areaStyle: { color: 'rgba(245, 108, 108, 0.1)' }
              },
              {
                name: '警告',
                type: 'line',
                data: [],
                itemStyle: { color: '#e6a23c' },
                areaStyle: { color: 'rgba(230, 162, 60, 0.1)' }
              },
              {
                name: '可重试错误',
                type: 'line',
                data: [],
                itemStyle: { color: '#409eff' },
                areaStyle: { color: 'rgba(64, 158, 255, 0.1)' }
              }
            ]
          }
          
          trendChartInstance.setOption(option)
        }
      })
    }
    
    // 初始化错误类型图表
    const initTypeChart = () => {
      nextTick(() => {
        if (errorTypeChart.value && !typeChartInstance) {
          typeChartInstance = echarts.init(errorTypeChart.value)
          
          const option = {
            title: {
              text: '错误类型分布',
              left: 'center',
              textStyle: { fontSize: 14 }
            },
            tooltip: {
              trigger: 'item',
              formatter: '{a} <br/>{b}: {c} ({d}%)'
            },
            series: [
              {
                name: '错误类型',
                type: 'pie',
                radius: ['30%', '70%'],
                avoidLabelOverlap: false,
                label: {
                  show: false,
                  position: 'center'
                },
                emphasis: {
                  label: {
                    show: true,
                    fontSize: '18',
                    fontWeight: 'bold'
                  }
                },
                labelLine: {
                  show: false
                },
                data: []
              }
            ]
          }
          
          typeChartInstance.setOption(option)
        }
      })
    }
    
    // 获取错误统计数据
    const fetchErrorStats = async () => {
      try {
        // 模拟API调用
        const response = await fetch('/api/task-logs/error-stats')
        const data = await response.json()
        
        Object.assign(errorStats, data)
      } catch (error) {
        console.error('获取错误统计失败:', error)
      }
    }
    
    // 获取错误列表
    const fetchErrorList = async () => {
      loading.value = true
      try {
        const params = new URLSearchParams({
          page: pagination.currentPage - 1,
          size: pagination.pageSize,
          status: 'FAILED'
        })
        
        if (errorTypeFilter.value) {
          params.append('errorCode', errorTypeFilter.value)
        }
        
        if (severityFilter.value) {
          params.append('severity', severityFilter.value)
        }
        
        const response = await fetch(`/api/task-logs/errors?${params}`)
        const data = await response.json()
        
        errorList.value = data.content || []
        pagination.total = data.totalElements || 0
        
        // 生成诊断建议
        generateDiagnosticInsights()
        
      } catch (error) {
        console.error('获取错误列表失败:', error)
        ElMessage.error('获取错误列表失败')
      } finally {
        loading.value = false
      }
    }
    
    // 获取错误趋势数据
    const fetchErrorTrend = async () => {
      try {
        const response = await fetch(`/api/task-logs/error-trend?range=${trendTimeRange.value}`)
        const data = await response.json()
        
        if (trendChartInstance && data.trend) {
          const option = {
            series: [
              { data: data.trend.critical || [] },
              { data: data.trend.warning || [] },
              { data: data.trend.retryable || [] }
            ]
          }
          trendChartInstance.setOption(option)
        }
        
        if (typeChartInstance && data.distribution) {
          const option = {
            series: [{
              data: data.distribution.map(item => ({
                name: getErrorTypeName(item.errorCode),
                value: item.count
              }))
            }]
          }
          typeChartInstance.setOption(option)
        }
      } catch (error) {
        console.error('获取错误趋势失败:', error)
      }
    }
    
    // 生成诊断建议
    const generateDiagnosticInsights = () => {
      const insights = []
      
      // 分析高频错误
      const errorCounts = {}
      errorList.value.forEach(error => {
        errorCounts[error.errorCode] = (errorCounts[error.errorCode] || 0) + 1
      })
      
      const topErrors = Object.entries(errorCounts)
        .sort(([,a], [,b]) => b - a)
        .slice(0, 3)
      
      if (topErrors.length > 0) {
        const [topErrorCode, count] = topErrors[0]
        insights.push({
          id: 1,
          title: '高频错误警报',
          description: `检测到 ${getErrorTypeName(topErrorCode)} 错误发生了 ${count} 次，建议检查相关配置`,
          color: '#f56c6c',
          actions: [
            { name: '查看详情', type: 'primary', action: 'viewErrorDetails', params: { errorCode: topErrorCode } },
            { name: '批量重试', type: 'warning', action: 'batchRetry', params: { errorCode: topErrorCode } }
          ]
        })
      }
      
      // 分析重试模式
      const highRetryTasks = errorList.value.filter(error => error.retryCount > 3)
      if (highRetryTasks.length > 0) {
        insights.push({
          id: 2,
          title: '重试过多警告',
          description: `发现 ${highRetryTasks.length} 个任务重试次数超过3次，可能存在系统性问题`,
          color: '#e6a23c',
          actions: [
            { name: '停止重试', type: 'danger', action: 'stopRetry', params: { tasks: highRetryTasks } },
            { name: '调整策略', type: 'info', action: 'adjustRetryPolicy' }
          ]
        })
      }
      
      // 时间模式分析
      const recentErrors = errorList.value.filter(error => 
        new Date(error.startTime) > new Date(Date.now() - 30 * 60 * 1000)
      )
      
      if (recentErrors.length > 10) {
        insights.push({
          id: 3,
          title: '短时间大量错误',
          description: `最近30分钟内发生了 ${recentErrors.length} 个错误，可能服务异常`,
          color: '#f56c6c',
          actions: [
            { name: '暂停爬虫', type: 'danger', action: 'pauseCrawler' },
            { name: '检查服务', type: 'primary', action: 'checkService' }
          ]
        })
      }
      
      diagnosticInsights.value = insights
    }
    
    // 重试任务
    const retryTask = async (error) => {
      try {
        retryLoading.value = true
        await fetch(`/api/crawler/retry/${error.taskId}`, { method: 'POST' })
        ElMessage.success('任务重试已启动')
        refreshErrorList()
      } catch (error) {
        ElMessage.error('重试任务失败')
      } finally {
        retryLoading.value = false
      }
    }
    
    // 忽略错误
    const ignoreError = async (error) => {
      try {
        await ElMessageBox.confirm('确认忽略此错误？忽略后将不再显示', '确认操作', {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          type: 'warning'
        })
        
        await fetch(`/api/task-logs/${error.id}/ignore`, { method: 'PUT' })
        ElMessage.success('错误已忽略')
        refreshErrorList()
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error('忽略错误失败')
        }
      }
    }
    
    // 查看任务流程
    const viewTaskFlow = (error) => {
      // 触发事件让父组件处理
      console.log('查看任务流程:', error.taskId)
    }
    
    // 执行诊断建议操作
    const executeAction = async (action) => {
      switch (action.action) {
        case 'viewErrorDetails':
          errorTypeFilter.value = action.params.errorCode
          refreshErrorList()
          break
          
        case 'batchRetry':
          try {
            await ElMessageBox.confirm(`确认批量重试所有 ${getErrorTypeName(action.params.errorCode)} 错误？`, '批量操作', {
              confirmButtonText: '确认',
              cancelButtonText: '取消',
              type: 'warning'
            })
            
            await fetch('/api/crawler/batch-retry', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ errorCode: action.params.errorCode })
            })
            
            ElMessage.success('批量重试已启动')
            refreshErrorList()
          } catch (error) {
            if (error !== 'cancel') {
              ElMessage.error('批量重试失败')
            }
          }
          break
          
        case 'pauseCrawler':
          try {
            await fetch('/api/crawler/pause', { method: 'POST' })
            ElMessage.success('爬虫已暂停')
          } catch (error) {
            ElMessage.error('暂停爬虫失败')
          }
          break
          
        default:
          ElMessage.info('功能开发中...')
      }
    }
    
    // 刷新错误列表
    const refreshErrorList = () => {
      fetchErrorList()
      fetchErrorStats()
      fetchErrorTrend()
    }
    
    // 分页处理
    const handleSizeChange = (val) => {
      pagination.pageSize = val
      fetchErrorList()
    }
    
    const handleCurrentChange = (val) => {
      pagination.currentPage = val
      fetchErrorList()
    }
    
    // 工具函数
    const formatTime = (timestamp) => {
      return new Date(timestamp).toLocaleString('zh-CN')
    }
    
    const formatDateTime = (timestamp) => {
      return new Date(timestamp).toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      })
    }
    
    const formatJSON = (obj) => {
      return JSON.stringify(obj, null, 2)
    }
    
    const getTaskTypeColor = (type) => {
      const colors = {
        CATALOG_CRAWL: 'primary',
        PACKAGE_CRAWL: 'success',
        PRODUCT_CRAWL: 'info',
        OTHER: 'warning'
      }
      return colors[type] || 'info'
    }
    
    const getTaskTypeName = (type) => {
      const names = {
        CATALOG_CRAWL: '目录爬取',
        PACKAGE_CRAWL: '封装爬取', 
        PRODUCT_CRAWL: '产品爬取',
        OTHER: '其他'
      }
      return names[type] || type
    }
    
    const getErrorTypeColor = (errorCode) => {
      const colors = {
        TIMEOUT: 'warning',
        API_ERROR: 'danger',
        DATA_PROCESSING_ERROR: 'info',
        SYSTEM_ERROR: 'danger',
        UNKNOWN_ERROR: 'warning'
      }
      return colors[errorCode] || 'info'
    }
    
    const getErrorTypeName = (errorCode) => {
      const names = {
        TIMEOUT: '超时',
        API_ERROR: 'API错误',
        DATA_PROCESSING_ERROR: '数据处理',
        SYSTEM_ERROR: '系统错误',
        UNKNOWN_ERROR: '未知错误'
      }
      return names[errorCode] || errorCode
    }
    
    const getStatusColor = (status) => {
      const colors = {
        FAILED: 'danger',
        RETRYING: 'warning',
        IGNORED: 'info'
      }
      return colors[status] || 'info'
    }
    
    const getStatusName = (status) => {
      const names = {
        FAILED: '失败',
        RETRYING: '重试中',
        IGNORED: '已忽略'
      }
      return names[status] || status
    }
    
    // WebSocket处理
    const handleWebSocketMessage = (data) => {
      if (data.type === 'ERROR_OCCURRED') {
        // 新错误发生，更新统计和列表
        refreshErrorList()
      } else if (data.type === 'ERROR_RESOLVED') {
        // 错误已解决，更新相关数据
        errorStats.recoveredErrors++
        refreshErrorList()
      }
    }
    
    // 组件生命周期
    onMounted(async () => {
      await Promise.all([
        fetchErrorStats(),
        fetchErrorList(),
        fetchErrorTrend()
      ])
      
      initTrendChart()
      initTypeChart()
      
      // 建立WebSocket连接 - 临时禁用
      // wsConnection = connectWebSocket('/ws/task-logs', handleWebSocketMessage)
    })
    
    onUnmounted(() => {
      if (trendChartInstance) {
        trendChartInstance.dispose()
        trendChartInstance = null
      }
      
      if (typeChartInstance) {
        typeChartInstance.dispose()
        typeChartInstance = null
      }
      
      // 临时禁用WebSocket
      // if (wsConnection) {
      //   disconnectWebSocket(wsConnection)
      // }
    })
    
    return {
      loading,
      retryLoading,
      errorStats,
      errorTypeFilter,
      severityFilter,
      trendTimeRange,
      errorList,
      pagination,
      errorTrendChart,
      errorTypeChart,
      diagnosticInsights,
      refreshErrorList,
      retryTask,
      ignoreError,
      viewTaskFlow,
      executeAction,
      handleSizeChange,
      handleCurrentChange,
      formatTime,
      formatDateTime,
      formatJSON,
      getTaskTypeColor,
      getTaskTypeName,
      getErrorTypeColor,
      getErrorTypeName,
      getStatusColor,
      getStatusName
    }
  }
}
</script>

<style scoped>
.error-diagnostic-panel {
  padding: 20px;
}

.error-stats-cards .el-row {
  margin-bottom: 0;
}

.error-stat-card {
  height: 100px;
}

.error-stat-card.critical {
  border-left: 4px solid #f56c6c;
}

.error-stat-card.warning {
  border-left: 4px solid #e6a23c;
}

.error-stat-card.retry {
  border-left: 4px solid #409eff;
}

.error-stat-card.recovery {
  border-left: 4px solid #67c23a;
}

.stat-content {
  display: flex;
  align-items: center;
  height: 100%;
}

.stat-icon {
  font-size: 32px;
  margin-right: 15px;
}

.error-stat-card.critical .stat-icon {
  color: #f56c6c;
}

.error-stat-card.warning .stat-icon {
  color: #e6a23c;
}

.error-stat-card.retry .stat-icon {
  color: #409eff;
}

.error-stat-card.recovery .stat-icon {
  color: #67c23a;
}

.stat-info {
  flex: 1;
}

.stat-number {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.error-filters {
  display: flex;
  align-items: center;
}

.error-trend-chart {
  height: 300px;
}

.error-details-expand {
  padding: 20px;
  background-color: #f8f9fa;
}

.error-message {
  background-color: #fff;
  padding: 10px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  white-space: pre-wrap;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  max-height: 200px;
  overflow-y: auto;
}

.error-metadata {
  background-color: #fff;
  padding: 10px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  max-height: 150px;
  overflow-y: auto;
}

.error-actions {
  display: flex;
  gap: 10px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.pattern-chart h4,
.pattern-insights h4 {
  margin-top: 0;
  margin-bottom: 20px;
  color: #303133;
}

.insight-list {
  max-height: 300px;
  overflow-y: auto;
}

.insight-item {
  display: flex;
  margin-bottom: 20px;
  padding: 15px;
  background-color: #f8f9fa;
  border-radius: 6px;
  border-left: 4px solid #409eff;
}

.insight-icon {
  margin-right: 12px;
  font-size: 20px;
}

.insight-content {
  flex: 1;
}

.insight-title {
  font-weight: bold;
  color: #303133;
  margin-bottom: 5px;
}

.insight-description {
  color: #606266;
  font-size: 14px;
  line-height: 1.4;
  margin-bottom: 10px;
}

.insight-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>