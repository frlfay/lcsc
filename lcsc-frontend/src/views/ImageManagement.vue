<template>
  <div class="image-management">
    <!-- 搜索区域 -->
    <a-card class="mb-4">
      <a-form layout="inline" :model="searchForm">
        <a-form-item label="图片名称">
          <a-input v-model:value="searchForm.imageName" placeholder="输入图片名称" style="width: 200px" />
        </a-form-item>
        <a-form-item label="所属店铺">
          <a-select
            v-model:value="searchForm.shopId"
            placeholder="选择店铺"
            style="width: 200px"
            allow-clear
          >
            <a-select-option
              v-for="item in shopList"
              :key="item.id"
              :value="item.id"
            >
              {{ item.shopName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="图片链接">
          <a-input v-model:value="searchForm.imageLink" placeholder="输入图片链接" style="width: 200px" />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="handleSearch">
              <template #icon><SearchOutlined /></template>
              搜索
            </a-button>
            <a-button @click="handleReset">
              <template #icon><ReloadOutlined /></template>
              重置
            </a-button>
            <a-button type="primary" @click="showAddDialog = true; resetEditingImageLink()">
              <template #icon><PlusOutlined /></template>
              新增
            </a-button>
            <a-button @click="showBatchImportDialog = true">
              <template #icon><UploadOutlined /></template>
              批量导入
            </a-button>
            <a-button @click="exportData">
              <template #icon><DownloadOutlined /></template>
              导出
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 图片链接列表 -->
    <a-card>
      <a-table
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1000 }"
        row-key="id"
        table-layout="fixed"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'shopName'">
            {{ getShopName(record.shopId) }}
          </template>
          <template v-else-if="column.key === 'imageLink'">
            <a-tooltip :title="record.imageLink" placement="topLeft">
              <a-typography-text
                copyable
                class="image-link-text"
              >
                {{ record.imageLink }}
              </a-typography-text>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'preview'">
            <a-button type="link" size="small" @click="previewImage(record)">
              <template #icon><EyeOutlined /></template>
            </a-button>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatDateTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a-button size="small" @click="handleEdit(record)">编辑</a-button>
              <a-button size="small" danger @click="handleDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <!-- 分页 -->
      <div class="mt-4 text-right">
        <a-pagination
          v-model:current="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-size-options="['10', '20', '50', '100']"
          :show-total="(total: number) => `共 ${total} 条`"
          show-size-changer
          show-quick-jumper
          @change="handleCurrentChange"
          @show-size-change="handleSizeChange"
        />
      </div>
    </a-card>

    <!-- 新增/编辑图片链接对话框 -->
    <a-modal
      v-model:open="showAddDialog"
      :title="editingImageLink.id ? '编辑图片链接' : '新增图片链接'"
      width="700px"
      @ok="handleSaveImageLink"
    >
      <a-form
        ref="imageLinkFormRef"
        :model="editingImageLink"
        :rules="imageLinkRules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 18 }"
      >
        <a-form-item label="图片名称" name="imageName">
          <a-input v-model:value="editingImageLink.imageName" placeholder="如：C123456_001.jpg" />
          <div class="form-tip">建议格式：产品编号_序号.jpg</div>
        </a-form-item>
        <a-form-item label="所属店铺" name="shopId">
          <a-select
            v-model:value="editingImageLink.shopId"
            placeholder="选择店铺"
            style="width: 100%"
          >
            <a-select-option
              v-for="item in shopList"
              :key="item.id"
              :value="item.id"
            >
              {{ item.shopName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="图片链接" name="imageLink">
          <a-textarea 
            v-model:value="editingImageLink.imageLink" 
            placeholder="输入完整的图片链接URL"
            :rows="3"
          />
          <div class="form-tip">请输入完整的HTTP/HTTPS链接</div>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 批量导入对话框 -->
    <a-modal
      v-model:open="showBatchImportDialog"
      title="批量导入图片链接"
      width="800px"
      :footer="null"
      @cancel="handleImportCancel"
    >
      <div class="import-section">
        <h4>Excel导入</h4>
        <a-alert
          message="请先下载模板，填写数据后上传Excel文件"
          type="info"
          :closable="false"
          style="margin-bottom: 16px"
        />

        <a-space direction="vertical" :size="16" style="width: 100%">
          <!-- 步骤1：下载模板 -->
          <div>
            <h5>步骤1：下载模板</h5>
            <a-button type="primary" @click="handleDownloadTemplate">
              <template #icon><DownloadOutlined /></template>
              下载Excel模板
            </a-button>
          </div>

          <!-- 步骤2：上传文件 -->
          <div>
            <h5>步骤2：上传Excel文件</h5>
            <a-upload
              :file-list="uploadFileList"
              :before-upload="beforeUpload"
              :remove="handleRemoveFile"
              accept=".xlsx,.xls"
              :max-count="1"
            >
              <a-button>
                <template #icon><UploadOutlined /></template>
                选择Excel文件
              </a-button>
            </a-upload>
            <div class="form-tip" style="margin-top: 8px">
              支持.xlsx和.xls格式，单次最多上传1个文件
            </div>
          </div>

          <!-- 步骤3：导入 -->
          <div>
            <a-space>
              <a-button
                type="primary"
                :loading="importLoading"
                :disabled="uploadFileList.length === 0"
                @click="handleExcelImport"
              >
                <template #icon><UploadOutlined /></template>
                开始导入
              </a-button>
              <a-button @click="handleImportCancel">取消</a-button>
            </a-space>
          </div>

          <!-- 导入结果 -->
          <div v-if="importResult" class="import-result">
            <a-divider>导入结果</a-divider>
            <a-result
              :status="importResult.failureCount === 0 ? 'success' : 'warning'"
              :title="`成功导入 ${importResult.successCount} 条，失败 ${importResult.failureCount} 条`"
            >
              <template #extra>
                <a-space>
                  <a-button type="primary" @click="handleImportComplete">完成</a-button>
                  <a-button v-if="importResult.failureCount > 0" @click="showErrorDetails = !showErrorDetails">
                    {{ showErrorDetails ? '隐藏' : '查看' }}错误详情
                  </a-button>
                </a-space>
              </template>
            </a-result>

            <!-- 错误详情 -->
            <div v-if="showErrorDetails && importResult.errors.length > 0" class="error-details">
              <h5>错误详情：</h5>
              <a-list
                size="small"
                :data-source="importResult.errors"
                :pagination="{ pageSize: 5 }"
              >
                <template #renderItem="{ item }">
                  <a-list-item>
                    <strong>第 {{ item.rowNumber }} 行：</strong>
                    <a-typography-text type="danger">
                      {{ item.errors.join('; ') }}
                    </a-typography-text>
                  </a-list-item>
                </template>
              </a-list>
            </div>
          </div>
        </a-space>

        <div class="import-example" style="margin-top: 24px">
          <h5>Excel格式说明：</h5>
          <ul style="margin: 8px 0; padding-left: 20px; font-size: 12px">
            <li>第1列：店铺名称（必填）</li>
            <li>第2列：产品编号（选填，仅用于参考）</li>
            <li>第3列：图片名称（必填，如：C123456_front.jpg）</li>
            <li>第4列：图片链接（必填，需以http://或https://开头）</li>
          </ul>
        </div>
      </div>
    </a-modal>

    <!-- 图片预览对话框 -->
    <a-modal
      v-model:open="showPreviewDialog"
      title="图片预览"
      width="600px"
      :footer="null"
    >
      <div class="image-preview" v-if="previewImageUrl">
        <img 
          :src="previewImageUrl" 
          :alt="previewImageName"
          @load="onImageLoad"
          @error="onImageError"
          style="max-width: 100%; max-height: 400px; display: block; margin: 0 auto;"
        />
        <div class="image-info">
          <p><strong>图片名称：</strong>{{ previewImageName }}</p>
          <p><strong>图片链接：</strong>{{ previewImageUrl }}</p>
        </div>
      </div>
      <div v-else class="no-image">
        <a-empty description="图片加载失败" />
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { 
  SearchOutlined, 
  ReloadOutlined, 
  PlusOutlined, 
  EditOutlined, 
  DeleteOutlined, 
  UploadOutlined, 
  DownloadOutlined, 
  EyeOutlined 
} from '@ant-design/icons-vue'
import {
  getImageLinkPage,
  addImageLink,
  updateImageLink,
  deleteImageLink,
  importImageLinksFromExcel,
  downloadImportTemplate
} from '@/api/imageLink'
import { getShopList } from '@/api/shop'
import type { ImageLink, Shop, ImageLinkImportResult } from '@/types'
import type { FormInstance } from 'ant-design-vue'
import type { UploadProps } from 'ant-design-vue'

// 表格列定义
const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
    key: 'id',
    width: 70
  },
  {
    title: '图片名称',
    dataIndex: 'imageName',
    key: 'imageName',
    width: 180,
    ellipsis: true
  },
  {
    title: '所属店铺',
    key: 'shopName',
    width: 120
  },
  {
    title: '图片链接',
    key: 'imageLink',
    ellipsis: true
  },
  {
    title: '预览',
    key: 'preview',
    width: 70,
    align: 'center'
  },
  {
    title: '创建时间',
    key: 'createdAt',
    width: 160
  },
  {
    title: '操作',
    key: 'action',
    width: 140,
    fixed: 'right'
  }
]

// 响应式数据
const loading = ref(false)
const tableData = ref<ImageLink[]>([])
const shopList = ref<Shop[]>([])
const showAddDialog = ref(false)
const showBatchImportDialog = ref(false)
const showPreviewDialog = ref(false)
const imageLinkFormRef = ref<FormInstance>()
const previewImageUrl = ref('')
const previewImageName = ref('')

// Excel导入相关
const uploadFileList = ref<UploadProps['fileList']>([])
const importLoading = ref(false)
const importResult = ref<ImageLinkImportResult | null>(null)
const showErrorDetails = ref(false)

const searchForm = reactive({
  imageName: '',
  shopId: undefined as number | undefined,
  imageLink: ''
})

const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const editingImageLink = reactive<ImageLink>({
  imageName: '',
  shopId: 0,
  imageLink: ''
})

// 表单验证规则
const imageLinkRules = {
  imageName: [
    { required: true, message: '请输入图片名称', trigger: 'blur' }
  ],
  shopId: [
    { required: true, message: '请选择店铺', trigger: 'change' }
  ],
  imageLink: [
    { required: true, message: '请输入图片链接', trigger: 'blur' },
    { type: 'url', message: '请输入有效的URL', trigger: 'blur' }
  ]
}

// 方法
const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      imageName: searchForm.imageName,
      shopId: searchForm.shopId,
      imageLink: searchForm.imageLink
    }
    const result = await getImageLinkPage(params)
    tableData.value = result.records
    pagination.total = result.total
  } catch (error) {
    console.error('获取图片链接数据失败:', error)
  } finally {
    loading.value = false
  }
}

