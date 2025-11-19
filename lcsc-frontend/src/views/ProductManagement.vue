<template>
  <div class="product-management">
    <!-- 搜索区域 -->
    <a-card class="mb-4">
      <a-form :model="searchForm" layout="inline">
        <a-form-item label="产品编号">
          <a-input 
            v-model:value="searchForm.productCode" 
            placeholder="输入产品编号"
            allow-clear
            style="width: 200px"
          />
        </a-form-item>
        <a-form-item label="品牌">
          <a-input 
            v-model:value="searchForm.brand" 
            placeholder="输入品牌名称"
            allow-clear
            style="width: 200px"
          />
        </a-form-item>
        <a-form-item label="型号">
          <a-input 
            v-model:value="searchForm.model" 
            placeholder="输入型号"
            allow-clear
            style="width: 200px"
          />
        </a-form-item>
        <a-form-item label="一级分类">
          <a-select
            v-model:value="searchForm.categoryLevel1Id"
            placeholder="选择一级分类"
            allow-clear
            style="width: 200px"
            @change="handleLevel1Change"
          >
            <a-select-option
              v-for="item in level1Categories"
              :key="item.id"
              :label="item.categoryLevel1Name"
              :value="item.id"
            >
              {{ item.categoryLevel1Name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="二级分类">
          <a-select
            v-model:value="searchForm.categoryLevel2Id"
            placeholder="选择二级分类"
            allow-clear
            style="width: 200px"
            :disabled="!searchForm.categoryLevel1Id"
          >
            <a-select-option
              v-for="item in level2Categories"
              :key="item.id"
              :label="item.categoryLevel2Name"
              :value="item.id"
            >
              {{ item.categoryLevel2Name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="库存状态">
          <a-select
            v-model:value="searchForm.hasStock"
            placeholder="选择库存状态"
            allow-clear
            style="width: 150px"
          >
            <a-select-option label="有库存" :value="true">有库存</a-select-option>
            <a-select-option label="无库存" :value="false">无库存</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
      <div class="search-actions">
        <a-button type="primary" @click="handleSearch">
          <template #icon>
            <SearchOutlined />
          </template>
          搜索
        </a-button>
        <a-button @click="handleReset">
          <template #icon>
            <ReloadOutlined />
          </template>
          重置
        </a-button>
<!--        <a-button type="primary" @click="showAddDialog = true" class="btn-success">-->
<!--          <template #icon>-->
<!--            <PlusOutlined />-->
<!--          </template>-->
<!--          新增产品-->
<!--        </a-button>-->
<!--        <a-button @click="showCrawlerDialog = true" class="btn-warning">-->
<!--          <template #icon>-->
<!--            <CloudDownloadOutlined />-->
<!--          </template>-->
<!--          爬取产品-->
<!--        </a-button>-->
<!--        <a-button @click="showStatistics" class="btn-info">-->
<!--          <template #icon>-->
<!--            <BarChartOutlined />-->
<!--          </template>-->
<!--          统计信息-->
<!--        </a-button>-->
        <a-dropdown>
          <template #overlay>
            <a-menu @click="handleExportMenuClick">
              <a-menu-item key="excel-current">
                <FileExcelOutlined />
                导出当前结果（Excel）
              </a-menu-item>
              <a-menu-item key="excel-all">
                <FileExcelOutlined />
                导出所有产品（Excel）
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item key="csv-current">
                <FileTextOutlined />
                导出当前结果（CSV）
              </a-menu-item>
            </a-menu>
          </template>
          <a-button class="btn-success">
            <template #icon>
              <DownloadOutlined />
            </template>
            导出数据
            <DownOutlined />
          </a-button>
        </a-dropdown>
      </div>
    </a-card>

    <!-- 产品列表 -->
    <a-card>
      <a-table
        :dataSource="tableData"
        :loading="loading"
        :pagination="false"
        bordered
        :scroll="{ x: 1400 }"
        row-key="id"
      >
        <a-table-column key="thumbnail" title="主图" width="80" fixed="left">
          <template #default="{ record }">
            <img
              v-if="record.productImageUrlBig"
              :src="record.productImageUrlBig"
              class="product-thumbnail"
              @click="handleImagePreview(record.productImageUrlBig)"
              @error="handleImageError"
              alt="产品主图"
            />
            <span v-else class="no-image">无图</span>
          </template>
        </a-table-column>
        <a-table-column key="productCode" data-index="productCode" title="产品编号" width="120" fixed="left" />
        <a-table-column key="brand" data-index="brand" title="品牌" width="100" />
        <a-table-column key="model" data-index="model" title="型号" width="130" :ellipsis="true" />
        <a-table-column key="packageName" data-index="packageName" title="封装" width="90" />
        <a-table-column key="categoryLevel1Name" data-index="categoryLevel1Name" title="一级分类" width="100">
          <template #default="{ record }">
            <a-tag v-if="record.categoryLevel1Name" color="blue" class="category-tag">
              {{ record.categoryLevel1Name }}
            </a-tag>
            <span v-else>-</span>
          </template>
        </a-table-column>
        <a-table-column key="categoryLevel2Name" data-index="categoryLevel2Name" title="二级分类" width="120">
          <template #default="{ record }">
            <a-tag v-if="record.categoryLevel2Name" color="cyan" class="category-tag">
              {{ record.categoryLevel2Name }}
            </a-tag>
            <span v-else>-</span>
          </template>
        </a-table-column>
        <a-table-column key="totalStockQuantity" data-index="totalStockQuantity" title="库存" width="80" />
        <a-table-column key="ladderPrice1" title="阶梯价1" width="110">
          <template #default="{ record }">
            <a-tooltip v-if="record.ladderPrice1Quantity && record.ladderPrice1Price" placement="top">
              <template #title>
                <div class="price-tooltip">
                  <div v-if="record.ladderPrice1Quantity">阶梯1: {{ record.ladderPrice1Quantity }}+ = ￥{{ record.ladderPrice1Price }}</div>
                  <div v-if="record.ladderPrice2Quantity">阶梯2: {{ record.ladderPrice2Quantity }}+ = ￥{{ record.ladderPrice2Price }}</div>
                  <div v-if="record.ladderPrice3Quantity">阶梯3: {{ record.ladderPrice3Quantity }}+ = ￥{{ record.ladderPrice3Price }}</div>
                  <div v-if="record.ladderPrice4Quantity">阶梯4: {{ record.ladderPrice4Quantity }}+ = ￥{{ record.ladderPrice4Price }}</div>
                  <div v-if="record.ladderPrice5Quantity">阶梯5: {{ record.ladderPrice5Quantity }}+ = ￥{{ record.ladderPrice5Price }}</div>
                </div>
              </template>
              <span class="price-cell">{{ record.ladderPrice1Quantity }}+: ￥{{ record.ladderPrice1Price }}</span>
            </a-tooltip>
            <span v-else>-</span>
          </template>
        </a-table-column>
        <a-table-column key="lastCrawledAt" data-index="lastCrawledAt" title="最后爬取时间" width="160">
          <template #default="{ record }">
            {{ formatDateTime(record.lastCrawledAt || record.createdAt) }}
          </template>
        </a-table-column>
        <a-table-column key="action" title="操作" width="320" fixed="right">
          <template #default="{ record }">
            <a-space >
              <a-button size="small" @click="handleEdit(record)">
                <template #icon>
                  <EditOutlined />
                </template>
                编辑
              </a-button>
<!--              <a-button size="small" @click="handleViewResources(record.productCode)" class="btn-info">-->
<!--                <template #icon>-->
<!--                  <FolderOutlined />-->
<!--                </template>-->
<!--                资源-->
<!--              </a-button>-->
<!--              <a-button size="small" @click="handleCrawl(record.productCode)" class="btn-warning">-->
<!--                <template #icon>-->
<!--                  <ReloadOutlined />-->
<!--                </template>-->
<!--                更新-->
<!--              </a-button>-->
              <a-button size="small" danger @click="handleDelete(record)">
                <template #icon>
                  <DeleteOutlined />
                </template>
                删除
              </a-button>
            </a-space>
          </template>
        </a-table-column>
      </a-table>

      <!-- 分页 -->
      <div class="mt-4 text-right">
        <a-pagination
          v-model:current="pagination.current"
          v-model:page-size="pagination.size"
          :page-size-options="['10', '20', '50', '100']"
          :total="pagination.total"
          show-size-changer
          show-quick-jumper
          show-total
          @change="handleCurrentChange"
          @show-size-change="handleSizeChange"
        >
          <template #buildOptionText="props">
            <span>{{ props.value }}条/页</span>
          </template>
        </a-pagination>
      </div>
    </a-card>

    <!-- 新增/编辑产品对话框 -->
    <a-modal 
      v-model:visible="showAddDialog" 
      :title="editingProduct.id ? '编辑产品' : '新增产品'" 
      width="800px"
      @ok="handleSaveProduct"
      @cancel="resetEditingProduct"
    >
      <a-form
        ref="productFormRef"
        :model="editingProduct"
        :rules="productRules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 18 }"
      >
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item label="产品编号" name="productCode">
              <a-input v-model:value="editingProduct.productCode" placeholder="如：C123456" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="品牌" name="brand">
              <a-input v-model:value="editingProduct.brand" placeholder="输入品牌名称" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item label="型号" name="model">
              <a-input v-model:value="editingProduct.model" placeholder="输入型号" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="封装" name="packageName">
              <a-input v-model:value="editingProduct.packageName" placeholder="输入封装名称" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item label="一级分类" name="categoryLevel1Id">
              <a-select
                v-model:value="editingProduct.categoryLevel1Id"
                placeholder="选择一级分类"
                style="width: 100%"
                @change="handleEditLevel1Change"
              >
                <a-select-option
                  v-for="item in level1Categories"
                  :key="item.id"
                  :label="item.categoryLevel1Name"
                  :value="item.id"
                >
                  {{ item.categoryLevel1Name }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="二级分类" name="categoryLevel2Id">
              <a-select
                v-model:value="editingProduct.categoryLevel2Id"
                placeholder="选择二级分类"
                style="width: 100%"
                :disabled="!editingProduct.categoryLevel1Id"
              >
                <a-select-option
                  v-for="item in editLevel2Categories"
                  :key="item.id"
                  :label="item.categoryLevel2Name"
                  :value="item.id"
                >
                  {{ item.categoryLevel2Name }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item label="库存数量">
              <a-input-number
                v-model:value="editingProduct.totalStockQuantity"
                :min="0"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="图片名称">
              <a-input v-model:value="editingProduct.imageName" placeholder="如：C123456_001.jpg" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="简介" :label-col="{ span: 3 }" :wrapper-col="{ span: 21 }">
          <a-textarea
            v-model:value="editingProduct.briefDescription"
            :rows="3"
            placeholder="产品简介，最多200字"
            :maxlength="200"
            show-count
          />
        </a-form-item>

        <!-- 阶梯价格独立输入 -->
        <a-divider orientation="left">阶梯价格</a-divider>
        <div class="ladder-price-section">
          <a-row :gutter="20" class="ladder-price-row">
            <a-col :span="12">
              <a-form-item label="阶梯1 数量" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                <a-input-number
                  v-model:value="editingProduct.ladderPrice1Quantity"
                  :min="0"
                  style="width: 100%"
                  placeholder="起订量"
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="阶梯1 价格" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                <a-input-number
                  v-model:value="editingProduct.ladderPrice1Price"
                  :min="0"
                  :precision="4"
                  style="width: 100%"
                  placeholder="单价 (USD)"
                />
              </a-form-item>
            </a-col>
          </a-row>

          <a-row :gutter="20" class="ladder-price-row">
            <a-col :span="12">
              <a-form-item label="阶梯2 数量" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                <a-input-number
                  v-model:value="editingProduct.ladderPrice2Quantity"
                  :min="0"
                  style="width: 100%"
                  placeholder="起订量"
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="阶梯2 价格" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                <a-input-number
                  v-model:value="editingProduct.ladderPrice2Price"
                  :min="0"
                  :precision="4"
                  style="width: 100%"
                  placeholder="单价 (USD)"
                />
              </a-form-item>
            </a-col>
          </a-row>

          <a-row :gutter="20" class="ladder-price-row">
            <a-col :span="12">
              <a-form-item label="阶梯3 数量" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                <a-input-number
                  v-model:value="editingProduct.ladderPrice3Quantity"
                  :min="0"
                  style="width: 100%"
                  placeholder="起订量"
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="阶梯3 价格" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                <a-input-number
                  v-model:value="editingProduct.ladderPrice3Price"
                  :min="0"
                  :precision="4"
                  style="width: 100%"
                  placeholder="单价 (USD)"
                />
              </a-form-item>
            </a-col>
          </a-row>

          <a-row :gutter="20" class="ladder-price-row">
            <a-col :span="12">
              <a-form-item label="阶梯4 数量" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                <a-input-number
                  v-model:value="editingProduct.ladderPrice4Quantity"
                  :min="0"
                  style="width: 100%"
                  placeholder="起订量"
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="阶梯4 价格" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                <a-input-number
                  v-model:value="editingProduct.ladderPrice4Price"
                  :min="0"
                  :precision="4"
                  style="width: 100%"
                  placeholder="单价 (USD)"
                />
              </a-form-item>
            </a-col>
          </a-row>

          <a-row :gutter="20" class="ladder-price-row">
            <a-col :span="12">
              <a-form-item label="阶梯5 数量" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                <a-input-number
                  v-model:value="editingProduct.ladderPrice5Quantity"
                  :min="0"
                  style="width: 100%"
                  placeholder="起订量"
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="阶梯5 价格" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                <a-input-number
                  v-model:value="editingProduct.ladderPrice5Price"
                  :min="0"
                  :precision="4"
                  style="width: 100%"
                  placeholder="单价 (USD)"
                />
              </a-form-item>
            </a-col>
          </a-row>
        </div>
      </a-form>
    </a-modal>

    <!-- 爬取产品对话框 -->
    <a-modal 
      v-model:visible="showCrawlerDialog" 
      title="爬取产品" 
      width="500px"
      @ok="handleCrawlerSubmit"
    >
      <a-form>
        <a-form-item label="产品编号">
          <a-input 
            v-model:value="crawlerForm.productCode" 
            placeholder="输入产品编号，如：C123456"
          />
        </a-form-item>
        <a-form-item label="批量爬取">
          <a-textarea 
            v-model:value="crawlerForm.batchCodes" 
            :rows="4"
            placeholder="多个产品编号用换行分隔"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 图片预览对话框 -->
    <a-modal
      v-model:visible="showImagePreview"
      title="产品主图"
      width="800px"
      :footer="null"
    >
      <div class="image-preview-container">
        <img :src="previewImageUrl" style="width: 100%; max-height: 600px; object-fit: contain;" @error="handleImageError" />
      </div>
    </a-modal>

    <!-- 资源查看对话框 -->
    <a-modal
      v-model:visible="showResourcesDialog"
      :title="`产品资源 - ${currentProductCode}`"
      width="1000px"
      :footer="null"
    >
      <div v-if="productResources.total === 0" class="empty-resources">
        <a-empty description="暂无资源文件" />
      </div>
      <div v-else class="resources-gallery">
        <!-- 资源分类标签 -->
        <a-tabs v-model:activeKey="activeResourceTab" type="card">
          <a-tab-pane key="all" :tab="`全部 (${productResources.total})`">
            <div class="resource-grid">
              <div
                v-for="(resource, index) in productResources.all"
                :key="index"
                class="resource-item"
                :class="{'pdf-item': resource.category === 'pdf'}"
              >
                <div class="resource-info">
                  <div class="resource-name">{{ resource.filename }}</div>
                  <div class="resource-meta">
                    <span class="resource-category" :class="resource.category">
                      {{ resource.category === 'image' ? '图片' : 'PDF' }}
                    </span>
                    <span class="resource-type">{{ resource.type }}</span>
                    <span class="resource-size">{{ formatFileSize(resource.size) }}</span>
                  </div>
                </div>
                <div class="resource-preview" @click="handleResourcePreview(resource)">
                  <div v-if="resource.category === 'image'" class="image-preview">
                    <img
                      :src="`http://localhost:8080/api${resource.url}`"
                      :alt="resource.filename"
                      @error="handleImageError"
                    />
                  </div>
                  <div v-else class="pdf-preview">
                    <FileOutlined style="font-size: 48px; color: #ff4d4f;" />
                    <div class="pdf-label">PDF文档</div>
                    <a-button type="link" size="small" @click.stop="openPdfInNewTab(resource)">
                      <template #icon><EyeOutlined /></template>
                      预览
                    </a-button>
                  </div>
                </div>
              </div>
            </div>
          </a-tab-pane>
          <a-tab-pane key="images" :tab="`图片 (${productResources.images.length})`">
            <div class="resource-grid">
              <div
                v-for="(image, index) in productResources.images"
                :key="index"
                class="resource-item"
              >
                <div class="resource-info">
                  <div class="resource-name">{{ image.filename }}</div>
                  <div class="resource-meta">
                    <span class="resource-category image">图片</span>
                    <span class="resource-type">{{ image.type }}</span>
                    <span class="resource-size">{{ formatFileSize(image.size) }}</span>
                  </div>
                </div>
                <div class="resource-preview" @click="handleResourcePreview(image)">
                  <img
                    :src="`http://localhost:8080/api${image.url}`"
                    :alt="image.filename"
                    @error="handleImageError"
                  />
                </div>
              </div>
            </div>
          </a-tab-pane>
          <a-tab-pane key="pdfs" :tab="`PDF (${productResources.pdfs.length})`">
            <div class="resource-grid">
              <div
                v-for="(pdf, index) in productResources.pdfs"
                :key="index"
                class="resource-item pdf-item"
              >
                <div class="resource-info">
                  <div class="resource-name">{{ pdf.filename }}</div>
                  <div class="resource-meta">
                    <span class="resource-category pdf">PDF</span>
                    <span class="resource-type">{{ pdf.type }}</span>
                    <span class="resource-size">{{ formatFileSize(pdf.size) }}</span>
                  </div>
                </div>
                <div class="resource-preview" @click="openPdfInNewTab(pdf)">
                  <FileOutlined style="font-size: 48px; color: #ff4d4f;" />
                  <div class="pdf-label">PDF文档</div>
                  <a-button type="link" size="small">
                    <template #icon><EyeOutlined /></template>
                    预览
                  </a-button>
                </div>
              </div>
            </div>
          </a-tab-pane>
        </a-tabs>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useRoute } from 'vue-router'
import {
  SearchOutlined,
  ReloadOutlined,
  CloudDownloadOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  BarChartOutlined,
  FolderOutlined,
  FileOutlined,
  EyeOutlined,
  DownloadOutlined,
  FileExcelOutlined,
  FileTextOutlined,
  DownOutlined
} from '@ant-design/icons-vue'
import {
  getProductPage,
  deleteProduct,
  crawlProduct,
  crawlProductBatch,
  addProduct,
  updateProduct,
  getProductStatistics,
  getProductImages,
  getProductResources,
  exportAllProductsExcel,
  exportProductsExcel,
  exportProductsCSV,
  downloadExportFile
} from '@/api/product'
import { getCategoryLevel1List, getCategoryLevel2ListByLevel1Id } from '@/api/category'
import type { Product, CategoryLevel1Code, CategoryLevel2Code, ProductResources, ResourceFile } from '@/types'

const route = useRoute()

// 响应式数据
const loading = ref(false)
const tableData = ref<Product[]>([])
const showCrawlerDialog = ref(false)
const showAddDialog = ref(false)
const showResourcesDialog = ref(false)
const showImagePreview = ref(false)
const previewImageUrl = ref('')
const productFormRef = ref()
const currentProductCode = ref('')
const productResources = ref<ProductResources>({
  all: [],
  images: [],
  pdfs: [],
  total: 0
})
const activeResourceTab = ref('all')

const level1Categories = ref<CategoryLevel1Code[]>([])
const level2Categories = ref<CategoryLevel2Code[]>([])
const editLevel2Categories = ref<CategoryLevel2Code[]>([])

const searchForm = reactive({
  productCode: '',
  brand: '',
  model: '',
  categoryLevel1Id: undefined as number | undefined,
  categoryLevel2Id: undefined as number | undefined,
  hasStock: undefined as boolean | undefined
})

const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const crawlerForm = reactive({
  productCode: '',
  batchCodes: ''
})

const editingProduct = reactive<Product>({
  productCode: '',
  categoryLevel1Id: 0,
  categoryLevel2Id: 0,
  brand: '',
  model: '',
  packageName: '',
  totalStockQuantity: 0,
  briefDescription: '',
  tierPrices: '',
  ladderPrice1Quantity: undefined,
  ladderPrice1Price: undefined,
  ladderPrice2Quantity: undefined,
  ladderPrice2Price: undefined,
  ladderPrice3Quantity: undefined,
  ladderPrice3Price: undefined,
  ladderPrice4Quantity: undefined,
  ladderPrice4Price: undefined,
  ladderPrice5Quantity: undefined,
  ladderPrice5Price: undefined
})

// 表单验证规则
const productRules = {
  productCode: [
    { required: true, message: '请输入产品编号', trigger: 'blur' }
  ],
  categoryLevel1Id: [
    { required: true, message: '请选择一级分类', trigger: 'change' }
  ],
  categoryLevel2Id: [
    { required: true, message: '请选择二级分类', trigger: 'change' }
  ]
}

// 方法
const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      productCode: searchForm.productCode,
      brand: searchForm.brand,
      model: searchForm.model,
      categoryLevel1Id: searchForm.categoryLevel1Id,
      categoryLevel2Id: searchForm.categoryLevel2Id,
      hasStock: searchForm.hasStock
    }
    console.log('调用产品搜索API，参数:', params)
    const result = await getProductPage(params)
    console.log('产品搜索API响应:', result)
    console.log('result类型:', typeof result)
    console.log('result.records:', result.records)
    console.log('result.records长度:', result.records?.length)
    console.log('result.total:', result.total)

    tableData.value = result.records
    pagination.total = result.total

    console.log('tableData.value:', tableData.value)
    console.log('tableData.value长度:', tableData.value?.length)
    console.log('pagination.total:', pagination.total)
  } catch (error) {
    console.error('获取产品数据失败:', error)
    message.error('获取产品数据失败')
  } finally {
    loading.value = false
  }
}

// 加载分类数据
const loadCategories = async () => {
  try {
    console.log('开始加载一级分类数据...')
    level1Categories.value = await getCategoryLevel1List()
    console.log('一级分类数据加载成功，数量:', level1Categories.value.length)
    console.log('一级分类数据:', level1Categories.value)
  } catch (error) {
    console.error('获取分类数据失败:', error)
    message.error('获取分类数据失败')
  }
}

// 处理一级分类变化（搜索）
const handleLevel1Change = async (categoryLevel1Id: number | undefined) => {
  console.log('一级分类发生变化:', categoryLevel1Id)
  searchForm.categoryLevel2Id = undefined
  level2Categories.value = []
  
  if (categoryLevel1Id) {
    try {
      console.log('开始加载二级分类数据，一级分类ID:', categoryLevel1Id)
      level2Categories.value = await getCategoryLevel2ListByLevel1Id(categoryLevel1Id)
      console.log('二级分类数据加载成功，数量:', level2Categories.value.length)
      console.log('二级分类数据:', level2Categories.value)
    } catch (error) {
      console.error('获取二级分类失败:', error)
      message.error('获取二级分类失败')
    }
  }
}

// 处理一级分类变化（编辑）
const handleEditLevel1Change = async (categoryLevel1Id: number | undefined) => {
  editingProduct.categoryLevel2Id = 0
  editLevel2Categories.value = []
  
  if (categoryLevel1Id) {
    try {
      editLevel2Categories.value = await getCategoryLevel2ListByLevel1Id(categoryLevel1Id)
    } catch (error) {
      console.error('获取二级分类失败:', error)
    }
  }
}

const handleSearch = () => {
  pagination.current = 1
  fetchData()
}

const handleReset = () => {
  searchForm.productCode = ''
  searchForm.brand = ''
  searchForm.model = ''
  searchForm.categoryLevel1Id = undefined
  searchForm.categoryLevel2Id = undefined
  searchForm.hasStock = undefined
  level2Categories.value = []
  pagination.current = 1
  fetchData()
}

const handleEdit = async (row: Product) => {
  Object.assign(editingProduct, row)
  
  // 加载对应的二级分类
  if (row.categoryLevel1Id) {
    try {
      editLevel2Categories.value = await getCategoryLevel2ListByLevel1Id(row.categoryLevel1Id)
    } catch (error) {
      console.error('获取二级分类失败:', error)
    }
  }
  
  showAddDialog.value = true
}

const handleSaveProduct = async () => {
  if (!productFormRef.value) return
  
  try {
    await productFormRef.value.validate()
    
    if (editingProduct.id) {
      await updateProduct(editingProduct.id, editingProduct)
      message.success('更新产品成功')
    } else {
      await addProduct(editingProduct)
      message.success('新增产品成功')
    }
    
    showAddDialog.value = false
    resetEditingProduct()
    fetchData()
  } catch (error) {
    console.error('保存产品失败:', error)
  }
}

const resetEditingProduct = () => {
  Object.assign(editingProduct, {
    id: undefined,
    productCode: '',
    categoryLevel1Id: 0,
    categoryLevel2Id: 0,
    brand: '',
    model: '',
    packageName: '',
    totalStockQuantity: 0,
    briefDescription: '',
    tierPrices: '',
    ladderPrice1Quantity: undefined,
    ladderPrice1Price: undefined,
    ladderPrice2Quantity: undefined,
    ladderPrice2Price: undefined,
    ladderPrice3Quantity: undefined,
    ladderPrice3Price: undefined,
    ladderPrice4Quantity: undefined,
    ladderPrice4Price: undefined,
    ladderPrice5Quantity: undefined,
    ladderPrice5Price: undefined
  })
  editLevel2Categories.value = []
}

const showStatistics = async () => {
  try {
    const stats = await getProductStatistics()
    Modal.info({
      title: '产品统计信息',
      content: `总产品数：${stats.totalProducts}\n有库存产品：${stats.productsWithStock}\n无库存产品：${stats.productsWithoutStock}`,
      okText: '确定'
    })
  } catch (error) {
    message.error('获取统计信息失败')
  }
}

const handleCrawl = async (productCode: string) => {
  try {
    await crawlProduct(productCode)
    message.success('开始爬取产品信息')
    // 延时刷新数据
    setTimeout(() => {
      fetchData()
    }, 5000)
  } catch (error) {
    message.error('爬取失败')
  }
}

const handleViewResources = async (productCode: string) => {
  try {
    currentProductCode.value = productCode
    const resources = await getProductResources(productCode)
    productResources.value = resources || {
      all: [],
      images: [],
      pdfs: [],
      total: 0
    }
    activeResourceTab.value = 'all'
    showResourcesDialog.value = true
  } catch (error) {
    message.error('获取资源列表失败')
  }
}

const handleResourcePreview = (resource: ResourceFile) => {
  if (resource.category === 'image') {
    // 图片预览 - 可以添加大图预览功能
    const imageUrl = `http://localhost:8080/api${resource.url}`
    window.open(imageUrl, '_blank')
  } else {
    // PDF预览
    openPdfInNewTab(resource)
  }
}

const openPdfInNewTab = (resource: ResourceFile) => {
  const pdfUrl = `http://localhost:8080/api${resource.url}`
  window.open(pdfUrl, '_blank')
}

const handleDelete = async (row: Product) => {
  Modal.confirm({
    title: '确定删除此产品吗？',
    content: '删除后无法恢复',
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteProduct(row.id!)
        message.success('删除成功')
        fetchData()
      } catch (error) {
        message.error('删除失败')
      }
    }
  })
}

