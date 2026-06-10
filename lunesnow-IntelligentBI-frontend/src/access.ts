import router from '@/router'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { ElMessage } from 'element-plus'

router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()

  // 无需登录的页面直接放行
  if (!to.matched.some((record) => record.meta.requiresAuth)) {
    next()
    return
  }

  // 首次加载 / 刷新时：尝试从后端恢复登录会话（cookie）
  if (loginUserStore.loginUser.userName === '未登录') {
    try {
      await loginUserStore.fetchLoginUser()
    } catch (e) {
      // 获取登录信息失败，继续走未登录逻辑
    }
  }

  // 尝试恢复后仍未登录 → 跳转登录页
  if (loginUserStore.loginUser.userName === '未登录') {
    next(`/user/login?redirect=${to.fullPath}`)
    return
  }

  // 管理员权限校验
  if (to.matched.some((record) => record.meta.requiresAdmin)) {
    if (loginUserStore.loginUser.userRole !== 'admin') {
      ElMessage.error('没有权限')
      next('/403')
      return
    }
  }

  next()
})
