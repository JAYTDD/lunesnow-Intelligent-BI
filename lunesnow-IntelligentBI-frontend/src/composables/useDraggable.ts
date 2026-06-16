/**
 * 可拖拽元素 Hook
 * 使用 transform: translate 优化性能，避免触发页面重排
 */

import { ref, onUnmounted, type Ref } from 'vue'

interface DraggableOptions {
  /** 初始位置 x */
  initX?: number
  /** 初始位置 y */
  initY?: number
  /** 拖拽结束回调 */
  onDragEnd?: (x: number, y: number) => void
  /** 拖拽开始回调 */
  onDragStart?: (x: number, y: number) => void
  /** 是否启用拖拽 */
  disabled?: boolean
}

interface DraggableReturn {
  /** 当前 x 坐标 */
  x: Ref<number>
  /** 当前 y 坐标 */
  y: Ref<number>
  /** 是否正在拖拽 */
  isDragging: Ref<boolean>
  /** 绑定到元素的事件 */
  bind: (el: HTMLElement) => void
  /** 设置位置 */
  setPosition: (x: number, y: number) => void
}

export function useDraggable(options: DraggableOptions = {}): DraggableReturn {
  const { initX = 0, initY = 0, onDragEnd, onDragStart, disabled = false } = options

  const x = ref(initX)
  const y = ref(initY)
  const isDragging = ref(false)

  let targetEl: HTMLElement | null = null
  let startX = 0
  let startY = 0
  let startTranslateX = 0
  let startTranslateY = 0

  // 更新元素位置（使用 transform，GPU 加速）
  const updatePosition = () => {
    if (!targetEl) return
    targetEl.style.transform = `translate(${x.value}px, ${y.value}px)`
  }

  // 鼠标按下
  const handleMouseDown = (e: MouseEvent) => {
    if (disabled) return
    // 只响应左键
    if (e.button !== 0) return

    isDragging.value = true
    startX = e.clientX
    startY = e.clientY
    startTranslateX = x.value
    startTranslateY = y.value

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

    const deltaX = e.clientX - startX
    const deltaY = e.clientY - startY

    x.value = startTranslateX + deltaX
    y.value = startTranslateY + deltaY

    // 直接更新 transform，不触发重排
    updatePosition()
  }

  // 鼠标松开
  const handleMouseUp = () => {
    if (!isDragging.value) return

    isDragging.value = false

    // 移除全局事件监听
    document.removeEventListener('mousemove', handleMouseMove)
    document.removeEventListener('mouseup', handleMouseUp)

    // 恢复文本选择
    document.body.style.userSelect = ''
    document.body.style.cursor = ''

    onDragEnd?.(x.value, y.value)
  }

  // 绑定到元素
  const bind = (el: HTMLElement) => {
    targetEl = el
    el.style.cursor = 'grab'
    el.style.willChange = 'transform' // 提示浏览器优化
    el.addEventListener('mousedown', handleMouseDown)
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
    if (targetEl) {
      targetEl.removeEventListener('mousedown', handleMouseDown)
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
