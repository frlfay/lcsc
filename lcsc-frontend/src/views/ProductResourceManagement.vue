<template>
  <div class="resource-management">
    <a-card title="📦 产品资源管理器">
      <!-- 文件夹列表 -->
      <a-table
        :dataSource="folders"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        row-key="productCode"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'productCode'">
            <a @click="showProductResources(record.productCode)">
              <FolderOutlined /> {{ record.productCode }}
            </a>
          </template>
          <template v-if="column.key === 'lastModified'">
            {{ formatDateTime(record.lastModified) }}
          </template>
          <template v-if="column.key === 'action'">
            <a-button type="primary" size="small" @click="showProductResources(record.productCode)">
              查看文件
            </a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 资源查看对话框 -->
    <a-modal
      v-model:open="showResourcesDialog"
      :title="`产品资源 - ${currentProductCode}`"
      width="1000px"
      :footer="null"
    >
      <div v-if="!resourceLoading && resources.total === 0" class="empty-resources">
        <a-empty description="暂无资源文件" />
      </div>
      <a-spin :spinning="resourceLoading">
        <div class="resources-gallery">
          <a-tabs v-model:activeKey="activeResourceTab" type="card">
            <a-tab-pane key="all" :tab="`全部 (${resources.total})`">
              <div class="resource-grid">
                <resource-card v-for="item in resources.all" :key="item.filename" :resource="item" />
              </div>
            </a-tab-pane>
            <a-tab-pane key="images" :tab="`图片 (${resources.images.length})`">
              <div class="resource-grid">
                <resource-card v-for="item in resources.images" :key="item.filename" :resource="item" />
              </div>
            </a-tab-pane>
            <a-tab-pane key="pdfs" :tab="`PDF (${resources.pdfs.length})`">
               <div class="resource-grid">
                <resource-card v-for="item in resources.pdfs" :key="item.filename" :resource="item" />
              </div>
            </a-tab-pane>
          </a-tabs>
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { FolderOutlined } from '@ant-design/icons-vue'
import { resourceApi, type ResourceFolder } from '@/api/resource'
import { getProductResources } from '@/api/product' // Reuse existing API function
import type { ProductResources, ResourceFile } from '@/types'
import ResourceCard from '@/components/ResourceCard.vue' // Assume we create a new component for display

const loading = ref(false)
const folders = ref<ResourceFolder[]>([])
const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
})

const columns = [
  { title: '产品型号', key: 'productCode', dataIndex: 'productCode' },
  { title: '图片数量', key: 'imageCount', dataIndex: 'imageCount', width: 120 },
  { title: 'PDF数量', key: 'pdfCount', dataIndex: 'pdfCount', width: 120 },
  { title: '最后更新', key: 'lastModified', dataIndex: 'lastModified', width: 200 },
  { title: '操作', key: 'action', width: 120 },
]

const showResourcesDialog = ref(false)
const resourceLoading = ref(false)
const currentProductCode = ref('')
const resources = reactive<ProductResources>({ all: [], images: [], pdfs: [], total: 0 })
const activeResourceTab = ref('all')

const fetchFolders = async () => {
  loading.value = true
  try {
    const response = await resourceApi.getFolders(pagination.current, pagination.pageSize)
    folders.value = response.records
    pagination.total = response.total
  } catch (error) {
    message.error('获取资源文件夹列表失败')
  } finally {
    loading.value = false
  }
}

const handleTableChange = (pager: any) => {
  pagination.current = pager.current
  pagination.pageSize = pager.pageSize
  fetchFolders()
}

const showProductResources = async (productCode: string) => {
  currentProductCode.value = productCode
  showResourcesDialog.value = true
  resourceLoading.value = true
  try {
    const data = await getProductResources(productCode)
    Object.assign(resources, data)
  } catch (error) {
    message.error(`获取 ${productCode} 的资源失败`)
  } finally {
    resourceLoading.value = false
  }
}

const formatDateTime = (timestamp: number) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

onMounted(() => {
  fetchFolders()
})
</script>

<style scoped>
.resource-management {
  padding: 24px;
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
.empty-resources {
  text-align: center;
  padding: 40px 0;
}
</style>