<template>
  <div class="dashboard-v3">
    <!-- 面板1: 分类管理 -->
    <a-card class="panel-card mb-4" title="📂 分类管理">
      <a-alert
        v-if="!systemStatus.categoriesSynced"
        type="warning"
        message="系统检测到尚未同步分类信息"
        description="请先爬取官方分类数据，才能开始爬取产品"
        show-icon
        class="mb-3"
      />

      <a-row :gutter="16" v-if="!systemStatus.categoriesSynced">
        <a-col :span="12">
          <a-button
            type="primary"
            size="large"
            :loading="syncLoading"
            @click="handleSyncCategories"
            block
          >
            <template #icon><CloudSyncOutlined /></template>
            爬取官方分类
          </a-button>
        </a-col>
        <a-col :span="12">
          <a-typography-text type="secondary" style="line-height: 32px; display: block; text-align: center;">
            预计1-2分钟，爬取立创商城所有分类
          </a-typography-text>
        </a-col>
      </a-row>

      <a-row :gutter="16" v-else>
        <a-col :span="8">
          <a-statistic
            title="已同步分类"
            :value="systemStatus.categoryStats.level2Count"
            suffix="个二级分类"
          />
        </a-col>
        <a-col :span="8">
          <a-statistic
            title="已爬取产品"
            :value="systemStatus.totalProductsInDb"
            suffix="个产品"
          />
        </a-col>
        <a-col :span="8">
          <a-space>
            <a-button @click="handleSyncCategories" :loading="syncLoading">
              <template #icon><ReloadOutlined /></template>
              重新同步
            </a-button>
            <a-tag v-if="isRunning" color="processing">
              <template #icon><SyncOutlined spin /></template>
              运行中
            </a-tag>
            <a-tag v-else color="success">
              <template #icon><CheckCircleOutlined /></template>
              空闲
            </a-tag>
          </a-space>
        </a-col>
      </a-row>
    </a-card>

    <!-- 面板2: 爬取控制 -->
    <a-card
      v-if="systemStatus.categoriesSynced"
      class="panel-card mb-4"
      title="🚀 爬取控制"
    >
      <a-row :gutter="16" class="mb-3">
        <a-col :span="24">
          <a-radio-group v-model:value="crawlMode" button-style="solid" size="large">
            <a-radio-button value="full">全量爬取</a-radio-button>
            <a-radio-button value="custom">自定义分类</a-radio-button>
          </a-radio-group>
        </a-col>
      </a-row>

      <!-- 全量爬取模式 -->
      <a-row :gutter="16" v-if="crawlMode === 'full'">
        <a-col :span="12">
          <a-alert
            type="info"
            :message="`全量爬取模式`"
            :description="`将爬取所有 ${systemStatus.categoryStats.level2Count} 个分类的产品数据`"
            show-icon
            class="mb-3"
          />
        </a-col>
        <a-col :span="12">
          <a-space style="float: right;">
            <a-button
              v-if="!isRunning"
              type="primary"
              size="large"
              @click="handleStartFullCrawl"
            >
              <template #icon><PlayCircleOutlined /></template>
              开始全量爬取
            </a-button>
            <a-button
              v-else
              danger
              size="large"
              @click="handleStopCrawler"
            >
              <template #icon><StopOutlined /></template>
              停止爬虫
            </a-button>
          </a-space>
        </a-col>
      </a-row>

      <!-- 自定义分类模式 -->
      <a-row :gutter="16" v-else>
        <a-col :span="16">
          <a-button
            type="primary"
            size="large"
            @click="handleOpenCategorySelector"
            :disabled="isRunning"
          >
            <template #icon><AppstoreOutlined /></template>
            选择要爬取的分类（{{ selectedCategories.length }}个已选）
          </a-button>
          <a-button
            v-if="selectedCategories.length > 0 && !isRunning"
            type="primary"
            size="large"
            class="ml-3"
            @click="handleStartBatchCrawl"
          >
            <template #icon><PlayCircleOutlined /></template>
            开始爬取选中分类
          </a-button>
          <a-button
            v-if="isRunning"
            danger
            size="large"
            class="ml-3"
            @click="handleStopCrawler"
          >
            <template #icon><StopOutlined /></template>
            停止爬虫
          </a-button>
          <a-button
            v-if="selectedCategories.length > 0 && !isRunning"
            size="large"
            class="ml-3"
            @click="handleClearMemory"
            title="清除选择记忆"
          >
            <template #icon><DeleteOutlined /></template>
            清除记忆
          </a-button>
        </a-col>
        <a-col :span="8">
          <a-typography-text type="secondary" style="line-height: 32px; display: block;">
            已选择 {{ selectedCategories.length }} 个分类
          </a-typography-text>
        </a-col>
      </a-row>
    </a-card>

    <!-- 面板3: 爬取进度监控 -->
    <a-card
      v-if="isRunning || queueStatus.completed > 0"
      class="panel-card mb-4"
      title="📊 爬取进度监控"
    >
      <!-- 队列状态卡片 -->
      <a-row :gutter="16" class="mb-3">
        <a-col :span="6">
          <a-card size="small" class="queue-stat-card">
            <a-statistic
              title="待处理"
              :value="queueStatus.pending"
              prefix="📋"
              :value-style="{ color: '#1890ff' }"
            />
            <div v-if="queueStatus.subTaskCount > 0" class="text-xs text-orange-500 mt-1">
              含 {{ queueStatus.subTaskCount }} 个拆分子任务
            </div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card size="small" class="queue-stat-card">
            <a-statistic
              title="处理中"
              :value="queueStatus.processing"
              prefix="⚙️"
              :value-style="{ color: '#faad14' }"
            />
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card size="small" class="queue-stat-card">
            <a-statistic
              title="已完成"
              :value="queueStatus.completed"
              prefix="✅"
              :value-style="{ color: '#52c41a' }"
            />
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card size="small" class="queue-stat-card">
            <a-statistic
              title="失败"
              :value="queueStatus.failed"
              prefix="❌"
              :value-style="{ color: '#ff4d4f' }"
            />
          </a-card>
        </a-col>
      </a-row>

      <!-- 进度条 -->
      <a-progress
        :percent="overallProgress"
        :status="overallProgress === 100 ? 'success' : 'active'"
        :show-info="true"
      >
        <template #format="percent">
          {{ percent }}% ({{ queueStatus.completed }} / {{ queueStatus.total }})
          <span v-if="queueStatus.subTaskCount > 0" class="text-orange-500 ml-2">
            (含 {{ queueStatus.subTaskCount }} 个拆分子任务)
          </span>
        </template>
      </a-progress>

      <!-- 任务拆分提示信息 -->
      <a-alert
        v-if="splitTasks.length > 0"
        type="info"
        class="mt-3"
        closable
        @close="splitTasks = []"
      >
        <template #message>
          <div><strong>🔀 智能任务拆分</strong></div>
        </template>
        <template #description>
          <div v-for="(task, index) in splitTasks.slice(0, 3)" :key="index" class="mb-2">
            <div><strong>{{ task.catalogName }}</strong> ({{ task.totalProducts }} 个产品)</div>
            <div class="text-secondary">
              已拆分为 {{ task.splitCount }} 个子任务
              <span v-if="task.splitDimension">({{ task.splitDimension }})</span>
            </div>
            <div v-if="task.splitUnits && task.splitUnits.length > 0" class="text-secondary small">
              包含: {{ task.splitUnits.map((u: any) => u.filterValue).join(', ') }}
              <span v-if="task.splitCount > task.splitUnits.length">等{{ task.splitCount }}个</span>
            </div>
          </div>
          <div v-if="splitTasks.length > 3" class="text-secondary">
            还有 {{ splitTasks.length - 3 }} 个分类被拆分...
          </div>
        </template>
      </a-alert>
    </a-card>

    <!-- 面板4: 存储路径信息 -->
    <a-card class="panel-card mb-4" title="💾 存储路径信息">
      <a-row :gutter="16">
        <a-col :span="24" class="mb-3">
          <a-typography-text strong>基础路径:</a-typography-text>
          <a-typography-text code>{{ storageInfo.basePath }}</a-typography-text>
          <a-button
            size="small"
            class="ml-3"
            :loading="storageLoading"
            @click="loadStoragePaths"
          >
            <template #icon><ReloadOutlined /></template>
            刷新
          </a-button>
        </a-col>
      </a-row>

      <a-row :gutter="16">
        <a-col :span="6">
          <a-statistic
            title="图片文件"
            :value="storageInfo.paths?.images?.fileCount || 0"
            suffix="个"
            :value-style="{ color: '#1890ff' }"
          />
          <div class="mt-2">
            <a-tag v-if="storageInfo.paths?.images?.exists" color="success">存在</a-tag>
            <a-tag v-else color="error">不存在</a-tag>
            <span class="ml-2">{{ (storageInfo.paths?.images?.sizeMB || 0).toFixed(1) }} MB</span>
          </div>
        </a-col>

        <a-col :span="6">
          <a-statistic
            title="PDF文件"
            :value="storageInfo.paths?.pdfs?.fileCount || 0"
            suffix="个"
            :value-style="{ color: '#52c41a' }"
          />
          <div class="mt-2">
            <a-tag v-if="storageInfo.paths?.pdfs?.exists" color="success">存在</a-tag>
            <a-tag v-else color="error">不存在</a-tag>
            <span class="ml-2">{{ (storageInfo.paths?.pdfs?.sizeMB || 0).toFixed(1) }} MB</span>
          </div>
        </a-col>

        <a-col :span="6">
          <a-statistic
            title="数据目录"
            value=""
            :value-style="{ color: '#faad14' }"
          />
          <div class="mt-2">
            <a-tag v-if="storageInfo.paths?.data?.exists" color="success">存在</a-tag>
            <a-tag v-else color="error">不存在</a-tag>
            <span class="ml-2">{{ storageInfo.paths?.data?.relativePath }}</span>
          </div>
        </a-col>

        <a-col :span="6">
          <a-statistic
            title="导出目录"
            value=""
            :value-style="{ color: '#722ed1' }"
          />
          <div class="mt-2">
            <a-tag v-if="storageInfo.paths?.exports?.exists" color="success">存在</a-tag>
            <a-tag v-else color="error">不存在</a-tag>
            <span class="ml-2">{{ storageInfo.paths?.exports?.relativePath }}</span>
          </div>
        </a-col>
      </a-row>

      <a-row class="mt-3">
        <a-col :span="24">
          <a-tag v-if="storageInfo.saveImages" color="green">
            <CheckCircleOutlined /> 图片保存已启用
          </a-tag>
          <a-tag v-else color="warning">
            <CloseCircleOutlined /> 图片保存已禁用
          </a-tag>
        </a-col>
      </a-row>
    </a-card>

    <!-- 面板4: 结果管理 -->
    <a-card
      v-if="systemStatus.categoriesSynced"
      class="panel-card mb-4"
      title="📦 爬取结果管理"
    >
      <a-row :gutter="16" class="mb-3">
        <a-col :span="12">
          <a-space>
            <a-button @click="loadCategoriesWithStatus" :loading="loadingResults">
              <template #icon><ReloadOutlined /></template>
              刷新数据
            </a-button>

            <a-dropdown v-if="selectedResultCategories.length > 0">
              <a-button type="primary">
                <template #icon><DownloadOutlined /></template>
                导出选中 ({{ selectedResultCategories.length }})
                <DownOutlined />
              </a-button>
              <template #overlay>
                <a-menu @click="handleBatchExport">
                  <a-menu-item key="excel">
                    <FileExcelOutlined />
                    导出为Excel
                  </a-menu-item>
                  <a-menu-item key="csv">
                    <FileTextOutlined />
                    导出为CSV
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </a-space>
        </a-col>

        <a-col :span="12">
          <a-input-search
            v-model:value="resultSearchKeyword"
            placeholder="搜索分类名称"
            allow-clear
            style="float: right;"
          />
        </a-col>
      </a-row>

      <!-- 分类结果表格 -->
      <a-table
        :dataSource="filteredCategoriesWithStatus"
        :loading="loadingResults"
        :pagination="resultPagination"
        :row-selection="{
          selectedRowKeys: selectedResultCategories,
          onChange: onResultSelectionChange
        }"
        :scroll="{ y: 400 }"
        row-key="id"
        size="middle"
      >
        <a-table-column key="categoryLevel2Name" title="分类名称" dataIndex="categoryLevel2Name" width="200" />

        <a-table-column key="crawlStatus" title="爬取状态" width="120">
          <template #default="{ record }">
            <a-tag v-if="record.crawlStatus === 'completed'" color="success">
              <CheckCircleOutlined /> 已完成
            </a-tag>
            <a-tag v-else-if="record.crawlStatus === 'processing'" color="processing">
              <SyncOutlined spin /> 爬取中
            </a-tag>
            <a-tag v-else-if="record.crawlStatus === 'failed'" color="error">
              <CloseCircleOutlined /> 失败
            </a-tag>
            <a-tag v-else color="default">
              <MinusCircleOutlined /> 未爬取
            </a-tag>
          </template>
        </a-table-column>

        <a-table-column key="totalProducts" title="产品数量" dataIndex="totalProducts" width="150" align="right">
          <template #default="{ record }">
            <a-space direction="vertical" size="small" style="width: 100%">
              <a-statistic
                :value="record.totalProducts"
                :value-style="{
                  fontSize: '14px',
                  color: record.totalProducts > 4800 ? '#fa8c16' : undefined
                }"
              />
              <!-- <a-tag v-if="record.totalProducts > 4800" color="warning" style="font-size: 11px">
                <template #icon>🔀</template>
                将拆分
              </a-tag> -->
            </a-space>
          </template>
        </a-table-column>

        <a-table-column key="lastCrawlTime" title="最后爬取时间" width="180">
          <template #default="{ record }">
            <span v-if="record.lastCrawlTime">
              {{ formatDateTime(record.lastCrawlTime) }}
            </span>
            <span v-else style="color: #8c8c8c">-</span>
          </template>
        </a-table-column>

        <a-table-column key="actions" title="操作" width="150" fixed="right">
          <template #default="{ record }">
            <a-space>
              <a-button
                type="link"
                size="small"
                @click="viewCategoryProducts(record)"
                :disabled="record.totalProducts === 0"
              >
                查看产品
              </a-button>
              <a-button
                type="link"
                size="small"
                @click="crawlSingleCategory(record.id)"
                :disabled="isRunning"
              >
                重新爬取
              </a-button>
            </a-space>
          </template>
        </a-table-column>
      </a-table>
    </a-card>

    <!-- 分类选择器弹窗（树形结构） -->
    <a-modal
      v-model:open="showCategorySelector"
      title="📂 选择要爬取的分类"
      width="900px"
      :ok-text="'确认选择'"
      :cancel-text="'取消'"
      @ok="handleCategorySelectorOk"
    >
      <a-alert
        message="提示"
        type="info"
        description="支持按一级/二级分类树形选择。默认不全选，避免误操作。勾选后点击确认按钮开始爬取。"
        show-icon
        class="mb-4"
      />

      <CategoryTreeSelector
        ref="categoryTreeSelectorRef"
        :categories="allCategories"
        :selected-category-ids="selectedCategories"
        @update:selected="handleTreeSelectionChange"
      />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import {
  CloudSyncOutlined,
  ReloadOutlined,
  PlayCircleOutlined,
  StopOutlined,
  AppstoreOutlined,
  DownloadOutlined,
  FileExcelOutlined,
  FileTextOutlined,
  DownOutlined,
  CheckCircleOutlined,
  SyncOutlined,
  CloseCircleOutlined,
  MinusCircleOutlined,
  PictureOutlined,
  DatabaseOutlined,
  ExportOutlined,
  ExclamationCircleOutlined,
  ClockCircleOutlined,
  PauseCircleOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'
import { h } from 'vue'
import CategoryTreeSelector from '@/components/CategoryTreeSelector.vue'
import {
  getSystemStatus,
  syncCategories,
  startFullCrawl,
  startBatchCrawl,
  stopCrawler,
  getCategoriesWithStatus,
  getAllCategories,
  exportProductsByCategories,
  downloadExportFile,
  getStoragePaths
} from '@/api/product'

const router = useRouter()

// localStorage 键名常量
const STORAGE_KEY = 'lcsc_crawler_selected_categories'

// 响应式数据
const syncLoading = ref(false)
const loadingResults = ref(false)
const isRunning = ref(false)
const crawlMode = ref<'full' | 'custom'>('full')
const categoryTreeSelectorRef = ref<InstanceType<typeof CategoryTreeSelector>>()
const showCategorySelector = ref(false)
const resultSearchKeyword = ref('')

const systemStatus = ref({
  categoriesSynced: false,
  categoryStats: {
    level1Count: 0,
    level2Count: 0
  }
})

const queueStatus = ref({
  pending: 0,
  processing: 0,
  completed: 0,
  failed: 0,
  total: 0,
  subTaskCount: 0
})

// 任务拆分记录
const splitTasks = ref<any[]>([])

const selectedCategories = ref<number[]>([])
const selectedResultCategories = ref<number[]>([])
const categoriesWithStatus = ref<any[]>([])
const allCategories = ref<any[]>([])

// 存储路径相关
const storageLoading = ref(false)
const storageInfo = ref({
  basePath: '',
  paths: {
    images: {
      path: '',
      relativePath: '',
      exists: false,
      fileCount: 0,
      sizeBytes: 0,
      sizeMB: 0
    },
    pdfs: {
      path: '',
      relativePath: '',
      exists: false,
      fileCount: 0,
      sizeBytes: 0,
      sizeMB: 0
    },
    data: {
      path: '',
      relativePath: '',
      exists: false
    },
    exports: {
      path: '',
      relativePath: '',
      exists: false
    }
  },
  saveImages: false,
  config: {
    storageBasePath: '',
    imageDir: '',
    pdfDir: '',
    dataDir: '',
    exportDir: ''
  }
})

const resultPagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number) => `共 ${total} 条数据`,
  onChange: (page: number, pageSize: number) => {
    resultPagination.current = page
    resultPagination.pageSize = pageSize
  }
})

