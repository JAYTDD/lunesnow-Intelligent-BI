import { createRouter, createWebHistory } from 'vue-router'
import BasicLayout from '@/layouts/BasicLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // ==================== 布局内路由（需要登录） ====================
    {
      path: '/',
      component: BasicLayout,
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/views/HomePage.vue'),
        },
        {
          path: 'add/chart',
          name: 'addChart',
          component: () => import('@/views/AddChartPage.vue'),
        },
        {
          path: 'my/charts',
          name: 'myCharts',
          component: () => import('@/views/MyChartsPage.vue'),
        },
        {
          path: 'chart/detail/:id',
          name: 'chartDetail',
          component: () => import('@/views/ChartDetailPage.vue'),
        },
        {
          path: 'admin/userManage',
          name: 'userManage',
          component: () => import('@/views/admin/UserManagePage.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'admin/userCharts/:userId',
          name: 'userCharts',
          component: () => import('@/views/admin/UserChartsPage.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'admin/rateLimit',
          name: 'rateLimit',
          component: () => import('@/views/admin/RateLimitPage.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/user/ProfilePage.vue'),
        },
        {
          path: 'dashboard/editor',
          name: 'dashboardEditor',
          component: () => import('@/views/DashboardEditor.vue'),
        },
      ],
    },

    // ==================== 独立页面（无需登录） ====================
    {
      path: '/user/login',
      name: 'login',
      component: () => import('@/views/user/LoginPage.vue'),
    },
    {
      path: '/user/register',
      name: 'register',
      component: () => import('@/views/user/RegisterPage.vue'),
    },
    {
      path: '/403',
      name: 'forbidden',
      component: () => import('@/views/Error403Page.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'notFound',
      component: () => import('@/views/NotFoundPage.vue'),
    },
  ],
})

// 路由切换后滚动到顶部
router.afterEach(() => {
  window.scrollTo(0, 0)
})

export default router
