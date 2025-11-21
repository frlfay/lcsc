<template>
  <div class="advanced-export">
    <a-card title="高级导出配置" class="config-card">
      <a-form :model="exportForm" layout="vertical">
        <!-- 店铺选择 -->
        <a-form-item label="选择店铺">
          <a-select
            v-model:value="exportForm.shopIds"
            mode="multiple"
            placeholder="选择要导出的店铺（不选则导出全部）"
            style="width: 100%"
            :options="shopOptions"
            allow-clear
          />
        </a-form-item>

        <!-- 分类筛选 -->
        <a-form-item label="分类筛选">
          <a-tree-select
            v-model:value="exportForm.categoryIds"
            :tree-data="categoryTreeData"
            tree-checkable
            placeholder="选择分类（不选则导出全部）"
            style="width: 100%"
            :show-checked-strategy="SHOW_PARENT"
            allow-clear
          />
        </a-form-item>

        <!-- 品牌筛选 -->
        <a-form-item label="品牌筛选">
          <a-select
            v-model:value="exportForm.brands"
            mode="tags"
            placeholder="输入品牌名称（可输入多个）"
            style="width: 100%"
            allow-clear
          />
        </a-form-item>

        <!-- 关键词搜索 -->
        <a-form-item label="关键词搜索">
          <a-input
            v-model:value="exportForm.keyword"
            placeholder="搜索产品编号、型号、品牌"
            allow-clear
          />
        </a-form-item>

        <!-- 价格折扣设置 -->
        <a-form-item label="价格折扣">
          <a-space>
            <a-input-number
              v-model:value="discountPercent"
              :min="1"
              :max="100"
              :precision="0"
              addon-after="%"
              style="width: 120px"
            />
            <span class="discount-hint">（输入折扣百分比，如90表示9折）</span>
          </a-space>
        </a-form-item>

        <!-- 导出选项 -->
        <a-form-item label="导出选项">
          <a-space direction="vertical">
            <a-checkbox v-model:checked="exportForm.includeImageLinks">
              包含图片链接（每个店铺一列）
            </a-checkbox>
            <a-checkbox v-model:checked="exportForm.includeLadderPrices">
              包含阶梯价格（6级）
            </a-checkbox>
          </a-space>
        </a-form-item>
      </a-form>

      <!-- 操作按钮 -->
      <div class="action-buttons">
        <a-space>
          <a-button type="primary" @click="handlePreview" :loading="previewLoading">
            <template #icon><EyeOutlined /></template>
            预览数据
          </a-button>
          <a-button type="primary" @click="handleExport" :loading="exportLoading" :disabled="!previewData">
            <template #icon><DownloadOutlined /></template>
            导出Excel
          </a-button>
          <a-button @click="handleReset">
            <template #icon><ReloadOutlined /></template>
            重置
          </a-button>
        </a-space>
      </div>
    </a-card>

    <!-- 预览结果 -->
    <a-card v-if="previewData" title="导出预览" class="preview-card">
      <a-descriptions :column="2" bordered>
        <a-descriptions-item label="产品数量">
          <a-statistic :value="previewData.productCount" suffix="个" />
        </a-descriptions-item>
        <a-descriptions-item label="店铺数量">
          <a-statistic :value="previewData.shopCount" suffix="个" />
        </a-descriptions-item>
        <a-descriptions-item label="分类数量">
          <a-statistic :value="previewData.categoryCount" suffix="个" />
        </a-descriptions-item>
        <a-descriptions-item label="品牌数量">
          <a-statistic :value="previewData.brandCount" suffix="个" />
        </a-descriptions-item>
        <a-descriptions-item label="选中店铺" :span="2">
          <a-tag v-for="shop in previewData.shops" :key="shop" color="blue">{{ shop }}</a-tag>
          <span v-if="previewData.shops.length === 0">全部店铺</span>
        </a-descriptions-item>
      </a-descriptions>

      <a-alert
        v-if="previewData.productCount > 10000"
        message="数据量较大"
        description="导出的产品数量超过10000，生成Excel可能需要较长时间，请耐心等待。"
        type="warning"
        show-icon
        style="margin-top: 16px"
      />

      <a-alert
        v-if="previewData.productCount === 0"
        message="没有符合条件的产品"
        description="请调整筛选条件后重新预览。"
        type="info"
        show-icon
        style="margin-top: 16px"
      />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { TreeSelect } from 'ant-design-vue'