// 计算属性
const overallProgress = computed(() => {
  if (queueStatus.value.total === 0) return 0
  return Math.round((queueStatus.value.completed / queueStatus.value.total) * 100)
})

const filteredCategoriesWithStatus = computed(() => {
  // 1. 筛选
  let filtered = categoriesWithStatus.value
  if (resultSearchKeyword.value) {
    filtered = filtered.filter(cat =>
      cat.categoryLevel2Name.includes(resultSearchKeyword.value)
    )
  }

  // 2. 更新总数
  resultPagination.total = filtered.length

  // 3. 分页切片
  const start = (resultPagination.current - 1) * resultPagination.pageSize
  const end = start + resultPagination.pageSize
  return filtered.slice(start, end)
})

// 监听搜索关键词变化，重置到第一页
watch(resultSearchKeyword, () => {
  resultPagination.current = 1
})

// 方法
const checkSystemStatus = async () => {
  try {
    const data = await getSystemStatus()
    if (data) {
      systemStatus.value = data
      isRunning.value = data.isRunning
      queueStatus.value = data.queueStatus
    }
  } catch (error) {
    console.error('检查系统状态失败:', error)
  }
}

// 加载存储路径信息
const loadStoragePaths = async () => {
  storageLoading.value = true
  try {
    const res = await getStoragePaths()
    console.log('存储路径API响应:', res)
    // 响应拦截器已经返回了data部分，直接使用即可
    if (res && res.basePath) {
      storageInfo.value = res
      console.log('存储路径信息:', res)
    } else {
      console.error('API响应格式错误:', res)
      message.error('获取存储路径信息失败: 响应格式不正确')
    }
  } catch (error) {
    console.error('获取存储路径信息失败:', error)
    message.error('获取存储路径信息失败: ' + (error as Error).message)
  } finally {
    storageLoading.value = false
  }
}

