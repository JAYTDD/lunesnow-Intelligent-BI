/**
 * 通用轮询 Hook
 * 支持指数退避策略 + Page Visibility API
 */

import { ref, onUnmounted, onMounted } from 'vue'

interface PollingOptions {
  /** 初始轮询间隔（毫秒），默认 3000 */
  interval?: number
  /** 最大轮询间隔（毫秒），默认 30000 */
  maxInterval?: number
  /** 退避系数，默认 1.5 */
  backoff?: number
}

/**
 * 通用轮询 Hook
 * @param callback 轮询回调函数，返回 true 表示停止轮询
 * @param options 配置选项
 * @returns start/stop 控制函数
 */
export function usePolling(callback: () => Promise<boolean>, options: PollingOptions = {}) {
  const { interval = 3000, maxInterval = 30000, backoff = 1.5 } = options

  const currentInterval = ref(interval) // 当前轮询间隔
  const timer = ref<ReturnType<typeof setTimeout>>() // 定时器
  const isRunning = ref(false) // 是否正在运行
  const isPageVisible = ref(true) // 页面是否可见
  let ticking = false // 防止 tick 并发

  // 调度下一次轮询
  const scheduleNext = () => {
    if (!isRunning.value) return
    console.log(`[轮询] 下次执行: ${currentInterval.value}ms 后`)
    timer.value = setTimeout(async () => {
      await tick()
      scheduleNext()
    }, currentInterval.value)
  }

  // 执行轮询
  const tick = async () => {
    if (!isPageVisible.value || ticking) {
      console.log('[轮询] 跳过（不可见或正在执行）')
      return
    }
    ticking = true
    console.log(`[轮询] 执行，当前间隔: ${currentInterval.value}ms`)
    try {
      // 执行回调函数，返回 true 表示停止轮询
      const shouldStop = await callback()
      if (shouldStop) {
        console.log('[轮询] 回调返回 true，停止轮询')
        stop()
        return
      }
      // 回调成功（返回 false），重置间隔，保持快速轮询
      currentInterval.value = interval
    } catch (error) {
      console.error('[轮询] 执行出错:', error)
      // 出错时退避
      currentInterval.value = Math.min(currentInterval.value * backoff, maxInterval)
      console.log(`[轮询] 退避至: ${currentInterval.value}ms`)
    } finally {
      ticking = false
    }
  }

  // 启动轮询
  const start = () => {
    console.log(`[轮询] 启动，初始间隔: ${interval}ms`)
    if (isRunning.value) {
      console.log('[轮询] 已在运行，跳过')
      return
    }

    isRunning.value = true
    currentInterval.value = interval
    console.log(`[轮询] 状态: running=true, interval=${interval}ms`)

    // 立即执行一次，等完成后再调度下一次
    tick().then(() => {
      scheduleNext()
    })
  }

  // 停止轮询
  const stop = () => {
    console.log('[轮询] 停止')
    isRunning.value = false
    if (timer.value) {
      clearTimeout(timer.value)
      timer.value = undefined
    }
  }

  // 暂停（页面不可见时）
  const pause = () => {
    console.log('[轮询] 暂停（页面不可见）')
    if (timer.value) {
      clearTimeout(timer.value)
      timer.value = undefined
    }
  }

  // 恢复（页面可见时）
  const resume = () => {
    if (isRunning.value && !timer.value) {
      console.log('[轮询] 恢复（页面可见），重置间隔并立即请求')
      // 重置间隔，从初始值重新开始
      currentInterval.value = interval
      // 立即执行一次，等完成后再调度下一次
      tick().then(() => {
        scheduleNext()
      })
    }
  }

  // Page Visibility API
  const handleVisibilityChange = () => {
    const wasVisible = isPageVisible.value
    isPageVisible.value = !document.hidden
    console.log(`[轮询] 页面可见性变化: ${wasVisible} → ${isPageVisible.value}`)
    if (!isPageVisible.value) {
      pause()
    } else {
      resume()
    }
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', handleVisibilityChange)
    console.log('[轮询] 已注册 visibilitychange 监听器')
  })

  onUnmounted(() => {
    stop()
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  })

  return {
    start,
    stop,
    isRunning,
    currentInterval,
  }
}
