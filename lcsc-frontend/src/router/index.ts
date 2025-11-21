import { createRouter, createWebHistory } from 'vue-router'
import DashboardV3 from '@/views/DashboardV3.vue'
import ProductManagement from '@/views/ProductManagement.vue'
import ShopManagement from '@/views/ShopManagement.vue'
import CategoryManagement from '@/views/CategoryManagement.vue'
import ImageManagement from '@/views/ImageManagement.vue'
import DataVisualization from '@/views/DataVisualization.vue'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: DashboardV3
  },
  {
    path: '/products',
    name: 'ProductManagement',
    component: ProductManagement
  },
  {
    path: '/shops',
    name: 'ShopManagement',
    component: ShopManagement
  },
  {
    path: '/categories',
    name: 'CategoryManagement',
    component: CategoryManagement
  },
  {
    path: '/images',
    name: 'ImageManagement',
    component: ImageManagement
  },
  {
    path: '/product-resources',
    name: 'ProductResourceManagement',
    component: () => import('@/views/ProductResourceManagement.vue')
  },
  {
    path: '/visualization',
    name: 'DataVisualization',
    component: DataVisualization
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