const handleSyncCategories = async () => {
  syncLoading.value = true
  try {
    const res = await syncCategories()
    console.log('分类同步响应:', res)
    if (res && res.success) {
      const { level1Count, level2Count } = res
      if (level2Count === 0) {
        message.warning(`分类同步完成，但未获取到任何分类数据。请检查网络连接或API状态。`)
      } else {
        message.success(`分类同步成功！获取到 ${level1Count} 个一级分类，${level2Count} 个二级分类`)
      }
      await checkSystemStatus()
      await loadCategoriesWithStatus()
    } else {
      message.error(res?.message || '同步失败')
    }
  } catch (error) {
    console.error('同步分类失败:', error)
    message.error('同步失败，请重试')
  } finally {
    syncLoading.value = false
  }
}

const handleStartFullCrawl = async () => {
  try {
    const data = await startFullCrawl()
    if (data && data.success) {
      message.success('全量爬取已启动！')
      isRunning.value = true
    } else {
      message.error(data?.message || '启动失败')
    }
  } catch (error) {
    console.error('启动全量爬取失败:', error)
    message.error('启动失败，请重试')
  }
}

const handleStartBatchCrawl = async () => {
  if (selectedCategories.value.length === 0) {
    message.warning('请先选择要爬取的分类')
    return
  }

  try {
    const data = await startBatchCrawl(selectedCategories.value)
    if (data && data.success) {
      message.success(`已创建 ${selectedCategories.value.length} 个爬取任务！`)
      isRunning.value = true
      showCategorySelector.value = false
    } else {
      message.error(data?.message || '启动失败')
    }
  } catch (error) {
    console.error('批量爬取失败:', error)
    message.error('启动失败，请重试')
  }
}