const handleCrawlerSubmit = async () => {
  try {
    if (crawlerForm.productCode) {
      await crawlProduct(crawlerForm.productCode)
      message.success('开始爬取单个产品')
    }
    
    if (crawlerForm.batchCodes) {
      const codes = crawlerForm.batchCodes.split('\n').filter(code => code.trim())
      await crawlProductBatch(codes)
      message.success(`开始批量爬取 ${codes.length} 个产品`)
    }
    
    showCrawlerDialog.value = false
    crawlerForm.productCode = ''
    crawlerForm.batchCodes = ''
    
    // 延时刷新数据
    setTimeout(() => {
      fetchData()
    }, 5000)
  } catch (error) {
    message.error('爬取失败')
  }
}

const handleSizeChange = (current: number, size: number) => {
  pagination.size = size
  fetchData()
}

const handleCurrentChange = (current: number) => {
  pagination.current = current
  fetchData()
}

const formatDateTime = (dateTime: string | undefined) => {
  if (!dateTime) return '-'
  return new Date(dateTime).toLocaleString('zh-CN')
}

const formatFileSize = (bytes: number) => {
  if (bytes === 0) return '0 Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const handleImageError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjQiIGhlaWdodD0iNjQiIHZpZXdCb3g9IjAgMCA2NCA2NCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHJlY3Qgd2lkdGg9IjY0IiBoZWlnaHQ9IjY0IiBmaWxsPSIjRjVGNUY1Ii8+CjxwYXRoIGQ9Ik0yMS4zMzMzIDIxLjMzMzNIMzIuMDAwMCIgc3Ryb2tlPSIjQkZCRkJGIiBzdHJva2Utd2lkdGg9IjIiLz4KPHA+'
}

