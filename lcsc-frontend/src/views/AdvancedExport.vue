<template>
  <div class="advanced-export">
    <a-card title="高级导出配置" class="config-card">
      <a-form :model="filterForm" layout="vertical">
        <a-row :gutter="16">
          <!-- 店铺选择（单选） -->
          <a-col :span="8">
            <a-form-item label="选择店铺" required>
              <a-select
                v-model:value="filterForm.shopId"
                placeholder="请选择店铺"
                :options="shopOptions"
                @change="handleShopChange"
                allow-clear
              />
            </a-form-item>
          </a-col>

          <!-- 一级分类 -->
          <a-col :span="8">
            <a-form-item label="一级分类">
              <a-select
                v-model:value="filterForm.categoryLevel1Id"
                placeholder="请选择一级分类"
                :options="level1Options"
                @change="handleLevel1Change"
                allow-clear
              />
            </a-form-item>
          </a-col>

          <!-- 二级分类 -->
          <a-col :span="8">
            <a-form-item label="二级分类">
              <a-select
                v-model:value="filterForm.categoryLevel2Id"
                placeholder="请先选择一级分类"
                :options="level2Options"
                :disabled="!filterForm.categoryLevel1Id"
                @change="handleLevel2Change"
                allow-clear
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <!-- 三级分类 -->
          <a-col :span="8">
            <a-form-item label="三级分类">
              <a-select
                v-model:value="filterForm.categoryLevel3Id"
                placeholder="请先选择二级分类"
                :options="level3Options"
                :disabled="!filterForm.categoryLevel2Id"
                allow-clear
              />
            </a-form-item>
          </a-col>

          <!-- 品牌选择（单选） -->
          <a-col :span="8">
            <a-form-item label="品牌">
              <a-select
                v-model:value="filterForm.brand"
                placeholder="选择品牌"
                :options="brandOptions"
                show-search
                allow-clear
              />
            </a-form-item>
          </a-col>

          <!-- 是否有图片 -->
          <a-col :span="8">
            <a-form-item label="是否有图片">
              <a-select
                v-model:value="filterForm.hasImage"
                placeholder="选择图片筛选条件"
                allow-clear
              >
                <a-select-option :value="undefined">全部</a-select-option>
                <a-select-option :value="true">有图片</a-select-option>
                <a-select-option :value="false">无图片</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <!-- 库存范围 -->
          <a-col :span="8">
            <a-form-item label="库存范围">
              <a-space>
                <a-input-number
                  v-model:value="filterForm.stockMin"
                  placeholder="最小值"
                  :min="0"
                  style="width: 120px"
                />
                <span>至</span>
                <a-input-number
                  v-model:value="filterForm.stockMax"
                  placeholder="最大值"
                  :min="0"
                  style="width: 120px"
                />
              </a-space>
            </a-form-item>
          </a-col>
        </a-row>

        <!-- 折扣设置（6个独立输入框） -->
        <a-form-item label="折扣设置（%）" required>
          <a-space>
            <div v-for="(_, index) in 6" :key="index" style="display: inline-block">
              <div style="margin-bottom: 4px; font-size: 12px; color: #666">
                {{ index + 1 }}级折扣
              </div>
              <a-input-number
                v-model:value="filterForm.discounts[index]"
                :min="1"
                :max="100"
                :precision="0"
                style="width: 80px"
              />
            </div>
          </a-space>
        </a-form-item>

        <!-- 操作按钮 -->
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="handleAddTask" :loading="addLoading" :disabled="!filterForm.shopId">
              <template #icon><PlusOutlined /></template>
              确定添加
            </a-button>
            <a-button @click="handleResetFilter">
              <template #icon><ReloadOutlined /></template>
              重置筛选
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 任务列表 -->
    <a-card title="导出任务列表" class="task-list-card" style="margin-top: 16px">
      <template #extra>
        <a-space>
          <span>共 {{ taskList.length }} 个产品</span>
          <a-button
            type="primary"
            danger
            @click="handleExport"
            :loading="exportLoading"
            :disabled="taskList.length === 0"
          >
            <template #icon><DownloadOutlined /></template>
            确定导出
          </a-button>
          <a-button @click="handleClearTasks" :disabled="taskList.length === 0">
            <template #icon><DeleteOutlined /></template>
            清空列表
          </a-button>
        </a-space>
      </template>

      <a-table
        :columns="taskColumns"
        :data-source="taskList"
        :pagination="{ pageSize: 10 }"
        row-key="productCode"
        size="small"
      >
        <template #bodyCell="{ column, record, index }">
          <template v-if="column.key === 'index'">
            {{ index + 1 }}
          </template>
          <template v-else-if="column.key === 'discounts'">
            {{ record.discounts.join(', ') }}%
          </template>
          <template v-else-if="column.key === 'addedAt'">
            {{ formatTimestamp(record.addedAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" danger size="small" @click="handleRemoveTask(record.productCode)">
              删除
            </a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, DownloadOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { getAllShops } from '@/api/shop'
import { getAllCategories } from '@/api/category'
import { getAllBrands } from '@/api/product'
import { addToTaskList, exportTaobaoCsv, type ExportTaskItem } from '@/api/export'
import type { Shop, CategoryLevel1Code, CategoryLevel2Code, CategoryLevel3Code } from '@/types'

// 筛选表单
const filterForm = reactive({
  shopId: undefined as number | undefined,
  categoryLevel1Id: undefined as number | undefined,
  categoryLevel2Id: undefined as number | undefined,
  categoryLevel3Id: undefined as number | undefined,
  brand: undefined as string | undefined,
  hasImage: undefined as boolean | undefined,
  stockMin: undefined as number | undefined,
  stockMax: undefined as number | undefined,
  discounts: [90, 88, 85, 82, 80, 78] // 默认折扣配置
})

// 任务列表
const taskList = ref<ExportTaskItem[]>([])

// 加载状态
const addLoading = ref(false)
const exportLoading = ref(false)

// 下拉选项
const shopOptions = ref<{ label: string; value: number }[]>([])
const level1Options = ref<{ label: string; value: number }[]>([])
const level2Options = ref<{ label: string; value: number }[]>([])
const level3Options = ref<{ label: string; value: number }[]>([])
const brandOptions = ref<{ label: string; value: string }[]>([])

// 分类数据缓存
const level1List = ref<CategoryLevel1Code[]>([])
const level2List = ref<CategoryLevel2Code[]>([])
const level3List = ref<CategoryLevel3Code[]>([])

// 任务列表表格列定义
const taskColumns = [
  { title: '序号', key: 'index', width: 60 },
  { title: '产品编号', dataIndex: 'productCode', key: 'productCode', width: 120 },
  { title: '型号', dataIndex: 'model', key: 'model' },
  { title: '品牌', dataIndex: 'brand', key: 'brand', width: 120 },
  { title: '店铺', dataIndex: 'shopName', key: 'shopName', width: 150 },
  { title: '折扣配置', key: 'discounts', width: 200 },
  { title: '添加时间', key: 'addedAt', width: 160 },
  { title: '操作', key: 'action', width: 80, fixed: 'right' }
]

// 页面加载时初始化数据
onMounted(() => {
  loadShops()
  loadCategories()
  loadBrands()
})

// 加载店铺列表
const loadShops = async () => {
  try {
    const shops = await getAllShops()
    shopOptions.value = shops.map((shop: Shop) => ({
      label: shop.shopName,
      value: shop.id
    }))
  } catch (error) {
    message.error('加载店铺列表失败')
  }
}

// 加载分类数据
const loadCategories = async () => {
  try {
    const categories = await getAllCategories()
    level1List.value = categories.level1 || []
    level2List.value = categories.level2 || []
    level3List.value = categories.level3 || []

    // 构建一级分类选项
    level1Options.value = level1List.value.map((cat: CategoryLevel1Code) => ({
      label: cat.categoryLevel1Name,
      value: cat.id
    }))
  } catch (error) {
    message.error('加载分类数据失败')
  }
}

// 加载品牌列表
const loadBrands = async () => {
  try {
    const brands = await getAllBrands()
    brandOptions.value = brands.map((brand: string) => ({
      label: brand,
      value: brand
    }))
  } catch (error) {
    message.error('加载品牌列表失败')
  }
}

// 一级分类变化事件
const handleLevel1Change = (value: number | undefined) => {
  filterForm.categoryLevel2Id = undefined
  filterForm.categoryLevel3Id = undefined
  level2Options.value = []
  level3Options.value = []

  if (value) {
    // 筛选二级分类
    level2Options.value = level2List.value
      .filter((cat: CategoryLevel2Code) => cat.categoryLevel1Id === value)
      .map((cat: CategoryLevel2Code) => ({
        label: cat.categoryLevel2Name,
        value: cat.id
      }))
  }
}

// 二级分类变化事件
const handleLevel2Change = (value: number | undefined) => {
  filterForm.categoryLevel3Id = undefined
  level3Options.value = []

  if (value) {
    // 筛选三级分类
    level3Options.value = level3List.value
      .filter((cat: CategoryLevel3Code) => cat.categoryLevel2Id === value)
      .map((cat: CategoryLevel3Code) => ({
        label: cat.categoryLevel3Name,
        value: cat.id
      }))
  }
}

// 店铺变化事件
const handleShopChange = () => {
  // 店铺变化时可以清空其他筛选条件（可选）
}

// 添加到任务列表
const handleAddTask = async () => {
  if (!filterForm.shopId) {
    message.warning('请先选择店铺')
    return
  }

  // 验证折扣配置
  if (filterForm.discounts.length !== 6 || filterForm.discounts.some(d => !d || d < 1 || d > 100)) {
    message.warning('请填写完整的6级折扣配置（1-100之间）')
    return
  }

  addLoading.value = true
  try {
    const updatedTasks = await addToTaskList({
      shopId: filterForm.shopId!,
      categoryLevel1Id: filterForm.categoryLevel1Id,
      categoryLevel2Id: filterForm.categoryLevel2Id,
      categoryLevel3Id: filterForm.categoryLevel3Id,
      brand: filterForm.brand,
      hasImage: filterForm.hasImage,
      stockMin: filterForm.stockMin,
      stockMax: filterForm.stockMax,
      discounts: filterForm.discounts,
      currentTasks: taskList.value
    })

    const addedCount = updatedTasks.length - taskList.value.length
    taskList.value = updatedTasks

    message.success(`成功添加 ${addedCount} 个产品到任务列表（已自动去重）`)
  } catch (error: any) {
    message.error('添加任务失败: ' + (error.message || '未知错误'))
  } finally {
    addLoading.value = false
  }
}

// 导出为淘宝CSV
const handleExport = async () => {
  if (taskList.value.length === 0) {
    message.warning('任务列表为空，请先添加产品')
    return
  }

  exportLoading.value = true
  try {
    await exportTaobaoCsv(taskList.value)
    message.success('导出成功')
  } catch (error: any) {
    message.error('导出失败: ' + (error.message || '未知错误'))
  } finally {
    exportLoading.value = false
  }
}

// 重置筛选条件
const handleResetFilter = () => {
  filterForm.categoryLevel1Id = undefined
  filterForm.categoryLevel2Id = undefined
  filterForm.categoryLevel3Id = undefined
  filterForm.brand = undefined
  filterForm.hasImage = undefined
  filterForm.stockMin = undefined
  filterForm.stockMax = undefined
  filterForm.discounts = [90, 88, 85, 82, 80, 78]
  level2Options.value = []
  level3Options.value = []
}

// 清空任务列表
const handleClearTasks = () => {
  taskList.value = []
  message.success('已清空任务列表')
}

// 删除单个任务
const handleRemoveTask = (productCode: string) => {
  taskList.value = taskList.value.filter(task => task.productCode !== productCode)
  message.success('已删除')
}

// 格式化时间戳
const formatTimestamp = (timestamp: number): string => {
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}
</script>

<style scoped>
.advanced-export {
  padding: 24px;
}

.config-card,
.task-list-card {
  margin-bottom: 16px;
}

.discount-hint {
  color: #999;
  font-size: 12px;
}
</style>
