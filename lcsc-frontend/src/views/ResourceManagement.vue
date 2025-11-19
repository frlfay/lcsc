<template>
  <div class="resource-management">
    <!-- 搜索区域 -->
    <a-card class="mb-4">
      <a-form layout="inline" :model="searchForm">
        <a-form-item label="资源名称">
          <a-input v-model:value="searchForm.resourceName" placeholder="输入资源名称" style="width: 200px" />
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
        <a-form-item label="资源类型">
          <a-select
            v-model:value="searchForm.resourceType"
            placeholder="选择资源类型"
            style="width: 150px"
            allow-clear
          >
            <a-select-option value="image">图片</a-select-option>
            <a-select-option value="pdf">PDF</a-select-option>
            <a-select-option value="document">文档</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="资源链接">
          <a-input v-model:value="searchForm.resourceLink" placeholder="输入资源链接" style="width: 200px" />
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
            <a-button type="primary" @click="showAddDialog = true; resetEditingResourceLink()">
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

    <!-- 资源链接列表 -->
    <a-card>
      <a-table
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="false"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'shopName'">
            {{ getShopName(record.shopId) }}
          </template>
          <template v-else-if="column.key === 'resourceLink'">
            <a-typography-text copyable :ellipsis="{ tooltip: record.resourceLink }" style="max-width: 200px">
              {{ record.resourceLink }}
            </a-typography-text>
          </template>
          <template v-else-if="column.key === 'resourceType'">
            <a-tag :color="getResourceTypeColor(record.resourceType)">
              {{ getResourceTypeLabel(record.resourceType) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'preview'">
            <a-button type="link" size="small" @click="previewResource(record)">
              <template #icon><EyeOutlined /></template>
              预览
            </a-button>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatDateTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button size="small" @click="handleEdit(record)">
                <template #icon><EditOutlined /></template>
                编辑
              </a-button>
              <a-button size="small" danger @click="handleDelete(record)">
                <template #icon><DeleteOutlined /></template>
                删除
              </a-button>
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

    <!-- 新增/编辑资源链接对话框 -->
    <a-modal
      v-model:open="showAddDialog"
      :title="editingResourceLink.id ? '编辑资源链接' : '新增资源链接'"
      width="700px"
      @ok="handleSaveResourceLink"
    >
      <a-form
        ref="resourceLinkFormRef"
        :model="editingResourceLink"
        :rules="resourceLinkRules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 18 }"
      >
        <a-form-item label="资源名称" name="resourceName">
          <a-input v-model:value="editingResourceLink.resourceName" placeholder="如：C123456_001.jpg或C123456_datasheet.pdf" />
          <div class="form-tip">建议格式：产品编号_描述.扩展名</div>
        </a-form-item>
        <a-form-item label="所属店铺" name="shopId">
          <a-select
            v-model:value="editingResourceLink.shopId"
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
        <a-form-item label="资源类型" name="resourceType">
          <a-select
            v-model:value="editingResourceLink.resourceType"
            placeholder="选择资源类型"
            style="width: 100%"
            @change="handleResourceTypeChange"
          >
            <a-select-option value="image">图片</a-select-option>
            <a-select-option value="pdf">PDF文档</a-select-option>
            <a-select-option value="document">其他文档</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="文件扩展名" name="fileExtension" v-if="editingResourceLink.resourceType !== 'image'">
          <a-input v-model:value="editingResourceLink.fileExtension" placeholder="如：pdf, doc, xlsx" />
          <div class="form-tip">不含点号，如：pdf</div>
        </a-form-item>
        <a-form-item label="资源链接" name="resourceLink">
          <a-textarea
            v-model:value="editingResourceLink.resourceLink"
            placeholder="输入完整的资源链接URL"
            :rows="3"
          />
          <div class="form-tip">请输入完整的HTTP/HTTPS链接</div>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 批量导入对话框 -->
    <a-modal
      v-model:open="showBatchImportDialog"
      title="批量导入资源链接"
      width="800px"
      @ok="handleBatchImport"
    >
      <div class="import-section">
        <h4>导入说明</h4>
        <a-alert
          message="CSV格式要求：资源名称,店铺ID,资源类型,资源链接,文件扩展名"
          type="info"
          :closable="false"
          style="margin-bottom: 16px"
        />

        <a-form-item label="选择店铺">
          <a-select
            v-model:value="batchImportShopId"
            placeholder="选择目标店铺"
            style="width: 300px"
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

        <a-form-item label="导入数据">
          <a-textarea
            v-model:value="batchImportData"
            :rows="8"
            placeholder="请粘贴CSV数据，格式：资源名称,店铺ID,资源类型,资源链接,文件扩展名"
          />
        </a-form-item>

        <div class="import-example">
          <h5>示例数据：</h5>
          <pre>C123456_001.jpg,1,image,https://example.com/images/C123456_001.jpg,
C123456_datasheet.pdf,1,pdf,https://example.com/docs/C123456_datasheet.pdf,pdf
C789012_manual.doc,2,document,https://example.com/docs/C789012_manual.doc,doc</pre>
        </div>
      </div>
    </a-modal>

    <!-- 资源预览对话框 -->
    <a-modal
      v-model:open="showPreviewDialog"
      title="资源预览"
      width="800px"
      :footer="null"
    >
      <div class="resource-preview-container" v-if="previewResourceUrl">
        <div v-if="previewResourceType === 'image'" class="image-preview-large">
          <img
            :src="previewResourceUrl"
            :alt="previewResourceName"
            @load="onResourceLoad"
            @error="onResourceError"
            style="max-width: 100%; max-height: 500px; display: block; margin: 0 auto;"
          />
        </div>
        <div v-else class="document-preview">
          <div class="document-icon">
            <FileOutlined style="font-size: 64px; color: #ff4d4f;" />
          </div>
          <div class="document-actions">
            <a-button type="primary" @click="openResourceInNewTab">
              <template #icon><EyeOutlined /></template>
              在新窗口中打开
            </a-button>
            <a-button @click="downloadResource" style="margin-left: 8px;">
              <template #icon><DownloadOutlined /></template>
              下载
            </a-button>
          </div>
        </div>
        <div class="resource-info-detail">
          <p><strong>资源名称：</strong>{{ previewResourceName }}</p>
          <p><strong>资源类型：</strong>{{ getResourceTypeLabel(previewResourceType) }}</p>
          <p><strong>资源链接：</strong>{{ previewResourceUrl }}</p>
        </div>
      </div>
      <div v-else class="no-resource">
        <a-empty description="资源加载失败" />
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
  EyeOutlined,
  FileOutlined
} from '@ant-design/icons-vue'
import {
  getImageLinkPage,
  addImageLink,
  updateImageLink,
  deleteImageLink,
  saveOrUpdateImageLinkBatch
} from '@/api/imageLink'
import { getShopList } from '@/api/shop'
import type { ResourceLink, Shop } from '@/types'
import type { FormInstance } from 'ant-design-vue'

// 表格列定义
const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
    key: 'id',
    width: 80
  },
  {
    title: '资源名称',
    dataIndex: 'resourceName',
    key: 'resourceName',
    width: 200
  },
  {
    title: '所属店铺',
    key: 'shopName',
    width: 150
  },
  {
    title: '资源类型',
    key: 'resourceType',
    width: 100
  },
  {
    title: '资源链接',
    key: 'resourceLink',
    width: 250
  },
  {
    title: '预览',
    key: 'preview',
    width: 80
  },
  {
    title: '创建时间',
    key: 'createdAt',
    width: 180
  },
  {
    title: '操作',
    key: 'action',
    width: 150,
    fixed: 'right'
  }
]

