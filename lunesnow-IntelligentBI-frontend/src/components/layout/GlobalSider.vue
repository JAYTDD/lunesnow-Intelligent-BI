<template>
  <div class="global-sider">
    <!-- 品牌区 -->
    <div class="brand">
      <div class="brand-logo">
        <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
          <rect width="28" height="28" rx="8" fill="#18181b"/>
          <path d="M8 18V12M12 18V10M16 18V14M20 18V8" stroke="#22c55e" stroke-width="2.5" stroke-linecap="round"/>
        </svg>
      </div>
      <div class="brand-text">
        <span class="brand-name">Intelligent BI</span>
        <span class="brand-sub">
          <span class="ws-status" :class="{ 'ws-online': connected }"></span>
          {{ connected ? '实时连接中' : '离线' }}
        </span>
      </div>
    </div>

    <!-- 菜单 -->
    <el-menu v-if="loginUserStore.loginUser?.id" :default-active="current" @select="doMenuClick">
      <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
        <el-icon v-if="item.icon">
          <component :is="item.icon" />
        </el-icon>
        <span>{{ item.label }}</span>
      </el-menu-item>
    </el-menu>

    <!-- 用户信息 -->
    <div class="user-section">
      <div class="user-info" @click="goToProfile">
        <div class="user-avatar">
          <img
            v-if="loginUserStore.loginUser.userAvatar"
            :src="loginUserStore.loginUser.userAvatar"
            class="avatar-img"
            alt="头像"
          />
          <span v-else>{{ avatarText }}</span>
        </div>
        <div class="user-detail">
          <span class="user-name">{{ loginUserStore.loginUser.userName || '未登录' }}</span>
          <span class="user-role">{{ loginUserStore.loginUser.userRole === 'admin' ? '管理员' : '用户' }}</span>
        </div>
      </div>
      <button class="logout-btn" @click="handleLogout" title="退出登录">
        <el-icon :size="16"><SwitchButton /></el-icon>
      </button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { userLogout } from '@/api/userController'
import type { Component } from 'vue'
import { HomeFilled, PieChart, User, SwitchButton, DataBoard } from '@element-plus/icons-vue'

interface MenuItem {
  path: string
  label: string
  icon: Component
  adminOnly?: boolean
}

defineProps<{
  connected?: boolean
}>()

const loginUserStore = useLoginUserStore()
const router = useRouter()
const route = useRoute()

// 头像文字
const avatarText = computed(() => {
  const name = loginUserStore.loginUser.userName || ''
  return name.charAt(0).toUpperCase() || '?'
})

// 菜单项定义
const allItems: MenuItem[] = [
  { path: '/', label: '主页', icon: HomeFilled },
  { path: '/add/chart', label: '添加图表', icon: PieChart },
  { path: '/my/charts', label: '我的图表', icon: PieChart },
  { path: '/dashboard/editor', label: '仪表盘', icon: DataBoard },
  { path: '/admin/userManage', label: '用户管理', icon: User, adminOnly: true },
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
  if (route.path === path) return
  router.push(path).catch(() => {})
}

// 跳转个人中心
const goToProfile = () => {
  if (route.path === '/profile') return
  router.push('/profile').catch(() => {})
}

// 退出登录
const handleLogout = async () => {
  try {
    await userLogout()
    loginUserStore.setLoginUser({ userName: '未登录' })
    ElMessage.success('已退出登录')
    router.push('/user/login')
  } catch {
    ElMessage.error('退出失败')
  }
}
</script>

<style lang="scss" scoped>
.global-sider {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px 12px;
}

/* 品牌区 */
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 8px;
  margin-bottom: 24px;
}

.brand-logo {
  flex-shrink: 0;
}

.brand-text {
  display: flex;
  flex-direction: column;
}

.brand-name {
  font-size: 15px;
  font-weight: 700;
  color: #18181b;
  letter-spacing: -0.3px;
}

.brand-sub {
  font-size: 11px;
  color: #a1a1aa;
  margin-top: 1px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.ws-status {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #d1d5db;
  transition: background 0.3s;

  &.ws-online {
    background: #22c55e;
    animation: pulse-ws 2s ease-in-out infinite;
  }
}

@keyframes pulse-ws {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* 菜单 */
:deep(.el-menu) {
  border-right: none;
  flex: 1;
}

:deep(.el-menu-item) {
  font-size: 13px;
  font-weight: 500;
  color: #71717a;
  height: 42px;
  line-height: 42px;
  margin: 2px 0;
  border-radius: 10px;
  transition: all 0.2s;

  &:hover {
    color: #18181b;
    background: #f4f4f5;
  }

  &.is-active {
    color: #18181b;
    background: #f4f4f5;
    font-weight: 600;
  }

  .el-icon {
    font-size: 17px;
  }
}

/* 用户区 */
.user-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 8px;
  margin-top: auto;
  border-top: 1px solid #e4e4e7;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  cursor: pointer;
  padding: 6px 8px;
  border-radius: 10px;
  transition: background 0.2s;

  &:hover {
    background: #f4f4f5;
  }
}

.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: #18181b;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
  overflow: hidden;

  .avatar-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.user-detail {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: #18181b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  font-size: 11px;
  color: #a1a1aa;
}

.logout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 8px;
  color: #a1a1aa;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;

  &:hover {
    background: #f4f4f5;
    color: #18181b;
  }
}
</style>
