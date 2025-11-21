<template>
  <div id="app">
    <a-layout class="layout-container">
      <a-layout-header class="layout-header">
        <div class="header-content">
          <div class="logo">
            <h1>立创商城爬虫管理系统</h1>
          </div>
          <div class="header-nav">
            <a-menu
              mode="horizontal"
              :selected-keys="[currentRoute]"
              theme="dark"
              class="header-menu"
              @click="handleMenuClick"
            >
              <a-menu-item key="/dashboard">
                <template #icon>
                  <DashboardOutlined />
                </template>
                仪表盘
              </a-menu-item>
              <a-menu-item key="/products">
                <template #icon>
                  <ShoppingOutlined />
                </template>
                产品管理
              </a-menu-item>
              <a-menu-item key="/shops">
                <template #icon>
                  <ShopOutlined />
                </template>
                店铺管理
              </a-menu-item>
              <a-menu-item key="/categories">
                <template #icon>
                  <AppstoreOutlined />
                </template>
                分类管理
              </a-menu-item>
              <a-menu-item key="/images">
                <template #icon>
                  <PictureOutlined />
                </template>
                图片管理
              </a-menu-item>
              <a-menu-item key="/export">
                <template #icon>
                  <CloudDownloadOutlined />
                </template>
                高级导出
              </a-menu-item>
<!--              <a-menu-item key="/product-resources">-->
<!--                <template #icon>-->
<!--                  <FileImageOutlined />-->
<!--                </template>-->
<!--                产品资源-->
<!--              </a-menu-item>-->
            </a-menu>
          </div>

        </div>
      </a-layout-header>
      <a-layout-content class="layout-main">
        <div class="content-wrapper">
          <router-view />
        </div>
      </a-layout-content>
    </a-layout>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  DashboardOutlined,
  ShoppingOutlined,
  ShopOutlined,
  AppstoreOutlined,
  PictureOutlined,
  FolderOutlined,
  FileImageOutlined,
  CloudDownloadOutlined
} from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()

const currentRoute = computed(() => route.path)

const handleMenuClick = ({ key }: { key: string }) => {
  router.push(key)
}
</script>

<style scoped>
.layout-container {
  min-height: 100vh;
  background: #f8fafc;
}

.layout-header {
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  padding: 0;
  height: 64px;
  line-height: 64px;
  position: sticky;
  top: 0;
  z-index: 1000;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 0 32px;
}

.logo {
  flex-shrink: 0;
}

.logo h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  letter-spacing: -0.025em;
}

.header-nav {
  flex: 1;
  display: flex;
  justify-content: center;
  margin: 0 48px;
}

.header-menu {
  background: transparent !important;
  border: none !important;
  line-height: 64px;
  font-size: 14px;
}

.header-menu :deep(.ant-menu-item),
.header-menu :deep(.ant-menu-submenu) {
  color: #6b7280 !important;
  font-weight: 500;
  margin: 0 4px;
  border-radius: 6px;
  transition: all 0.2s ease;
}

.header-menu :deep(.ant-menu-item:hover),
.header-menu :deep(.ant-menu-submenu:hover) {
  color: #1f2937 !important;
  background: #f3f4f6 !important;
}

.header-menu :deep(.ant-menu-item-selected) {
  color: #3b82f6 !important;
  background: #eff6ff !important;
}

.header-status {
  flex-shrink: 0;
  padding: 8px 16px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  height: 40px;
  display: flex;
  align-items: center;
}

.layout-main {
  background: #f8fafc;
  min-height: calc(100vh - 64px);
  padding: 24px;
}

.content-wrapper {
  background: white;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  min-height: calc(100vh - 112px);
  overflow: hidden;
  margin: 0 32px;
}

:global(body) {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

:global(#app) {
  height: 100vh;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header-content {
    padding: 0 16px;
  }
  
  .logo h1 {
    font-size: 16px;
  }
  
  .header-nav {
    margin: 0 20px;
  }
  
  .content-wrapper {
    padding: 16px;
  }
}
</style>
