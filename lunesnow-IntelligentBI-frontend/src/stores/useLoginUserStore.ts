import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLoginUser } from '@/api/userController'

/**
 * 登录用户信息 Store
 */
export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<API.LoginUserVO>({
    userName: '未登录',
  })

  const setLoginUser = (user: API.LoginUserVO) => {
    loginUser.value = user
  }

  // 从后端获取登录用户信息
  const fetchLoginUser = async () => {
    const res = await getLoginUser()
    if (res.code === 0 && res.data) {
      loginUser.value = res.data
    }
  }

  return { loginUser, setLoginUser, fetchLoginUser }
})