const handleImagePreview = (imageUrl: string) => {
  previewImageUrl.value = imageUrl
  showImagePreview.value = true
}

// 处理导出菜单点击
const handleExportMenuClick = async ({ key }: { key: string }) => {
  loading.value = true

  try {
    let response: any

    switch (key) {
      case 'excel-all':
        // 导出所有产品
        response = await exportAllProductsExcel()
        break

      case 'excel-current':
        // 导出当前搜索结果
        response = await exportProductsExcel({
          categoryLevel1Id: searchForm.categoryLevel1Id,
          categoryLevel2Id: searchForm.categoryLevel2Id,
          brand: searchForm.brand,
          productCode: searchForm.productCode,
          model: searchForm.model,
          hasStock: searchForm.hasStock
        })
        break

      case 'csv-current':
        // 导出CSV
        response = await exportProductsCSV({
          categoryLevel1Id: searchForm.categoryLevel1Id,
          categoryLevel2Id: searchForm.categoryLevel2Id,
          brand: searchForm.brand,
          productCode: searchForm.productCode,
          model: searchForm.model,
          hasStock: searchForm.hasStock
        })
        break

      default:
        return
    }

    // 响应拦截器已将标准Result包裹的data解包，故此处直接使用返回对象
    // 若请求失败会在拦截器抛错进入catch
    if (response && typeof response === 'object') {
      const { filename, recordCount } = response
      message.success(`导出成功！共 ${recordCount} 条记录，正在下载...`)

      // 触发文件下载
      setTimeout(() => {
        downloadExportFile(filename)
      }, 500)
    } else {
      message.error('导出失败')
    }

  } catch (error) {
    console.error('导出失败:', error)
    message.error('导出失败，请重试')
  } finally {
    loading.value = false
  }
}

