/**
 * WebSocket 通用 Hook
 * 支持自动重连、心跳检测
 */

import { ref, watch, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useLoginUserStore } from '@/stores/useLoginUserStore'

interface WebSocketMessage {
  type: 'success' | 'failure' | 'info'
  chartId?: number
  chartName?: string
  message: string
}

/**
 * WebSocket Hook
 * @param url WebSocket 地址（可选，默认根据环境自动构建）
 * @returns messages / connected / send
 */
export function useWebSocket(url?: string) {
  const messages = ref<WebSocketMessage[]>([])
  const connected = ref(false)

  const loginUserStore = useLoginUserStore()

  // 动态构建 WebSocket URL（每次连接时读取最新 userId，避免初始化时烤死 -1）
  const buildWsUrl = (): string => {
    if (url) return url
    const uid = loginUserStore.loginUser.id
    const isDev = import.meta.env.DEV
    const backendHost = isDev ? 'localhost:8088' : window.location.host
    const protocol = isDev ? 'ws:' : window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${backendHost}/api/ws/chart?userId=${uid}`
  }

  let ws: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let reconnectCount = 0
  const maxReconnect = 5

  // 连接
  const connect = () => {
    const uid = loginUserStore.loginUser.id
    if (!uid || uid <= 0) {
      console.log('[WebSocket] 用户未登录，跳过连接')
      return
    }

    console.log('[WebSocket] 连接:', buildWsUrl())
    ws = new WebSocket(buildWsUrl())

    ws.onopen = () => {
      console.log('[WebSocket] 连接成功')
      connected.value = true
      reconnectCount = 0

      // 启动心跳
      startHeartbeat()
    }

    ws.onmessage = (event) => {
      // 心跳响应忽略
      if (event.data === 'pong') return

      try {
        const data: WebSocketMessage = JSON.parse(event.data)
        console.log('[WebSocket] 收到消息:', data)
        messages.value.push(data)

        // 显示通知
        if (data.type === 'success') {
          ElMessage.success({
            message: `图表"${data.chartName || ''}"生成成功`,
            duration: 5000,
          })
        } else if (data.type === 'failure') {
          ElMessage.error({
            message: `图表"${data.chartName || ''}"生成失败`,
            duration: 8000,
          })
        }
      } catch (e) {
        console.log('[WebSocket] 消息解析失败:', e)
      }
    }

    ws.onclose = (event) => {
      console.log('[WebSocket] 连接关闭:', event.code, event.reason)
      connected.value = false
      stopHeartbeat()

      // 非主动关闭 → 尝试重连
      if (event.code !== 1000 && reconnectCount < maxReconnect) {
        // 指数退避
        const delay = Math.min(1000 * Math.pow(2, reconnectCount), 30000)
        console.log(`[WebSocket] ${delay}ms 后重连 (${reconnectCount + 1}/${maxReconnect})`)
        reconnectTimer = setTimeout(() => {
          reconnectCount++
          connect()
        }, delay)
      }
    }

    ws.onerror = (error) => {
      console.error('[WebSocket] 连接错误:', error)
    }
  }

  // 心跳
  const startHeartbeat = () => {
    heartbeatTimer = setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send('ping')
      }
    }, 30000)
  }

  const stopHeartbeat = () => {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  // 断开
  const disconnect = () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    stopHeartbeat()
    if (ws) {
      ws.close(1000, '手动关闭')
      ws = null
    }
    connected.value = false
  }

  // 发送消息
  const send = (data: any) => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(data))
    }
  }

  // 跟随登录态：登录成功后连接，未登录/登出时断开
  // 解决"setup 阶段立即 connect 时用户尚未登录（id 为空）导致永远连不上"的问题
  watch(
    () => loginUserStore.loginUser.id,
    (id) => {
      if (id && id > 0) {
        // 已存在有效连接则不再重复连接
        if (ws && ws.readyState === WebSocket.OPEN) return
        connect()
      } else {
        disconnect()
      }
    },
    { immediate: true },
  )

  // 组件卸载时断开
  onUnmounted(() => {
    disconnect()
  })

  return {
    messages,
    connected,
    send,
    disconnect,
    reconnect: connect,
  }
}
