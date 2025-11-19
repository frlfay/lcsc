<template>
  <div class="category-tree-selector">
    <!-- 搜索框 -->
    <a-input-search
      v-model:value="searchKeyword"
      placeholder="搜索分类名称..."
      class="mb-3"
      allow-clear
      style="margin-bottom: 16px;"
    />

    <!-- 操作按钮 -->
    <a-space class="mb-3" style="margin-bottom: 12px;">
      <a-button size="small" @click="handleSelectAll">
        <template #icon><CheckSquareOutlined /></template>
        全选
      </a-button>
      <a-button size="small" @click="handleClearAll">
        <template #icon><MinusSquareOutlined /></template>
        清除
      </a-button>
      <a-button size="small" @click="handleExpandAll">
        <template #icon><ExpandOutlined /></template>
        展开全部
      </a-button>
      <a-button size="small" @click="handleCollapseAll">
        <template #icon><CompressOutlined /></template>
        折叠全部
      </a-button>
      <a-divider type="vertical" />
      <span class="text-muted">已选: {{ selectedCount }}/{{ allCategoriesCount }}</span>
    </a-space>

    <!-- 树形结构 -->
    <div style="border: 1px solid #d9d9d9; border-radius: 4px; padding: 8px; max-height: 500px; overflow-y: auto;">
      <a-tree
        v-if="treeData.length > 0"
        :tree-data="filterTreeData(treeData)"
        :checked-keys="checkedKeys"
        :expanded-keys="expandedKeys"
        checkable
        block-node
        @update:expanded-keys="handleExpandedKeysChange"
        @update:checked-keys="handleCheckedKeysChange"
      >
        <template #title="nodeData">
          <span class="tree-title">
            <span v-if="nodeData.isLevel1" class="level1-badge">L1</span>
            <span v-else class="level2-badge">L2</span>
            <span class="title-text">{{ nodeData.title }}</span>
            <span v-if="nodeData.productCount !== undefined" class="product-count">
              {{ nodeData.productCount }} 件
            </span>
          </span>
        </template>
      </a-tree>
      <a-empty v-else description="无分类数据" />
    </div>

    <!-- 统计信息 -->
    <a-divider />
    <a-row :gutter="16">
      <a-col :span="12">
        <a-statistic
          title="已选择分类"
          :value="selectedCount"
          :value-style="{ color: '#1890ff' }"
        />
      </a-col>
      <a-col :span="12">
        <a-statistic
          title="总分类数"
          :value="allCategoriesCount"
          :value-style="{ color: '#52c41a' }"
        />
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import {
  CheckSquareOutlined,
  MinusSquareOutlined,
  ExpandOutlined,
  CompressOutlined
} from '@ant-design/icons-vue'
import type { CategoryLevel2Code } from '@/types'

interface TreeNode {
  key: string
  title: string
  isLevel1: boolean
  level1Id?: number
  level2Id?: number
  productCount?: number
  children?: TreeNode[]
}

interface Props {
  categories: Array<{
    id: number
    categoryLevel1Id: number
    categoryLevel1Name?: string
    categoryLevel2Name: string
    totalProducts?: number
    crawlStatus?: string
  }>
  selectedCategoryIds?: number[]
}

interface Emits {
  (e: 'update:selected', ids: number[]): void
}

const props = withDefaults(defineProps<Props>(), {
  selectedCategoryIds: () => []
})

const emit = defineEmits<Emits>()

const searchKeyword = ref('')
const expandedKeys = ref<string[]>([])
const checkedKeys = ref<string[]>([])
const treeData = ref<TreeNode[]>([])

// 计算选中的分类数
const selectedCount = computed(() => checkedKeys.value.length)

// 计算所有二级分类数
const allCategoriesCount = computed(() => {
  return props.categories.length
})

