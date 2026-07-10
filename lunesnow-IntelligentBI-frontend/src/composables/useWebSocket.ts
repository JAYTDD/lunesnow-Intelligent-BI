/**
 * WebSocket 通用 Hook
 * 支持自动重连、心跳检测（含超时检测）
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
  const MAX_MESSAGES = 100 // 消息列表最大容量
  const messages = ref<WebSocketMessage[]>([]) // 消息列表
  const connected = ref(false) // 连接状态

  const loginUserStore = useLoginUserStore() // 登录用户store

  // 动态构建 WebSocket URL（每次连接时读取最新 userId）
  const buildWsUrl = (): string => {
    if (url) return url // 有自定义url则直接使用
    const uid = loginUserStore.loginUser.id // 当前用户id
    const isDev = import.meta.env.DEV // 是否开发环境
    const wsHost = import.meta.env.VITE_WS_HOST as string | undefined // 从环境变量读取WS地址
    const backendHost = isDev ? wsHost || 'localhost:8088' : window.location.host // 后端地址（开发环境优先用环境变量）
    const protocol = isDev ? 'ws:' : window.location.protocol === 'https:' ? 'wss:' : 'ws:' // 协议
    return `${protocol}//${backendHost}/api/ws/chart?userId=${uid}`
  }

  let ws: WebSocket | null = null // WebSocket实例
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null // 重连定时器
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null // 心跳定时器
  let heartbeatTimeoutTimer: ReturnType<typeof setTimeout> | null = null // 心跳超时定时器
  let reconnectCount = 0 // 重连次数计数
  let manualClose = false // 是否手动关闭标记
  const maxReconnect = 5 // 最大重连次数

  /** 启动心跳检测 */
  const startHeartbeat = () => {
    stopHeartbeat() // 先清除旧定时器，防止重复
    heartbeatTimer = setInterval(() => {
      // 每30秒发送一次ping
      if (ws && ws.readyState === WebSocket.OPEN) {
        // 连接处于打开状态才发送
        ws.send('ping')
        // 启动超时检测：10秒内未收到pong则认为连接已死
        heartbeatTimeoutTimer = setTimeout(() => {
          console.warn('[WebSocket] 心跳超时，主动断开连接')
          ws?.close()
        }, 10000)
      }
    }, 30000)
  }

  /** 停止心跳检测 */
  const stopHeartbeat = () => {
    if (heartbeatTimer) {
      // 定时器存在则清除
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
    if (heartbeatTimeoutTimer) {
      // 清除超时定时器
      clearTimeout(heartbeatTimeoutTimer)
      heartbeatTimeoutTimer = null
    }
  }

  /** 连接WebSocket */
  const connect = () => {
    const uid = loginUserStore.loginUser.id // 当前用户id
    if (!uid || uid <= 0) {
      // 用户未登录则跳过连接
      console.log('[WebSocket] 用户未登录，跳过连接')
      return
    }

    manualClose = false // 重置手动关闭标记

    console.log('[WebSocket] 连接:', buildWsUrl())
    ws = new WebSocket(buildWsUrl()) // 创建WebSocket实例

    // 连接成功回调
    ws.onopen = () => {
      console.log('[WebSocket] 连接成功')
      connected.value = true // 更新连接状态
      reconnectCount = 0 // 重置重连计数
      startHeartbeat() // 启动心跳
    }

    // 收到消息回调
    ws.onmessage = (event) => {
      if (event.data === 'pong') {
        // 心跳响应
        if (heartbeatTimeoutTimer) {
          // 收到pong则清除超时定时器
          clearTimeout(heartbeatTimeoutTimer)
          heartbeatTimeoutTimer = null
        }
        return
      }

      try {
        const data: WebSocketMessage = JSON.parse(event.data) // 解析消息
        console.log('[WebSocket] 收到消息:', data)
        messages.value.push(data) // 添加到消息列表
        if (messages.value.length > MAX_MESSAGES) {
          // 超过上限则裁剪旧消息
          messages.value = messages.value.slice(-MAX_MESSAGES)
        }

        // 显示通知
        if (data.type === 'success') {
          // 成功类型消息
          ElMessage.success({
            message: `图表"${data.chartName || ''}"生成成功`,
            duration: 5000,
          })
        } else if (data.type === 'failure') {
          // 失败类型消息
          ElMessage.error({
            message: `图表"${data.chartName || ''}"生成失败`,
            duration: 8000,
          })
        }
      } catch (e) {
        console.log('[WebSocket] 消息解析失败:', e)
      }
    }

    // 连接关闭回调
    ws.onclose = (event) => {
      console.log('[WebSocket] 连接关闭:', event.code, event.reason)
      connected.value = false // 更新连接状态
      stopHeartbeat() // 停止心跳
      ws = null // 清空实例引用

      if (manualClose) {
        // 主动关闭则不重连
        manualClose = false
        return
      }

      // 非主动关闭 → 尝试重连
      if (reconnectCount < maxReconnect) {
        // 未超过最大重连次数
        reconnectCount++ // 先递增，再判断和重连
        const delay = Math.min(1000 * Math.pow(2, reconnectCount - 1), 30000) // 指数退避延迟
        console.log(`[WebSocket] ${delay}ms 后重连 (${reconnectCount}/${maxReconnect})`)
        reconnectTimer = setTimeout(() => {
          // 延迟重连
          connect()
        }, delay)
      } else {
        console.warn('[WebSocket] 已达最大重连次数，停止重连')
      }
    }

    // 连接错误回调
    ws.onerror = (error) => {
      console.error('[WebSocket] 连接错误:', error)
    }
  }

  /** 断开WebSocket连接 */
  const disconnect = () => {
    manualClose = true // 标记为手动关闭，阻止自动重连
    if (reconnectTimer) {
      // 清除重连定时器
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    stopHeartbeat() // 停止心跳
    if (ws) {
      // 实例存在则关闭连接
      ws.close(1000, '手动关闭')
      ws = null
    }
    connected.value = false // 更新连接状态
  }

  /** 发送消息 */
  const send = (data: any) => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      // 连接处于打开状态才发送
      ws.send(JSON.stringify(data))
    }
  }

  // 跟随登录态：登录成功后连接，未登录/登出时断开
  // 解决"setup 阶段立即 connect 时用户尚未登录（id 为空）导致永远连不上"的问题
  watch(
    () => loginUserStore.loginUser.id, // 监听用户id变化
    (id) => {
      if (id && id > 0) {
        // 已登录且id有效
        if (ws && ws.readyState === WebSocket.OPEN) return // 连接已打开则跳过
        connect()
      } else {
        // 未登录或id无效则断开
        disconnect()
      }
    },
    { immediate: true }, // 立即执行一次
  )

  // 组件卸载时断开
  onUnmounted(() => {
    disconnect() // 清理连接
  })

  return {
    messages,
    connected,
    send,
    disconnect,
    reconnect: connect,
  }
}