const handleStopCrawler = async () => {
  try {
    const data = await stopCrawler()
    // 后端现在返回 { success, message, isRunning, pendingTasks, processingTasks }
    if (data && data.success) {
      message.success(data.message || '爬虫已停止')
      isRunning.value = false
      // 刷新状态，确保UI同步
      await checkSystemStatus()
    } else {
      message.error(data?.message || '停止失败，请检查网络连接')
    }
  } catch (error) {
    console.error('停止爬虫失败:', error)
    message.error('停止失败，请检查网络连接后重试')
  }
}

const loadCategoriesWithStatus = async () => {
  loadingResults.value = true
  try {
    const data = await getCategoriesWithStatus()
    if (data) {
      categoriesWithStatus.value = data
      // total 在 computed 中动态计算，此处重置页码
      resultPagination.current = 1
    }
  } catch (error) {
    console.error('加载分类状态失败:', error)
  } finally {
    loadingResults.value = false
  }
}

// 加载所有分类（包括未爬取的），用于分类选择器
const loadAllCategoriesForSelector = async () => {
  try {
    const data = await getAllCategories()
    if (data) {
      allCategories.value = data
    }
  } catch (error) {
    console.error('加载所有分类失败:', error)
    message.error('加载分类列表失败')
  }
}

// 打开分类选择器
const handleOpenCategorySelector = async () => {
  await loadAllCategoriesForSelector()
  showCategorySelector.value = true
}