// 生命周期
onMounted(async () => {
  // 从 URL 查询参数中读取分类ID
  const categoryLevel2Id = route.query.categoryLevel2Id
  if (categoryLevel2Id) {
    const categoryId = Number(categoryLevel2Id)
    if (!isNaN(categoryId)) {
      searchForm.categoryLevel2Id = categoryId
      console.log('从URL读取到分类参数:', categoryId)
    }
  }

  await loadCategories()

  // 如果有分类参数，需要加载对应的二级分类列表
  if (searchForm.categoryLevel2Id) {
    try {
      // 先获取该二级分类所属的一级分类
      const allLevel2Categories = await Promise.all(
        level1Categories.value.map(level1 =>
          getCategoryLevel2ListByLevel1Id(level1.id).catch(() => [])
        )
      )

      // 找到包含目标二级分类的一级分类
      for (let i = 0; i < level1Categories.value.length; i++) {
        const level2List = allLevel2Categories[i]
        const found = level2List.find(cat => cat.id === searchForm.categoryLevel2Id)
        if (found) {
          searchForm.categoryLevel1Id = level1Categories.value[i].id
          level2Categories.value = level2List
          console.log('自动设置一级分类:', level1Categories.value[i].id)
          break
        }
      }
    } catch (error) {
      console.error('加载分类数据失败:', error)
    }
  }

  fetchData()
})
</script>

