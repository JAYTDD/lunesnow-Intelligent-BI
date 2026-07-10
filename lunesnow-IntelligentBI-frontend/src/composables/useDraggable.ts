/**
 * 可拖拽元素 Hook
 * 使用 transform: translate3d 优化性能，避免触发页面重排。
 * - target: 被移动的元素（设置 transform）
 * - handle: 触发拖拽的元素（默认同 target），可用于只在标题栏拖拽
 * - getZoom: 返回画布缩放比例，用于换算拖拽位移
 * - ignore: 命中返回 true 时不触发拖拽（如按钮）
 */

import { ref, onUnmounted, type Ref } from 'vue'

// 拖拽参数
interface DraggableOptions {
  initX?: number // 初始位置 x
  initY?: number // 初始位置 y
  onDragEnd?: (x: number, y: number) => void // 拖拽结束回调
  onDragStart?: (x: number, y: number) => void // 拖拽开始回调
  disabled?: boolean // 是否启用拖拽
  getZoom?: () => number // 获取当前画布缩放比例，用于换算拖拽位移
  ignore?: (target: EventTarget | null) => boolean // 命中该元素时不触发拖拽（如操作按钮）
}

// 拖拽返回值
interface DraggableReturn {
  x: Ref<number> // 当前 x 坐标
  y: Ref<number> // 当前 y 坐标
  isDragging: Ref<boolean> // 是否正在拖拽
  bind: (target: HTMLElement, handle?: HTMLElement) => void // 绑定：target=被移动元素，handle=触发拖拽元素(默认同 target)
  setPosition: (x: number, y: number) => void // 设置位置
}

// 拖拽实现
export function useDraggable(options: DraggableOptions = {}): DraggableReturn {
  // 解构参数
  const {
    initX = 0,
    initY = 0,
    onDragEnd,
    onDragStart,
    disabled = false,
    getZoom,
    ignore,
  } = options

  const x = ref(initX) // 当前 x 坐标
  const y = ref(initY) // 当前 y 坐标
  const isDragging = ref(false) // 是否正在拖拽

  let targetEl: HTMLElement | null = null // 拖拽目标元素
  let handleEl: HTMLElement | null = null // 触发拖拽元素(默认同 target)
  let startX = 0 // 拖拽开始时 x 坐标
  let startY = 0 // 拖拽开始时 y 坐标
  let startTranslateX = 0 // 拖拽开始时 transform 位置 x
  let startTranslateY = 0 // 拖拽开始时 transform 位置 y

  // 更新元素位置（使用 transform，GPU 加速，避免重排）
  const updatePosition = () => {
    if (!targetEl) return
    targetEl.style.transform = `translate3d(${x.value}px, ${y.value}px, 0)`
  }

  // 鼠标按下
  const handleMouseDown = (e: MouseEvent) => {
    if (disabled) return
    // 只响应左键
    if (e.button !== 0) return
    // 命中忽略元素（如按钮）不触发拖拽
    if (ignore?.(e.target)) return

    isDragging.value = true
    startX = e.clientX
    startY = e.clientY
    startTranslateX = x.value
    startTranslateY = y.value

    // 仅拖拽时提升图层，避免常驻 will-change 占用显存
    if (targetEl) targetEl.style.willChange = 'transform'

    // 添加全局事件监听
    document.addEventListener('mousemove', handleMouseMove)
    document.addEventListener('mouseup', handleMouseUp)

    // 禁止文本选择
    document.body.style.userSelect = 'none'
    document.body.style.cursor = 'grabbing'

    onDragStart?.(x.value, y.value)
  }

  // 鼠标移动
  const handleMouseMove = (e: MouseEvent) => {
    if (!isDragging.value) return

    const zoom = getZoom ? getZoom() : 1
    const deltaX = e.clientX - startX
    const deltaY = e.clientY - startY

    x.value = startTranslateX + deltaX / zoom
    y.value = startTranslateY + deltaY / zoom

    // 直接更新 transform，不触发重排
    updatePosition()
  }

  // 鼠标松开
  const handleMouseUp = () => {
    if (!isDragging.value) return

    isDragging.value = false
    if (targetEl) targetEl.style.willChange = ''

    // 移除全局事件监听
    document.removeEventListener('mousemove', handleMouseMove)
    document.removeEventListener('mouseup', handleMouseUp)

    // 恢复文本选择
    document.body.style.userSelect = ''
    document.body.style.cursor = ''

    onDragEnd?.(x.value, y.value)
  }

  // 绑定到元素：target 被移动，handle 触发拖拽
  const bind = (target: HTMLElement, handle?: HTMLElement) => {
    targetEl = target
    handleEl = handle ?? target
    handleEl.style.cursor = 'grab' // 鼠标指针样式
    handleEl.addEventListener('mousedown', handleMouseDown)
    updatePosition()
  }

  // 设置位置
  const setPosition = (newX: number, newY: number) => {
    x.value = newX
    y.value = newY
    updatePosition()
  }

  // 清理
  onUnmounted(() => {
    if (handleEl) {
      handleEl.removeEventListener('mousedown', handleMouseDown)
    }
    document.removeEventListener('mousemove', handleMouseMove)
    document.removeEventListener('mouseup', handleMouseUp)
  })

  return {
    x,
    y,
    isDragging,
    bind,
    setPosition,
  }
}
