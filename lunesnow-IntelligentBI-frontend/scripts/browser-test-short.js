// ============================================================
// 浏览器自动化对比测试 ——【短任务场景：任务约 10s 完成】
// 真实运行 src/composables/usePolling.ts（指数退避 + Page Visibility）
// 自动与「朴素固定轮询（不优化）」对比请求数。
// 用法：
//   1. 先在页面上 F5 刷新一次（避免监听叠加）
//   2. 打开「我的图表」页（确保 Vue/Vite 已加载）
//   3. 复制本文件全部内容 → F12 Console 回车
//   4. 等约 60 秒，自动打印对比结果
// 注意：依赖 Vite dev server，请在 npm run dev 下运行
// ============================================================
;(async () => {
  const TASK_DONE_MS = 10000 // 【场景参数】任务完成耗时（短任务）
  const OBS_MS = TASK_DONE_MS + 5000 // 总观测时长（任务完成后留 5s）
  // 隐藏期跟随任务进度：覆盖任务进行中的中段 30%~80%，三份隐藏占比一致，仅任务时长不同
  const HIDDEN_START = Math.round(TASK_DONE_MS * 0.3)
  const HIDDEN_END = Math.round(TASK_DONE_MS * 0.8)
  const INTERVAL = 3000
  const MAX_INT = 30000
  const BACKOFF = 1.5

  // 覆盖 document.hidden，使脚本可模拟可见性变化
  let __hidden = false
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => __hidden })

  // 导入真实 usePolling 源码（Vite 会编译 .ts 为可执行的 ESM）
  const { usePolling } = await import('/src/composables/usePolling.ts')
  // 通过本项目的 .ts 重新导出拿到真实 vue（避免裸说明符 'vue' 在 Console 中无法解析）
  const { createApp, h } = await import('/scripts/_vue_api.ts')

  let poller
  let optimizedReq = 0
  const startTime = Date.now()

  // 挂载临时组件，让 usePolling 的 onMounted 生效（注册 visibilitychange 监听）
  const comp = {
    setup() {
      poller = usePolling(
        async () => {
          optimizedReq++ // 每次真实请求计数
          if (Date.now() - startTime >= TASK_DONE_MS) return true // 任务完成后停止
          return false
        },
        { interval: INTERVAL, maxInterval: MAX_INT, backoff: BACKOFF },
      )
      return () => h('div')
    },
  }

  createApp(comp).mount(document.createElement('div'))
  poller.start()   // 启动真实轮询

  // 实时朴素基线对照：固定间隔、不暂停，用于和上方真实 [轮询] #N 并排对比
  let baseCount = 0
  const printBase = () => {
    baseCount++
    const el = ((Date.now() - startTime) / 1000).toFixed(1)
    console.log(`[朴素基线] +${el}s 应发第 ${baseCount} 次（固定 ${INTERVAL}ms，不暂停）`)
  }
  printBase() // 立即首查
  const baseTimer = setInterval(() => {
    if (Date.now() - startTime >= TASK_DONE_MS) {
      clearInterval(baseTimer)
      console.log(`[朴素基线] 任务完成，停止（共 ${baseCount} 次）`)
      return
    }
    printBase()
  }, INTERVAL)

  // 自动时间轴：到时间点模拟切走 / 切回（无需手动操作）
  setTimeout(() => {
    __hidden = true
    document.dispatchEvent(new Event('visibilitychange'))
  }, HIDDEN_START)
  setTimeout(() => {
    __hidden = false
    document.dispatchEvent(new Event('visibilitychange'))
  }, HIDDEN_END)

  // 观测结束，统计对比
  setTimeout(() => {
    try {
      poller && poller.stop()
    } catch (e) {}
    const baselineReq = Math.ceil(TASK_DONE_MS / INTERVAL) // 朴素：固定间隔轮询直到任务完成（隐藏期也照发）
    const reduction = baselineReq === 0 ? 0 : ((1 - optimizedReq / baselineReq) * 100).toFixed(1)
    console.log('\n========== 对比结果（短任务）==========')
    console.log(`任务完成耗时           ：${TASK_DONE_MS / 1000}s`)
    console.log(
      `模拟隐藏时段           ：${HIDDEN_START / 1000}s ~ ${HIDDEN_END / 1000}s（共 ${(HIDDEN_END - HIDDEN_START) / 1000}s）`,
    )
    console.log(`朴素固定轮询请求数     ：${baselineReq}`)
    console.log(`优化后(退避+暂停)请求数：${optimizedReq}`)
    console.log(`无效请求减少           ：${reduction}%`)
    console.log('========================================')
  }, OBS_MS)

  console.log(
    `[测试已启动] 场景=短任务(${TASK_DONE_MS / 1000}s)，约 ${OBS_MS / 1000}s 后自动输出对比...`,
  )
})()
