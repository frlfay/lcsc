<template>
  <div class="resource-item" :class="{ 'pdf-item': resource.category === 'pdf' }" @click="handlePreview">
    <div class="resource-preview">
      <div v-if="resource.category === 'image'" class="image-preview">
        <img :src="fullUrl" :alt="resource.filename" @error="handleImageError" />
        <div class="preview-overlay">
          <EyeOutlined /> 预览
        </div>
      </div>
      <div v-else class="pdf-preview">
        <FilePdfOutlined style="font-size: 48px; color: #ff4d4f;" />
        <div class="pdf-label">PDF文档</div>
        <div class="preview-overlay">
          <EyeOutlined /> 预览
        </div>
      </div>
    </div>
    <div class="resource-info">
      <div class="resource-name" :title="resource.filename">{{ resource.filename }}</div>
      <div class="resource-meta">
        <span class="resource-size">{{ formatFileSize(resource.size) }}</span>
      </div>
    </div>
  </div>

  <!-- 图片预览模态框 -->
  <a-modal
    v-model:open="showImagePreview"
    :title="resource.filename"
    :footer="null"
    width="90%"
    centered
    @cancel="closeImagePreview"
  >
    <div class="image-preview-container">
      <img :src="fullUrl" :alt="resource.filename" class="preview-image" />
    </div>
  </a-modal>

  <!-- PDF预览模态框 -->
  <a-modal
    v-model:open="showPdfPreview"
    :title="resource.filename"
    :footer="null"
    width="90%"
    centered
    @cancel="closePdfPreview"
  >
    <div class="pdf-preview-container">
      <iframe
        :src="pdfViewerUrl"
        class="pdf-iframe"
        frameborder="0"
        @error="handlePdfError"
      ></iframe>
      <div v-if="pdfError" class="pdf-error">
        <p>PDF预览失败，请尝试下载文件查看</p>
        <a-button type="primary" @click="downloadFile">下载文件</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { FilePdfOutlined, EyeOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { ResourceFile } from '@/types'

const props = defineProps<{
  resource: ResourceFile
}>()

// 由于API返回的URL已经是完整的相对路径（包含/api），直接使用即可
// Vite代理会自动将/api请求转发到后端服务器
const fullUrl = computed(() => props.resource.url)

// 预览状态
const showImagePreview = ref(false)
const showPdfPreview = ref(false)
const pdfError = ref(false)

// PDF查看器URL
const pdfViewerUrl = computed(() => {
  // 使用浏览器内置PDF查看器
  return `${fullUrl.value}#view=FitH`
})

const handlePreview = () => {
  if (props.resource.category === 'image') {
    showImagePreview.value = true
  } else if (props.resource.category === 'pdf') {
    showPdfPreview.value = true
    pdfError.value = false
  } else {
    // 其他类型文件直接下载
    downloadFile()
  }
}

const closeImagePreview = () => {
  showImagePreview.value = false
}

const closePdfPreview = () => {
  showPdfPreview.value = false
  pdfError.value = false
}

const downloadFile = () => {
  const link = document.createElement('a')
  link.href = fullUrl.value
  link.download = props.resource.filename
  link.target = '_blank'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
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

const handlePdfError = () => {
  pdfError.value = true
  message.error('PDF预览失败')
}
</script>

<style scoped>
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
.resource-info {
  padding: 8px 12px;
  border-top: 1px solid #e8e8e8;
}
.resource-name {
  font-size: 12px;
  font-weight: 500;
  color: #262626;
  margin-bottom: 4px;
  word-break: break-all;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.resource-meta {
  font-size: 11px;
  color: #8c8c8c;
}

/* 预览悬停效果 */
.preview-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
  font-size: 14px;
  font-weight: 500;
  gap: 6px;
}
.resource-item:hover .preview-overlay {
  opacity: 1;
}

/* 图片预览样式 */
.image-preview-container {
  text-align: center;
  max-height: 70vh;
  overflow: auto;
}
.preview-image {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
  border-radius: 4px;
}

/* PDF预览样式 */
.pdf-preview-container {
  height: 70vh;
  position: relative;
}
.pdf-iframe {
  width: 100%;
  height: 100%;
  border: none;
  border-radius: 4px;
}
.pdf-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
  text-align: center;
}
.pdf-error p {
  margin-bottom: 16px;
  font-size: 16px;
}
</style>
