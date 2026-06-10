import axios, { type AxiosRequestConfig } from 'axios'
import { BACKEND_HOST_LOCAL, BACKEND_HOST_PROD } from '@/constants'
import { ElMessage } from 'element-plus'
import router from './router'

// 区分开发和生产环境
const isDev = import.meta.env.DEV
const BASE_URL = isDev ? BACKEND_HOST_LOCAL : BACKEND_HOST_PROD

// 创建 Axios 实例
const myAxios = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  withCredentials: true,
})

// 全局请求拦截器
myAxios.interceptors.request.use(
  function (config) {
    // 自动添加 /api 前缀（如果还没有）
    if (config.url && !config.url.startsWith('/api')) {
      config.url = `/api${config.url}`
    }
    return config
  },
  function (error) {
    return Promise.reject(error)
  },
)

// 全局响应拦截器
myAxios.interceptors.response.use(
  function (response) {
    const { data } = response
    if (!data) {
      ElMessage.error('服务异常')
      return Promise.reject(new Error('服务异常'))
    }

    const code: number = data.code
    const requestPath: string = response.config.url ?? ''

    if (code === 40100 && !requestPath.includes('user/get/login')) {
      return Promise.reject(new Error('请先登录'))
    }

    if (code !== 0) {
      return Promise.reject(new Error(data.message ?? '服务器错误'))
    }

    // 直接返回响应数据（BaseResponse）
    return data
  },
  function (error) {
    if (error.response?.status === 401) {
      router.push('/user/login?redirect=' + window.location.pathname)
    }
    return Promise.reject(error)
  },
)

/** 响应拦截器已将返回值解包为 BaseResponse，覆盖类型避免 AxiosResponse→T 错位 */
export default myAxios as unknown as {
  <T = any>(url: string, config?: AxiosRequestConfig): Promise<T>
}
