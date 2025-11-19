<template>
  <div class="shop-management">
    <!-- 搜索区域 -->
    <a-card class="mb-4">
      <a-form layout="inline" :model="searchForm">
        <a-form-item label="店铺名称">
          <a-input 
            v-model:value="searchForm.shopName" 
            placeholder="输入店铺名称"
            style="width: 200px"
          />
        </a-form-item>
        <a-form-item label="运费模板ID">
          <a-input 
            v-model:value="searchForm.shippingTemplateId" 
            placeholder="输入运费模板ID"
            style="width: 200px"
          />
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
            <a-button type="primary" @click="showAddDialog = true; resetEditingShop()">
              <template #icon><PlusOutlined /></template>
              新增店铺
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 店铺列表 -->
    <a-card>
      <a-table 
        :columns="columns"
        :data-source="tableData" 
        :loading="loading"
        :pagination="false"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'createdAt'">
            {{ formatDateTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatDateTime(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button size="small" @click="handleEdit(record)">
                <template #icon><EditOutlined /></template>
                编辑
              </a-button>
              <a-button size="small" @click="viewImageLinks(record)">
                <template #icon><PictureOutlined /></template>
                图片
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

    <!-- 新增/编辑店铺对话框 -->
    <a-modal 
      v-model:open="showAddDialog" 
      :title="editingShop.id ? '编辑店铺' : '新增店铺'" 
      width="600px"
      @ok="handleSaveShop"
    >
      <a-form
        ref="shopFormRef"
        :model="editingShop"
        :rules="shopRules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 18 }"
      >
        <a-form-item label="店铺名称" name="shopName">
          <a-input v-model:value="editingShop.shopName" placeholder="输入店铺名称" />
        </a-form-item>
        <a-form-item label="运费模板ID" name="shippingTemplateId">
          <a-input v-model:value="editingShop.shippingTemplateId" placeholder="输入运费模板ID" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 店铺图片链接对话框 -->
    <a-modal 
      v-model:open="showImageDialog" 
      title="店铺图片链接" 
      width="800px"
      :footer="null"
    >
      <div v-if="selectedShop">
        <h4>{{ selectedShop.shopName }} 的图片链接</h4>
        <a-table 
          :columns="imageColumns"
          :data-source="imageLinks" 
          :pagination="false"
          row-key="id"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'imageLink'">
              <a-typography-text copyable :ellipsis="{ tooltip: record.imageLink }" style="max-width: 300px">
                {{ record.imageLink }}
              </a-typography-text>
            </template>
            <template v-else-if="column.key === 'createdAt'">
              {{ formatDateTime(record.createdAt) }}
            </template>
          </template>
        </a-table>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined, EditOutlined, DeleteOutlined, PictureOutlined } from '@ant-design/icons-vue'
import { 
  getShopPage, 
  addShop, 
  updateShop, 
  deleteShop 
} from '@/api/shop'
import { getImageLinkListByShopId } from '@/api/imageLink'
import type { Shop, ImageLink } from '@/types'
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
    title: '店铺名称',
    dataIndex: 'shopName',
    key: 'shopName',
    width: 200
  },
  {
    title: '运费模板ID',
    dataIndex: 'shippingTemplateId',
    key: 'shippingTemplateId',
    width: 150
  },
  {
    title: '创建时间',
    key: 'createdAt',
    width: 180
  },
  {
    title: '更新时间',
    key: 'updatedAt',
    width: 180
  },
  {
    title: '操作',
    key: 'action',
    width: 200,
    fixed: 'right'
  }
]

// 图片链接表格列定义
const imageColumns = [
  {
    title: '图片名称',
    dataIndex: 'imageName',
    key: 'imageName',
    width: 200
  },
  {
    title: '图片链接',
    key: 'imageLink'
  },
  {
    title: '创建时间',
    key: 'createdAt',
    width: 150
  }
]

// 响应式数据
const loading = ref(false)
const tableData = ref<Shop[]>([])
const showAddDialog = ref(false)
const showImageDialog = ref(false)
const shopFormRef = ref<FormInstance>()
const selectedShop = ref<Shop | null>(null)
const imageLinks = ref<ImageLink[]>([])

const searchForm = reactive({
  shopName: '',
  shippingTemplateId: ''
})

const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const editingShop = reactive<Shop>({
  shopName: '',
  shippingTemplateId: ''
})

// 表单验证规则
const shopRules = {
  shopName: [
    { required: true, message: '请输入店铺名称', trigger: 'blur' }
  ],
  shippingTemplateId: [
    { required: true, message: '请输入运费模板ID', trigger: 'blur' }
  ]
}

// 方法
const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      shopName: searchForm.shopName,
      shippingTemplateId: searchForm.shippingTemplateId
    }
    const result = await getShopPage(params)
    tableData.value = result.records
    pagination.total = result.total
  } catch (error) {
    console.error('获取店铺数据失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.current = 1
  fetchData()
}

const handleReset = () => {
  searchForm.shopName = ''
  searchForm.shippingTemplateId = ''
  pagination.current = 1
  fetchData()
}

const handleEdit = (row: Shop) => {
  Object.assign(editingShop, row)
  showAddDialog.value = true
}

const handleSaveShop = async () => {
  if (!shopFormRef.value) return
  
  try {
    await shopFormRef.value.validate()
    
    if (editingShop.id) {
      await updateShop(editingShop.id, editingShop)
      message.success('更新店铺成功')
    } else {
      await addShop(editingShop)
      message.success('新增店铺成功')
    }
    
    showAddDialog.value = false
    resetEditingShop()
    fetchData()
  } catch (error) {
    console.error('保存店铺失败:', error)
  }
}

const resetEditingShop = () => {
  Object.assign(editingShop, {
    id: undefined,
    shopName: '',
    shippingTemplateId: ''
  })
}

const handleDelete = async (row: Shop) => {
  try {
    await Modal.confirm({
      title: '提示',
      content: '确定删除此店铺吗？',
      okText: '确定',
      cancelText: '取消'
    })
    await deleteShop(row.id!)
    message.success('删除成功')
    fetchData()
  } catch (error: any) {
    if (error !== 'cancel') {
      message.error('删除失败')
    }
  }
}

const viewImageLinks = async (row: Shop) => {
  selectedShop.value = row
  try {
    imageLinks.value = await getImageLinkListByShopId(row.id!)
    showImageDialog.value = true
  } catch (error) {
    message.error('获取图片链接失败')
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

// 生命周期
onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.shop-management {
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
</style>