const loadShopList = async () => {
  try {
    shopList.value = await getShopList()
  } catch (error) {
    console.error('获取店铺列表失败:', error)
  }
}

const handleSearch = () => {
  pagination.current = 1
  fetchData()
}

const handleReset = () => {
  searchForm.imageName = ''
  searchForm.shopId = undefined
  searchForm.imageLink = ''
  pagination.current = 1
  fetchData()
}

const handleEdit = (row: ImageLink) => {
  Object.assign(editingImageLink, row)
  showAddDialog.value = true
}

const handleSaveImageLink = async () => {
  if (!imageLinkFormRef.value) return
  
  try {
    await imageLinkFormRef.value.validate()
    
    if (editingImageLink.id) {
      await updateImageLink(editingImageLink.id, editingImageLink)
      message.success('更新图片链接成功')
    } else {
      await addImageLink(editingImageLink)
      message.success('新增图片链接成功')
    }
    
    showAddDialog.value = false
    resetEditingImageLink()
    fetchData()
  } catch (error) {
    console.error('保存图片链接失败:', error)
  }
}

const resetEditingImageLink = () => {
  Object.assign(editingImageLink, {
    id: undefined,
    imageName: '',
    shopId: 0,
    imageLink: ''
  })
}

const handleDelete = async (row: ImageLink) => {
  try {
    await Modal.confirm({
      title: '提示',
      content: '确定删除此图片链接吗？',
      okText: '确定',
      cancelText: '取消'
    })
    await deleteImageLink(row.id!)
    message.success('删除成功')
    fetchData()
  } catch (error: any) {
    if (error !== 'cancel') {
      message.error('删除失败')
    }
  }
}