<style scoped>
.product-management {
  padding: 0;
}

.mb-4 {
  margin-bottom: 16px;
}

.mt-4 {
  margin-top: 16px;
}

.text-right {
  text-align: right;
}

.price-item {
  margin-bottom: 4px;
}

/* 主图缩略图样式 */
.product-thumbnail {
  width: 50px;
  height: 50px;
  object-fit: cover;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid #d9d9d9;
}

.product-thumbnail:hover {
  transform: scale(1.1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  border-color: #1890ff;
}

.no-image {
  display: inline-block;
  width: 50px;
  height: 50px;
  line-height: 50px;
  text-align: center;
  background: #f5f5f5;
  color: #999;
  font-size: 12px;
  border-radius: 4px;
}

/* 分类标签样式 */
.category-tag {
  font-size: 12px;
  padding: 2px 8px;
  margin: 0;
}

/* 价格显示样式 */
.price-cell {
  font-family: 'Courier New', monospace;
  color: #ff4d4f;
  font-weight: 500;
  font-size: 13px;
}

.price-tooltip {
  max-width: 250px;
}

.price-tooltip div {
  margin-bottom: 4px;
  font-size: 12px;
}

/* 图片预览容器 */
.image-preview-container {
  display: flex;
  justify-content: center;
  align-items: center;
  background: #f5f5f5;
  padding: 20px;
  border-radius: 4px;
}

/* 阶梯价格表单布局 */
.ladder-price-section {
  background: #fafafa;
  padding: 16px;
  border-radius: 4px;
  margin-bottom: 16px;
  border: 1px solid #e8e8e8;
}

.ladder-price-row {
  margin-bottom: 0;
}

.search-actions {
  margin-top: 16px;
  text-align: right;
}

.search-actions .ant-btn {
  margin-left: 8px;
}

.btn-success {
  background-color: #52c41a;
  border-color: #52c41a;
  color: white;
}

.btn-success:hover {
  background-color: #73d13d;
  border-color: #73d13d;
}

.btn-warning {
  background-color: #faad14;
  border-color: #faad14;
  color: white;
}

.btn-warning:hover {
  background-color: #ffc53d;
  border-color: #ffc53d;
}

.btn-info {
  background-color: #1890ff;
  border-color: #1890ff;
  color: white;
}

.btn-info:hover {
  background-color: #40a9ff;
  border-color: #40a9ff;
}

/* 资源查看对话框样式 */
.empty-resources {
  text-align: center;
  padding: 40px 0;
}

.resources-gallery {
  max-height: 600px;
  overflow-y: auto;
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  padding: 16px 0;
}

.resource-item {
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
  cursor: pointer;
  transition: all 0.3s ease;
}

.resource-item:hover {
  border-color: #1890ff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.resource-item.pdf-item {
  border-left: 4px solid #ff4d4f;
}

.resource-info {
  padding: 8px 12px;
  border-bottom: 1px solid #e8e8e8;
}

.resource-name {
  font-size: 12px;
  font-weight: 500;
  color: #262626;
  margin-bottom: 4px;
  word-break: break-all;
}

.resource-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 11px;
}

.resource-category {
  padding: 2px 6px;
  border-radius: 4px;
  color: #fff;
  font-weight: 500;
}

.resource-category.image {
  background: #52c41a;
}

.resource-category.pdf {
  background: #ff4d4f;
}

.resource-type {
  background: #f0f0f0;
  padding: 2px 6px;
  border-radius: 4px;
  color: #595959;
}

.resource-size {
  color: #8c8c8c;
}

.resource-preview {
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  position: relative;
}

.resource-preview img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.pdf-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 8px;
}

.pdf-label {
  font-size: 12px;
  color: #666;
  font-weight: 500;
}
</style>
