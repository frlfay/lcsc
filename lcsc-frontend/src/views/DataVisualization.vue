<template>
  <div class="data-visualization">
    <a-row class="page-header">
      <a-col :span="24">
        <h1>数据可视化分析</h1>
        <p class="page-desc">实时监控爬虫性能，分析数据趋势和系统健康状态</p>
      </a-col>
    </a-row>
    
    <!-- 引入增强数据可视化组件 -->
    <EnhancedDataVisualization />
  </div>
</template>

<script setup lang="ts">
import EnhancedDataVisualization from '@/components/EnhancedDataVisualization.vue'
</script>

<style scoped>
.data-visualization {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

.page-header {
  margin-bottom: 20px;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.page-header h1 {
  margin: 0 0 8px 0;
  color: #303133;
  font-size: 24px;
  font-weight: 600;
}

.page-desc {
  margin: 0;
  color: #606266;
  font-size: 14px;
  line-height: 1.5;
}
</style>