// 响应式数据
const loading = ref(false)
const tableData = ref<ResourceLink[]>([])
const shopList = ref<Shop[]>([])
const showAddDialog = ref(false)
const showBatchImportDialog = ref(false)
const showPreviewDialog = ref(false)
const resourceLinkFormRef = ref<FormInstance>()
const previewResourceUrl = ref('')
const previewResourceName = ref('')
const previewResourceType = ref('')
const batchImportShopId = ref<number | undefined>()
const batchImportData = ref('')

const searchForm = reactive({
  resourceName: '',
  shopId: undefined as number | undefined,
  resourceLink: '',
  resourceType: undefined as string | undefined
})

const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const editingResourceLink = reactive<ResourceLink>({
  resourceName: '',
  shopId: 0,
  resourceLink: '',
  resourceType: 'image',
  fileExtension: ''
})

// 表单验证规则
const resourceLinkRules = {
  resourceName: [
    { required: true, message: '请输入资源名称', trigger: 'blur' }
  ],
  shopId: [
    { required: true, message: '请选择店铺', trigger: 'change' }
  ],
  resourceType: [
    { required: true, message: '请选择资源类型', trigger: 'change' }
  ],
  resourceLink: [
    { required: true, message: '请输入资源链接', trigger: 'blur' },
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
      imageName: searchForm.resourceName, // 暂时使用现有API
      shopId: searchForm.shopId,
      imageLink: searchForm.resourceLink
    }
    const result = await getImageLinkPage(params)
    // 转换数据格式以适配新的ResourceLink类型
    tableData.value = result.records.map(item => ({
      id: item.id,
      resourceName: item.imageName,
      shopId: item.shopId,
      resourceLink: item.imageLink,
      resourceType: 'image' as const,
      fileExtension: item.imageName?.split('.').pop() || '',
      createdAt: item.createdAt,
      updatedAt: item.updatedAt
    }))
    pagination.total = result.total
  } catch (error) {
    console.error('获取资源链接数据失败:', error)
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
  searchForm.resourceName = ''
  searchForm.shopId = undefined
  searchForm.resourceLink = ''
  searchForm.resourceType = undefined
  pagination.current = 1
  fetchData()
}

