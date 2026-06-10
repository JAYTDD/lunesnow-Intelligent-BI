<template>
  <div class="global-header">
    <div class="brand" @click="router.push('/')">
      <div class="brand-title">Intelligent BI</div>
    </div>

    <el-menu
      class="menu"
      mode="horizontal"
      :default-active="current"
      :ellipsis="false"
      @select="doMenuClick"
    >
      <el-menu-item v-for="item in items" :key="item.path" :index="item.path">
        <el-icon v-if="item.icon">
          <component :is="item.icon" />
        </el-icon>
        {{ item.label }}
      </el-menu-item>
    </el-menu>

    <div class="user-area">
      <el-dropdown trigger="click">
        <span class="user-info">
          <el-avatar :src="loginUserStore.loginUser.userAvatar" size="small" />
          <span>{{ loginUserStore.loginUser.userName ?? '无名' }}</span>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item divided @click="doLogout">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { SwitchButton, HomeFilled, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { userLogout } from '@/api/userController'
import type { Component } from 'vue'

interface NavItem {
  path: string
  label: string
  icon: Component
  adminOnly?: boolean
}

const loginUserStore = useLoginUserStore()
const router = useRouter()
const route = useRoute()

// 导航菜单项
const allItems: NavItem[] = [
  { path: '/', label: '主页', icon: HomeFilled },
  { path: '/admin/userManage', label: '用户管理', icon: User, adminOnly: true },
]

// 按权限过滤
const items = computed(() =>
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

// 用户注销
const doLogout = async () => {
  const res = await userLogout()
  if (res.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    ElMessage.success('退出登录成功')
    await router.push('/user/login')
  } else {
    ElMessage.error('退出登录失败，' + res.message)
  }
}
</script>

<style lang="scss" scoped>
.global-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 24px;
}

.brand {
  flex: 0.3;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  cursor: pointer;
  user-select: none;
}

.brand-title {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  letter-spacing: -0.5px;
}

.menu {
  flex: 1;
  border-bottom: none !important;
  margin-left: 32px;
}

.menu :deep(.el-menu-item) {
  font-size: 14px;
  font-weight: 500;
  color: #5f6368;
  transition: color 0.2s;

  &:hover {
    color: #111827;
    background: transparent;
  }

  &.is-active {
    color: #409eff;
    border-bottom-color: #409eff;
  }
}

.user-area {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
}

.user-info {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: #f0f2f5;
  }

  span {
    font-size: 14px;
    color: #333;
    font-weight: 500;
  }
}

@media (max-width: 900px) {
  .global-header {
    flex-wrap: wrap;
    height: auto;
    padding: 12px 16px;
    gap: 12px;
  }

  .menu {
    order: 3;
    width: 100%;
    margin-left: 0;
  }
}
</style>
