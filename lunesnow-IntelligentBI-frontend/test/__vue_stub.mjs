// Vue API 桩 —— 供 Node 测试环境模拟真实 usePolling.ts 中 import from 'vue' 的 API
// 只实现 usePolling 实际用到的：ref、onMounted、onUnmounted

const mountQueue = []
const unmountQueue = []

export function ref(val) {
  return { value: val }
}
export { ref as shallowRef }

export function onMounted(fn) {
  mountQueue.push(fn)
}

export function onUnmounted(fn) {
  unmountQueue.push(fn)
}

// 测试用：手动触发 mounted / unmounted 回调
export function __flushMount() {
  for (const fn of mountQueue.splice(0)) fn()
}
export function __flushUnmount() {
  for (const fn of unmountQueue.splice(0)) fn()
}

// 重置队列（每次测试前调用）
export function __resetQueues() {
  mountQueue.length = 0
  unmountQueue.length = 0
}
