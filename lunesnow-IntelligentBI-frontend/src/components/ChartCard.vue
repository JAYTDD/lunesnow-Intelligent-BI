<template>
  <div
    ref="rootRef"
    class="chart-card"
    :class="{ 'chart-card--dragging': isDragging }"
    :style="{ width: item.width + 'px', height: item.height + 'px' }"
  >
    <!-- 拖拽手柄（只有这里能发起拖拽） -->
    <div ref="headerRef" class="card-header">
      <div class="card-handle">
        <el-icon :size="14"><Rank /></el-icon>
        <span class="card-name">{{ item.name }}</span>
      </div>
      <div class="card-actions">
        <el-button link size="small" @click="onRefresh">
          <el-icon :size="14"><Refresh /></el-icon>
        </el-button>
        <el-button link size="small" type="danger" @click="emit('remove', item.id)">
          <el-icon :size="14"><Close /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 图表容器 -->
    <div class="card-body">
      <div ref="chartRef" class="chart-container"></div>
    </div>

    <!-- 缩放手柄 -->
    <div class="resize-handle" @mousedown.stop="startResize">
      <el-icon :size="10"><BottomRight /></el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { Rank, Refresh, Close, BottomRight } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { useDraggable } from '@/composables/useDraggable'
import type { DashboardItem } from '@/types/dashboard'
import { safeParseChartConfig } from '@/utils/chartValidator'

const props = defineProps<{ item: DashboardItem; zoom: number }>()
const emit = defineEmits<{
  'update:position': [x: number, y: number]
  'update:size': [w: number, h: number]
  remove: [id: string]
}>()

const rootRef = ref<HTMLElement>()
const headerRef = ref<HTMLElement>()
const chartRef = ref<HTMLElement>()

// 拖拽交给 useDraggable：target=卡片根，handle=标题栏
const { isDragging, bind, setPosition } = useDraggable({
  initX: props.item.x,
  initY: props.item.y,
  getZoom: () => props.zoom,
  // 点击操作按钮不触发拖拽
  ignore: (t) => !!(t as HTMLElement)?.closest?.('.card-actions'),
  onDragEnd: (nx, ny) => emit('update:position', nx, ny),
})

// 外部修改坐标时同步（加载布局、适配视图等）
watch(
  () => props.item.x,
  (nx) => setPosition(nx, props.item.y),
)
watch(
  () => props.item.y,
  (ny) => setPosition(props.item.x, ny),
)

// ==================== 拖拽缩放 ====================
let resizeRafId = 0
let resizeState: {
  startX: number
  startY: number
  startW: number
  startH: number
  pendingW: number
  pendingH: number
} | null = null

const startResize = (e: MouseEvent) => {
  if (e.button !== 0) return
  const el = rootRef.value
  if (!el) return
  resizeState = {
    startX: e.clientX,
    startY: e.clientY,
    startW: props.item.width,
    startH: props.item.height,
    pendingW: props.item.width,
    pendingH: props.item.height,
  }
  el.style.willChange = 'transform'
  document.addEventListener('mousemove', onResizeMove)
  document.addEventListener('mouseup', onResizeEnd)
  document.body.style.userSelect = 'none'
  document.body.style.cursor = 'nwse-resize'
}

const onResizeMove = (e: MouseEvent) => {
  if (!resizeState || !rootRef.value) return
  const zoom = props.zoom
  resizeState.pendingW = Math.max(200, resizeState.startW + (e.clientX - resizeState.startX) / zoom)
  resizeState.pendingH = Math.max(150, resizeState.startH + (e.clientY - resizeState.startY) / zoom)
  // 合帧：一帧内只写一次 DOM
  if (!resizeRafId) {
    resizeRafId = requestAnimationFrame(() => {
      resizeRafId = 0
      const s = resizeState
      const el = rootRef.value
      if (s && el) {
        el.style.width = `${s.pendingW}px`
        el.style.height = `${s.pendingH}px`
      }
    })
  }
}

const onResizeEnd = () => {
  if (resizeRafId) {
    cancelAnimationFrame(resizeRafId)
    resizeRafId = 0
  }
  const s = resizeState
  if (s && rootRef.value) {
    props.item.width = s.pendingW
    props.item.height = s.pendingH
    rootRef.value.style.willChange = ''
    emit('update:size', s.pendingW, s.pendingH)
    // 缩放后重新渲染图表
    chartInstance?.resize()
  }
  resizeState = null
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', onResizeEnd)
  document.body.style.userSelect = ''
  document.body.style.cursor = ''
}

// ==================== ECharts ====================
let chartInstance: echarts.ECharts | null = null

const renderChart = () => {
  const dom = chartRef.value
  if (!dom || !props.item.genChart) return

  // 清理旧实例
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }

  const option = safeParseChartConfig(props.item.genChart)
  if (!option) return

  chartInstance = echarts.init(dom)
  chartInstance.setOption(option)
}

const onRefresh = () => chartInstance?.resize()

const handleWinResize = () => chartInstance?.resize()

// 尺寸/配置变化时同步图表
watch(() => props.item.width, () => chartInstance?.resize())
watch(() => props.item.height, () => chartInstance?.resize())
watch(() => props.item.genChart, () => nextTick(renderChart))

onMounted(() => {
  if (rootRef.value && headerRef.value) bind(rootRef.value, headerRef.value)
  nextTick(renderChart)
  window.addEventListener('resize', handleWinResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleWinResize)
  if (resizeRafId) cancelAnimationFrame(resizeRafId)
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', onResizeEnd)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<style lang="scss" scoped>
.chart-card {
  position: absolute;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e4e4e7;
  overflow: hidden;
  transition:
    box-shadow 0.2s,
    border-color 0.2s;

  &:hover {
    border-color: #d4d4d8;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);

    .resize-handle {
      opacity: 1;
    }
  }

  &--dragging {
    border-color: #18181b;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
    z-index: 100;
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid #f4f4f5;
  background: #fafafa;
  cursor: grab;

  &:active {
    cursor: grabbing;
  }
}

.card-handle {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #71717a;
}

.card-name {
  font-size: 12px;
  font-weight: 600;
  color: #3f3f46;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-actions {
  display: flex;
  gap: 2px;
}

.card-body {
  height: calc(100% - 36px - 20px);
}

.chart-container {
  width: 100%;
  height: 100%;
}

.resize-handle {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: nwse-resize;
  color: #d1d5db;
  opacity: 0;
  transition: opacity 0.2s;

  &:hover {
    color: #71717a;
  }
}
</style>