const handleBatchImport = async () => {
  // 此方法已废弃，改用handleExcelImport
}

// Excel导入相关方法
const handleDownloadTemplate = () => {
  try {
    downloadImportTemplate()
    message.success('模板下载成功')
  } catch (error) {
    message.error('模板下载失败')
  }
}

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  // 验证文件类型
  const isExcel = file.type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' ||
                  file.type === 'application/vnd.ms-excel' ||
                  file.name.endsWith('.xlsx') ||
                  file.name.endsWith('.xls')

  if (!isExcel) {
    message.error('只能上传Excel文件（.xlsx或.xls）')
    return false
  }

  // 验证文件大小（限制为10MB）
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    message.error('文件大小不能超过10MB')
    return false
  }

  uploadFileList.value = [file as any]
  return false // 阻止自动上传
}

const handleRemoveFile = () => {
  uploadFileList.value = []
}

const handleExcelImport = async () => {
  if (uploadFileList.value?.length === 0) {
    message.warning('请先选择要上传的Excel文件')
    return
  }

  const file = uploadFileList.value![0] as any
  importLoading.value = true

  try {
    const result = await importImageLinksFromExcel(file)
    importResult.value = result

    if (result.failureCount === 0) {
      message.success(`成功导入 ${result.successCount} 条数据`)
    } else {
      message.warning(`导入完成：成功 ${result.successCount} 条，失败 ${result.failureCount} 条`)
    }

    // 刷新列表
    fetchData()
  } catch (error: any) {
    console.error('导入失败:', error)
    message.error(error.message || '导入失败，请检查文件格式')
  } finally {
    importLoading.value = false
  }
}