const handleBatchExport = async ({ key }: { key: string }) => {
  if (selectedResultCategories.value.length === 0) {
    message.warning('请先选择要导出的分类')
    return
  }

  const format = key === 'excel' ? 'excel' : 'csv'

  try {
    const result = await exportProductsByCategories(selectedResultCategories.value, format)
    const { filename, recordCount } = result
    message.success(`导出成功！共 ${recordCount} 条记录，正在下载...`)
    setTimeout(() => {
      downloadExportFile(filename)
    }, 500)
  } catch (error) {
    console.error('导出失败:', error)
    message.error('导出失败，请重试')
  }
}

// 树形选择器更新选中项
const handleTreeSelectionChange = (selectedIds: number[]) => {
  selectedCategories.value = selectedIds
  // 保存到 localStorage
  localStorage.setItem(STORAGE_KEY, JSON.stringify(selectedIds))
}

const onResultSelectionChange = (selectedRowKeys: number[]) => {
  selectedResultCategories.value = selectedRowKeys
}

const handleCategorySelectorOk = () => {
  // 从树形选择器获取最终选中的ID
  if (categoryTreeSelectorRef.value) {
    selectedCategories.value = categoryTreeSelectorRef.value.getSelectedIds()
  }
  showCategorySelector.value = false
}