// 初始化树形数据
function initializeTreeData() {
  const groupedByLevel1: Map<number, Array<any>> = new Map()
  const level1Map: Map<number, string> = new Map()

  // 分组数据
  props.categories.forEach(cat => {
    if (!groupedByLevel1.has(cat.categoryLevel1Id)) {
      groupedByLevel1.set(cat.categoryLevel1Id, [])
    }
    groupedByLevel1.get(cat.categoryLevel1Id)!.push(cat)
    level1Map.set(cat.categoryLevel1Id, cat.categoryLevel1Name || `分类${cat.categoryLevel1Id}`)
  })

  // 构建树
  const tree: TreeNode[] = Array.from(groupedByLevel1.entries())
    .sort((a, b) => a[0] - b[0])
    .map(([level1Id, level2Items]) => ({
      key: `L1-${level1Id}`,
      title: level1Map.get(level1Id) || `分类${level1Id}`,
      isLevel1: true,
      level1Id,
      children: level2Items
        .sort((a, b) => (a.id || 0) - (b.id || 0))
        .map(item => ({
          key: `L2-${item.id}`,
          title: item.categoryLevel2Name,
          isLevel1: false,
          level1Id: item.categoryLevel1Id,
          level2Id: item.id,
          productCount: item.totalProducts || 0
        }))
    }))

  treeData.value = tree
  
  // 默认展开所有一级分类
  expandedKeys.value = tree.map(t => t.key)
}

// 筛选树数据
function filterTreeData(nodes: TreeNode[]): TreeNode[] {
  if (!searchKeyword.value) return nodes

  const keyword = searchKeyword.value.toLowerCase()
  return nodes
    .map(node => ({
      ...node,
      children: node.children?.filter(child =>
        child.title.toLowerCase().includes(keyword)
      )
    }))
    .filter(node => {
      // 如果一级分类名称匹配或有子分类匹配，则保留
      return (
        node.title.toLowerCase().includes(keyword) ||
        (node.children && node.children.length > 0)
      )
    })
}

// 处理展开/折叠
function handleExpandedKeysChange(keys: string[]) {
  expandedKeys.value = keys
}

// 处理勾选变化
function handleCheckedKeysChange(keys: string[]) {
  checkedKeys.value = keys
  
  // 提取所有选中的二级分类ID
  const selectedIds = keys
    .filter(key => key.startsWith('L2-'))
    .map(key => parseInt(key.replace('L2-', '')))
  
  emit('update:selected', selectedIds)
}

// 全选（仅二级分类）
function handleSelectAll() {
  const allLevel2Keys = treeData.value
    .flatMap(node => node.children?.map(child => child.key) || [])
  
  checkedKeys.value = allLevel2Keys
  handleCheckedKeysChange(allLevel2Keys)
}

// 清除所有选择
function handleClearAll() {
  checkedKeys.value = []
  handleCheckedKeysChange([])
}

// 展开全部
function handleExpandAll() {
  const allKeys: string[] = []
  
  function collectKeys(nodes: TreeNode[]) {
    nodes.forEach(node => {
      allKeys.push(node.key)
      if (node.children) {
        collectKeys(node.children)
      }
    })
  }
  
  collectKeys(treeData.value)
  expandedKeys.value = allKeys
}

// 折叠全部
function handleCollapseAll() {
  expandedKeys.value = []
}

// 监听外部传入的选中ID
watch(
  () => props.selectedCategoryIds,
  (newIds) => {
    if (newIds && newIds.length > 0) {
      checkedKeys.value = newIds.map(id => `L2-${id}`)
    }
  },
  { immediate: false }
)

// 初始化
watch(
  () => props.categories,
  () => {
    initializeTreeData()
    // 恢复之前的选择
    if (props.selectedCategoryIds && props.selectedCategoryIds.length > 0) {
      checkedKeys.value = props.selectedCategoryIds.map(id => `L2-${id}`)
    }
  },
  { immediate: true, deep: true }
)

// 暴露方法给父组件
defineExpose({
  getSelectedIds: () => checkedKeys.value
    .filter(key => key.startsWith('L2-'))
    .map(key => parseInt(key.replace('L2-', '')))
})
</script>

<style scoped lang="scss">
.category-tree-selector {
  width: 100%;

  .tree-title {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;

    .level1-badge,
    .level2-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 20px;
      border-radius: 2px;
      font-size: 12px;
      font-weight: bold;
      color: white;
      min-width: 24px;
    }

    .level1-badge {
      background-color: #1890ff;
    }

    .level2-badge {
      background-color: #52c41a;
    }

    .title-text {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .product-count {
      color: #999;
      font-size: 12px;
      margin-left: 8px;
      min-width: 40px;
      text-align: right;
    }
  }
}

:deep(.ant-tree-treenode-checkbox-checked .ant-tree-node-content-wrapper) {
  font-weight: 500;
}

.text-muted {
  color: #8c8c8c;
  font-size: 12px;
}
</style>