const handleImportCancel = () => {
  showBatchImportDialog.value = false
  uploadFileList.value = []
  importResult.value = null
  showErrorDetails.value = false
}

const handleImportComplete = () => {
  handleImportCancel()
  fetchData()
}

const exportData = () => {
  if (tableData.value.length === 0) {
    message.warning('没有数据可导出')
    return
  }

  const headers = ['图片名称', '店铺名称', '图片链接', '创建时间']
  const csvContent = [
    headers.join(','),
    ...tableData.value.map(item => [
      item.imageName,
      getShopName(item.shopId),
      item.imageLink,
      formatDateTime(item.createdAt)
    ].join(','))
  ].join('\n')

  const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `图片链接_${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
}

const previewImage = (row: ImageLink) => {
  previewImageUrl.value = row.imageLink
  previewImageName.value = row.imageName
  showPreviewDialog.value = true
}

const onImageLoad = () => {
  // 图片加载成功
}

const onImageError = () => {
  message.error('图片加载失败')
}

const getShopName = (shopId: number) => {
  const shop = shopList.value.find(item => item.id === shopId)
  return shop ? shop.shopName : '-'
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

// 生命周期
onMounted(() => {
  fetchData()
  loadShopList()
})
</script>

<style scoped>
.image-management {
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

.search-actions {
  margin-top: 16px;
  text-align: right;
}

.search-actions .ant-btn {
  margin-left: 8px;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.import-section {
  padding: 16px 0;
}

.import-example {
  margin-top: 16px;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.import-example h5 {
  margin: 0 0 8px 0;
  color: #606266;
}

.import-example pre {
  margin: 0;
  font-size: 12px;
  color: #303133;
  background: none;
}

.image-preview {
  text-align: center;
}

.image-info {
  margin-top: 16px;
  text-align: left;
}

.image-info p {
  margin: 8px 0;
  word-break: break-all;
}

.no-image {
  text-align: center;
  padding: 40px;
}

.import-result {
  margin-top: 24px;
}

.error-details {
  margin-top: 16px;
  padding: 12px;
  background-color: #fff1f0;
  border: 1px solid #ffccc7;
  border-radius: 4px;
}

.error-details h5 {
  margin: 0 0 12px 0;
  color: #cf1322;
}

.image-link-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.image-link-text :deep(.ant-typography-copy) {
  margin-left: 4px;
}
</style>