const viewCategoryProducts = (record: any) => {
  router.push(`/products?categoryLevel2Id=${record.id}`)
}

const crawlSingleCategory = async (categoryId: number) => {
  selectedCategories.value = [categoryId]
  await handleStartBatchCrawl()
}

const formatDateTime = (dateTime: string) => {
  if (!dateTime) return '-'
  return new Date(dateTime).toLocaleString('zh-CN')
}

// 清除选择记忆
const handleClearMemory = () => {
  localStorage.removeItem(STORAGE_KEY)
  selectedCategories.value = []
  message.success('已清除选择记忆')
}

// 轮询检查状态
let statusInterval: any = null

onMounted(() => {
  // 从 localStorage 恢复上次的选择
  const savedSelection = localStorage.getItem(STORAGE_KEY)
  if (savedSelection) {
    try {
      const savedIds = JSON.parse(savedSelection)
      if (Array.isArray(savedIds) && savedIds.length > 0) {
        selectedCategories.value = savedIds
        console.log('已恢复上次选择的分类:', savedIds.length, '个')
      }
    } catch (error) {
      console.error('恢复选择失败:', error)
      localStorage.removeItem(STORAGE_KEY)
    }
  }

  checkSystemStatus()
  loadCategoriesWithStatus()
  loadStoragePaths()

  // 每5秒检查一次状态
  statusInterval = setInterval(() => {
    checkSystemStatus()
    if (isRunning.value) {
      loadCategoriesWithStatus()
    }
  }, 5000)
})

onUnmounted(() => {
  if (statusInterval) {
    clearInterval(statusInterval)
  }
})
</script>

<style scoped>
.dashboard-v3 {
  padding: 24px;
}

.panel-card {
  margin-bottom: 16px;
}

.mb-3 {
  margin-bottom: 12px;
}

.mb-4 {
  margin-bottom: 16px;
}

.ml-3 {
  margin-left: 12px;
}

.mt-2 {
  margin-top: 8px;
}

.mt-3 {
  margin-top: 12px;
}
</style>