const handleEdit = (row: ResourceLink) => {
  Object.assign(editingResourceLink, row)
  showAddDialog.value = true
}

const handleResourceTypeChange = (value: string) => {
  if (value === 'image') {
    editingResourceLink.fileExtension = ''
  }
}

const handleSaveResourceLink = async () => {
  if (!resourceLinkFormRef.value) return

  try {
    await resourceLinkFormRef.value.validate()

    // 转换数据格式以适配现有API
    const imageData = {
      id: editingResourceLink.id,
      imageName: editingResourceLink.resourceName,
      shopId: editingResourceLink.shopId,
      imageLink: editingResourceLink.resourceLink
    }

    if (editingResourceLink.id) {
      await updateImageLink(editingResourceLink.id, imageData)
      message.success('更新资源链接成功')
    } else {
      await addImageLink(imageData)
      message.success('新增资源链接成功')
    }

    showAddDialog.value = false
    resetEditingResourceLink()
    fetchData()
  } catch (error) {
    console.error('保存资源链接失败:', error)
  }
}

const resetEditingResourceLink = () => {
  Object.assign(editingResourceLink, {
    id: undefined,
    resourceName: '',
    shopId: 0,
    resourceLink: '',
    resourceType: 'image',
    fileExtension: ''
  })
}

const handleDelete = async (row: ResourceLink) => {
  try {
    await Modal.confirm({
      title: '提示',
      content: '确定删除此资源链接吗？',
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
  if (!batchImportData.value.trim()) {
    message.warning('请输入导入数据')
    return
  }

  try {
    const lines = batchImportData.value.trim().split('\n')
    const imageLinks: any[] = []

    for (const line of lines) {
      const parts = line.split(',')
      if (parts.length >= 4) {
        const resourceName = parts[0].trim()
        const shopId = batchImportShopId.value || parseInt(parts[1].trim())
        const resourceType = parts[2].trim()
        const resourceLink = parts[3].trim()

        // 暂时只导入图片类型，以适配现有API
        if (resourceType === 'image') {
          imageLinks.push({
            imageName: resourceName,
            shopId,
            imageLink: resourceLink
          })
        }
      }
    }

    if (imageLinks.length === 0) {
      message.warning('没有有效的图片数据行')
      return
    }

    await saveOrUpdateImageLinkBatch(imageLinks)
    message.success(`成功导入 ${imageLinks.length} 条资源链接`)

    showBatchImportDialog.value = false
    batchImportData.value = ''
    batchImportShopId.value = undefined
    fetchData()
  } catch (error) {
    message.error('批量导入失败')
  }
}

const exportData = () => {
  if (tableData.value.length === 0) {
    message.warning('没有数据可导出')
    return
  }

  const headers = ['资源名称', '店铺名称', '资源类型', '资源链接', '创建时间']
  const csvContent = [
    headers.join(','),
    ...tableData.value.map(item => [
      item.resourceName,
      getShopName(item.shopId),
      getResourceTypeLabel(item.resourceType),
      item.resourceLink,
      formatDateTime(item.createdAt)
    ].join(','))
  ].join('\n')

  const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `资源链接_${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
}

const previewResource = (row: ResourceLink) => {
  previewResourceUrl.value = row.resourceLink
  previewResourceName.value = row.resourceName
  previewResourceType.value = row.resourceType
  showPreviewDialog.value = true
}

const openResourceInNewTab = () => {
  window.open(previewResourceUrl.value, '_blank')
}

const downloadResource = () => {
  const link = document.createElement('a')
  link.href = previewResourceUrl.value
  link.download = previewResourceName.value
  link.click()
}

const onResourceLoad = () => {
  // 资源加载成功
}

const onResourceError = () => {
  message.error('资源加载失败')
}

const getShopName = (shopId: number) => {
  const shop = shopList.value.find(item => item.id === shopId)
  return shop ? shop.shopName : '-'
}

const getResourceTypeLabel = (type: string) => {
  const typeMap: Record<string, string> = {
    image: '图片',
    pdf: 'PDF',
    document: '文档'
  }
  return typeMap[type] || type
}

const getResourceTypeColor = (type: string) => {
  const colorMap: Record<string, string> = {
    image: 'green',
    pdf: 'red',
    document: 'blue'
  }
  return colorMap[type] || 'default'
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
.resource-management {
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

.resource-preview-container {
  text-align: center;
}

.image-preview-large {
  margin-bottom: 16px;
}

.document-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 0;
}

.document-icon {
  margin-bottom: 16px;
}

.document-actions {
  margin-bottom: 16px;
}

.resource-info-detail {
  margin-top: 16px;
  text-align: left;
  padding: 16px;
  background-color: #f5f5f5;
  border-radius: 4px;
}

.resource-info-detail p {
  margin: 8px 0;
  word-break: break-all;
}

.no-resource {
  text-align: center;
  padding: 40px;
}
</style>