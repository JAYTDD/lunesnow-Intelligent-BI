import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLoginUser } from '@/api/userController'

const LOGIN_USER_KEY = 'loginUser'

const getCachedUser = (): API.LoginUserVO => {
  try {
    const cached = localStorage.getItem(LOGIN_USER_KEY)
    if (cached) {
      return JSON.parse(cached)
    }
  } catch (e) {
    console.error('读取本地登录缓存失败', e)
  }
  return { userName: '未登录' }
}

// 保存登录用户信息到本地缓存
const saveCachedUser = (user: API.LoginUserVO) => {
  try {
    localStorage.setItem(LOGIN_USER_KEY, JSON.stringify(user))
  } catch (e) {
    console.error('保存登录缓存失败', e)
  }
}

const clearCachedUser = () => {
  try {
    localStorage.removeItem(LOGIN_USER_KEY)
  } catch (e) {
    console.error('清除登录缓存失败', e)
  }
}

export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<API.LoginUserVO>(getCachedUser())

  const setLoginUser = (user: API.LoginUserVO) => {
    loginUser.value = user
    saveCachedUser(user)
  }

  const clearLoginUser = () => {
    loginUser.value = { userName: '未登录' }
    clearCachedUser()
  }

  const fetchLoginUser = async () => {
    const res = await getLoginUser()
    if (res.code === 0 && res.data) {
      setLoginUser(res.data)
    } else {
      clearLoginUser()
      throw new Error('未登录')
    }
  }

  return { loginUser, setLoginUser, fetchLoginUser, clearLoginUser }
})