import { EyeOutlined, DownloadOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { getExportPreview, exportAdvanced } from '@/api/export'
import type { AdvancedExportRequest, ExportPreview } from '@/api/export'
import { getShopList } from '@/api/shop'
import { getCategoryLevel1List, getCategoryLevel2List } from '@/api/category'
import type { Shop, CategoryLevel1Code, CategoryLevel2Code } from '@/types'

const SHOW_PARENT = TreeSelect.SHOW_PARENT

// 响应式数据
const previewLoading = ref(false)
const exportLoading = ref(false)
const previewData = ref<ExportPreview | null>(null)
const shopList = ref<Shop[]>([])
const categoryLevel1List = ref<CategoryLevel1Code[]>([])
const categoryLevel2List = ref<CategoryLevel2Code[]>([])

const discountPercent = ref(100)

const exportForm = reactive<AdvancedExportRequest>({
  shopIds: [],
  categoryIds: [],
  brands: [],
  productCodes: [],
  keyword: '',
  discountRate: 1,
  includeImageLinks: true,
  includeLadderPrices: true
})

// 计算属性
const shopOptions = computed(() => {
  return shopList.value.map(shop => ({
    label: shop.shopName,
    value: shop.id
  }))
})

const categoryTreeData = computed(() => {
  return categoryLevel1List.value.map(level1 => ({
    title: level1.categoryLevel1Name,
    value: `l1_${level1.id}`,
    key: `l1_${level1.id}`,
    selectable: false,
    children: categoryLevel2List.value
      .filter(level2 => level2.categoryLevel1Id === level1.id)
      .map(level2 => ({
        title: level2.categoryLevel2Name,
        value: level2.id,
        key: `l2_${level2.id}`
      }))
  }))
})

// 方法
const loadShopList = async () => {
  try {
    shopList.value = await getShopList()
  } catch (error) {
    console.error('加载店铺列表失败:', error)
  }
}

const loadCategoryList = async () => {
  try {
    const [level1, level2] = await Promise.all([
      getCategoryLevel1List(),
      getCategoryLevel2List()
    ])
    categoryLevel1List.value = level1
    categoryLevel2List.value = level2
  } catch (error) {
    console.error('加载分类列表失败:', error)
  }
}

const buildRequest = (): AdvancedExportRequest => {
  // 处理分类ID（过滤掉一级分类的key）
  const categoryIds = (exportForm.categoryIds || [])
    .filter(id => typeof id === 'number')

  return {
    ...exportForm,
    categoryIds,
    discountRate: discountPercent.value / 100
  }
}

const handlePreview = async () => {
  previewLoading.value = true
  try {
    const request = buildRequest()
    previewData.value = await getExportPreview(request)
    message.success('预览成功')
  } catch (error: any) {
    message.error(error.message || '预览失败')
  } finally {
    previewLoading.value = false
  }
}

const handleExport = async () => {
  if (!previewData.value) {
    message.warning('请先预览数据')
    return
  }

  if (previewData.value.productCount === 0) {
    message.warning('没有符合条件的产品可导出')
    return
  }

  exportLoading.value = true
  try {
    const request = buildRequest()
    await exportAdvanced(request)
    message.success('导出成功')
  } catch (error: any) {
    message.error(error.message || '导出失败')
  } finally {
    exportLoading.value = false
  }
}

const handleReset = () => {
  exportForm.shopIds = []
  exportForm.categoryIds = []
  exportForm.brands = []
  exportForm.productCodes = []
  exportForm.keyword = ''
  exportForm.includeImageLinks = true
  exportForm.includeLadderPrices = true
  discountPercent.value = 100
  previewData.value = null
}

// 生命周期
onMounted(() => {
  loadShopList()
  loadCategoryList()
})
</script>

<style scoped>
.advanced-export {
  padding: 16px;
}

.config-card {
  margin-bottom: 16px;
}

.preview-card {
  margin-bottom: 16px;
}

.action-buttons {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.discount-hint {
  color: #999;
  font-size: 12px;
}

:deep(.ant-statistic-content) {
  font-size: 20px;
}

:deep(.ant-descriptions-item-label) {
  width: 100px;
}
</style>
