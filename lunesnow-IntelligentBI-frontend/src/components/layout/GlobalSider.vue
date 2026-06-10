<template>
  <div class="global-sider">
    <el-menu v-if="loginUserStore.loginUser?.id" :default-active="current" @select="doMenuClick">
      <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
        <el-icon v-if="item.icon">
          <component :is="item.icon" />
        </el-icon>
        <span>{{ item.label }}</span>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import type { Component } from 'vue'
import { HomeFilled, PieChart, User } from '@element-plus/icons-vue'

interface MenuItem {
  path: string
  label: string
  icon: Component
  adminOnly?: boolean
}

const loginUserStore = useLoginUserStore()
const router = useRouter()
const route = useRoute()

// 菜单项定义
const allItems: MenuItem[] = [
  { path: '/', label: '主页', icon: HomeFilled },
  { path: '/add/chart', label: '添加图表', icon: PieChart },
  { path: '/admin/userManage', label: '用户管理', icon: User, adminOnly: true },
  { path: '/my/charts', label: '我的图表', icon: PieChart },
]

// 按权限过滤
const menuItems = computed(() =>
  allItems.filter((item) => {
    if (item.adminOnly) {
      return loginUserStore.loginUser?.userRole === 'admin'
    }
    return true
  }),
)

// 当前激活菜单
const current = computed(() => route.path)

const doMenuClick = (path: string) => {
  router.push(path)
}
</script>

<style lang="scss" scoped>
.global-sider {
  padding: 12px 0;
  min-height: 100%;
}

.global-sider :deep(.el-menu) {
  border-right: none;
}

.global-sider :deep(.el-menu-item) {
  font-size: 14px;
  font-weight: 500;
  color: #5f6368;
  margin: 2px 8px;
  border-radius: 8px;
  transition: all 0.2s;

  &:hover {
    color: #111827;
    background: #f0f2f5;
  }

  &.is-active {
    color: #409eff;
    background: #ecf5ff;
  }

  .el-icon {
    font-size: 18px;
  }
}
</style>
