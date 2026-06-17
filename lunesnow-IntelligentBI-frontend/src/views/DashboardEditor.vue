<template>
  <div class="dashboard-editor">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <h1 class="toolbar-title">仪表盘编辑器</h1>
        <span class="toolbar-hint">拖拽卡片移动 · 角标缩放卡片 · 拖拽空白区域平移画布 · 滚轮缩放</span>
      </div>
      <div class="toolbar-center">
        <el-button size="small" text @click="fitView" title="适应画布">
          <el-icon><FullScreen /></el-icon>
        </el-button>
        <span class="zoom-label">{{ Math.round(canvasZoom * 100) }}%</span>
        <el-button size="small" text @click="resetView" title="重置视图">
          <el-icon><Aim /></el-icon>
        </el-button>
      </div>
      <div class="toolbar-right">
        <el-button size="small" type="primary" @click="showChartPicker = true">
          <el-icon><Plus /></el-icon> 添加图表
        </el-button>
        <el-divider direction="vertical" />
        <el-button size="small" type="danger" plain @click="clearAll">清空</el-button>
      </div>
    </div>

    <!-- 画布区域 -->
    <div
      class="canvas-wrapper"
      ref="canvasWrapperRef"
      @mousedown="onCanvasMouseDown"
      @wheel="onCanvasWheel"
    >
      <div
        class="canvas"
        ref="canvasRef"
        :style="{
          transform: `translate(${canvasOffset.x}px, ${canvasOffset.y}px) scale(${canvasZoom})`,
          transformOrigin: '0 0',
        }"
      >
        <!-- 空状态 -->
        <div v-if="dashboardCharts.length === 0" class="empty-canvas">
          <el-icon :size="48" color="#d1d5db"><DataBoard /></el-icon>
          <p>点击"添加图表"选择你的图表</p>
        </div>

        <!-- 可拖拽 + 可缩放的图表卡片 -->
        <div
          v-for="item in dashboardCharts"
          :key="item.id"
          class="chart-card"
          :class="{ 'chart-card--dragging': item.isDragging }"
          :style="{
            transform: `translate(${item.x}px, ${item.y}px)`,
            width: item.width + 'px',
            height: item.height + 'px',
          }"
          @mousedown.self="startDrag($event, item)"
        >
          <!-- 拖拽手柄 -->
          <div class="card-header" @mousedown.stop="startDrag($event, item)">
            <div class="card-handle">
              <el-icon :size="14"><Rank /></el-icon>
              <span class="card-name">{{ item.name }}</span>
            </div>
            <div class="card-actions">
              <el-button link size="small" @click.stop="refreshChart(item)">
                <el-icon :size="14"><Refresh /></el-icon>
              </el-button>
              <el-button link size="small" type="danger" @click.stop="removeChart(item.id)">
                <el-icon :size="14"><Close /></el-icon>
              </el-button>
            </div>
          </div>

          <!-- 图表容器 -->
          <div class="card-body">
            <div :id="`echart-${item.id}`" class="chart-container"></div>
          </div>

          <!-- 缩放手柄 -->
          <div class="resize-handle" @mousedown.stop="startResize($event, item)">
            <el-icon :size="10"><BottomRight /></el-icon>
          </div>
        </div>
      </div>
    </div>

    <!-- 选择图表弹窗 -->
    <el-dialog v-model="showChartPicker" title="选择图表" width="600px">
      <div v-loading="loadingCharts">
        <div v-if="myCharts.length === 0" class="empty-charts">
          <el-empty description="暂无图表，请先创建图表" />
        </div>
        <div v-else class="chart-picker-grid">
          <div
            v-for="chart in myCharts"
            :key="chart.id"
            class="picker-item"
            :class="{ 'picker-item--added': isAdded(chart.id) }"
            @click="addChartToDashboard(chart)"
          >
            <div class="picker-icon">
              <el-icon :size="24"><PieChart /></el-icon>
            </div>
            <div class="picker-info">
              <span class="picker-name">{{ chart.name || '未命名' }}</span>
              <span class="picker-type">{{ chart.chartType }}</span>
            </div>
            <el-tag v-if="isAdded(chart.id)" size="small" type="info">已添加</el-tag>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showChartPicker = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onUnmounted, watch } from 'vue'
import {
  Plus,
  Rank,
  Refresh,
  Close,
  DataBoard,
  PieChart,
  BottomRight,
  FullScreen,
  Aim,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listMyChartVoByPage } from '@/api/chartController'
import { safeParseChartConfig } from '@/utils/chartValidator'
import * as echarts from 'echarts'

// 仪表盘图表项
interface DashboardItem {
  id: string // 唯一标识 (chart_数据库ID 或自生成)
  chartId?: number // 数据库图表 ID
  name: string
  type: string
  genChart?: string // ECharts 配置 JSON
  x: number
  y: number
  width: number
  height: number
  isDragging: boolean
  isResizing: boolean
}

const STORAGE_KEY = 'dashboard_layout'

const canvasWrapperRef = ref<HTMLDivElement>()
const showChartPicker = ref(false)
const loadingCharts = ref(false)
const myCharts = ref<API.ChartVO[]>([])
const dashboardCharts = ref<DashboardItem[]>([])
const chartInstances = new Map<string, echarts.ECharts>()

// 画布缩放和平移状态
const canvasZoom = ref(1)
const canvasOffset = ref({ x: 0, y: 0 })
let canvasPanState = {
  isPanning: false,
  startX: 0,
  startY: 0,
  startOffsetX: 0,
  startOffsetY: 0,
}

// 当前操作的状态
let dragState:
  | {
      item: DashboardItem | null
      startX: number
      startY: number
      startItemX: number
      startItemY: number
    }
  | { item: null; startX: 0; startY: 0; startItemX: 0; startItemY: 0 } = {
  item: null,
  startX: 0,
  startY: 0,
  startItemX: 0,
  startItemY: 0,
}

let resizeState: {
  item: DashboardItem | null
  startX: number
  startY: number
  startW: number
  startH: number
} = { item: null, startX: 0, startY: 0, startW: 0, startH: 0 }

// ==================== 拖拽移动 ====================
const startDrag = (e: MouseEvent, item: DashboardItem) => {
  if (e.button !== 0) return
  item.isDragging = true
  dragState = {
    item,
    startX: e.clientX,
    startY: e.clientY,
    startItemX: item.x,
    startItemY: item.y,
  }
  document.addEventListener('mousemove', onDragMove)
  document.addEventListener('mouseup', onDragEnd)
  document.body.style.userSelect = 'none'
  document.body.style.cursor = 'grabbing'
}

const onDragMove = (e: MouseEvent) => {
  const { item, startX, startY, startItemX, startItemY } = dragState as any
  if (!item) return
  // 考虑缩放比例
  const zoom = canvasZoom.value
  item.x = startItemX + (e.clientX - startX) / zoom
  item.y = startItemY + (e.clientY - startY) / zoom
}

const onDragEnd = () => {
  const { item } = dragState as any
  if (item) {
    item.isDragging = false
  }
  dragState = { item: null, startX: 0, startY: 0, startItemX: 0, startItemY: 0 }
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
  document.body.style.userSelect = ''
  document.body.style.cursor = ''
  saveLayout()
}

// ==================== 拖拽缩放 ====================
const startResize = (e: MouseEvent, item: DashboardItem) => {
  if (e.button !== 0) return
  item.isResizing = true
  resizeState = {
    item,
    startX: e.clientX,
    startY: e.clientY,
    startW: item.width,
    startH: item.height,
  }
  document.addEventListener('mousemove', onResizeMove)
  document.addEventListener('mouseup', onResizeEnd)
  document.body.style.userSelect = 'none'
  document.body.style.cursor = 'nwse-resize'
}

const onResizeMove = (e: MouseEvent) => {
  const { item, startX, startY, startW, startH } = resizeState as any
  if (!item) return
  // 考虑缩放比例
  const zoom = canvasZoom.value
  const newW = Math.max(200, startW + (e.clientX - startX) / zoom)
  const newH = Math.max(150, startH + (e.clientY - startY) / zoom)
  item.width = newW
  item.height = newH
}

const onResizeEnd = () => {
  const { item } = resizeState as any
  if (item) {
    item.isResizing = false
    // 缩放后重新渲染图表
    const instance = chartInstances.get(item.id)
    instance?.resize()
  }
  resizeState = { item: null, startX: 0, startY: 0, startW: 0, startH: 0 }
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', onResizeEnd)
  document.body.style.userSelect = ''
  document.body.style.cursor = ''
  saveLayout()
}

// ==================== 画布缩放和平移 ====================
// 判断点击目标是否在图表卡片内
const isClickOnCard = (el: HTMLElement): boolean => {
  return !!el.closest('.chart-card')
}

// 画布拖拽平移
const onCanvasMouseDown = (e: MouseEvent) => {
  // 中键直接平移
  if (e.button === 1) {
    startPan(e)
    return
  }
  // 左键：空格+左键 或 点击空白区域（非卡片区域）
  if (e.button === 0 && !isClickOnCard(e.target as HTMLElement)) {
    startPan(e)
  }
}

const startPan = (e: MouseEvent) => {
  canvasPanState.isPanning = true
  canvasPanState.startX = e.clientX
  canvasPanState.startY = e.clientY
  canvasPanState.startOffsetX = canvasOffset.value.x
  canvasPanState.startOffsetY = canvasOffset.value.y
  document.body.style.cursor = 'grabbing'
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', onCanvasPanMove)
  document.addEventListener('mouseup', onCanvasPanEnd)
  e.preventDefault()
}

const onCanvasPanMove = (e: MouseEvent) => {
  if (!canvasPanState.isPanning) return
  const dx = e.clientX - canvasPanState.startX
  const dy = e.clientY - canvasPanState.startY
  canvasOffset.value.x = canvasPanState.startOffsetX + dx
  canvasOffset.value.y = canvasPanState.startOffsetY + dy
}

const onCanvasPanEnd = () => {
  if (canvasPanState.isPanning) {
    canvasPanState.isPanning = false
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }
  document.removeEventListener('mousemove', onCanvasPanMove)
  document.removeEventListener('mouseup', onCanvasPanEnd)
}

// 画布滚轮缩放（以鼠标位置为中心）
const onCanvasWheel = (e: WheelEvent) => {
  e.preventDefault()
  e.stopPropagation()

  const wrapper = canvasWrapperRef.value
  if (!wrapper) return

  const rect = wrapper.getBoundingClientRect()
  // 鼠标在 wrapper 内的位置
  const mouseX = e.clientX - rect.left
  const mouseY = e.clientY - rect.top

  const oldZoom = canvasZoom.value
  const delta = e.deltaY > 0 ? -0.08 : 0.08
  const newZoom = Math.min(3, Math.max(0.2, oldZoom + delta))
  const ratio = newZoom / oldZoom

  // 调整偏移量，使鼠标位置保持不变
  canvasOffset.value.x = mouseX - (mouseX - canvasOffset.value.x) * ratio
  canvasOffset.value.y = mouseY - (mouseY - canvasOffset.value.y) * ratio
  canvasZoom.value = newZoom
}

// 重置画布视图
const resetView = () => {
  canvasZoom.value = 1
  canvasOffset.value = { x: 0, y: 0 }
}

// 适应画布（所有卡片居中）
const fitView = () => {
  if (dashboardCharts.value.length === 0) {
    resetView()
    return
  }
  const wrapper = canvasWrapperRef.value
  if (!wrapper) return

  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
  dashboardCharts.value.forEach(item => {
    minX = Math.min(minX, item.x)
    minY = Math.min(minY, item.y)
    maxX = Math.max(maxX, item.x + item.width)
    maxY = Math.max(maxY, item.y + item.height)
  })

  const contentW = maxX - minX + 80
  const contentH = maxY - minY + 80
  const wrapperW = wrapper.clientWidth
  const wrapperH = wrapper.clientHeight
  const zoom = Math.min(1, wrapperW / contentW, wrapperH / contentH)

  canvasZoom.value = zoom
  canvasOffset.value = {
    x: (wrapperW - contentW * zoom) / 2 - minX * zoom + 40,
    y: (wrapperH - contentH * zoom) / 2 - minY * zoom + 40,
  }
}

// ==================== 图表操作 ====================
// 判断图表是否已添加
const isAdded = (chartId?: number) => {
  if (!chartId) return false
  return dashboardCharts.value.some((d) => d.chartId === chartId)
}

// 添加图表到仪表盘
const addChartToDashboard = (chart: API.ChartVO) => {
  if (isAdded(chart.id)) {
    ElMessage.info('该图表已添加')
    return
  }

  if (chart.status !== 'succeed' || !chart.genChart) {
    ElMessage.warning('该图表未生成成功，无法添加')
    return
  }

  // 计算新位置（避免重叠）
  const count = dashboardCharts.value.length
  const col = count % 3
  const row = Math.floor(count / 3)
  const newX = 40 + col * 380
  const newY = 40 + row * 320

  const newItem: DashboardItem = {
    id: `chart_${chart.id}`,
    chartId: chart.id,
    name: chart.name || '未命名',
    type: chart.chartType || 'bar',
    genChart: chart.genChart,
    x: newX,
    y: newY,
    width: 340,
    height: 280,
    isDragging: false,
    isResizing: false,
  }

  dashboardCharts.value.push(newItem)

  nextTick(() => {
    renderECharts(newItem)
  })

  saveLayout()
  ElMessage.success(`已添加: ${chart.name}`)
}

// 渲染 ECharts（使用真实配置）
const renderECharts = (item: DashboardItem) => {
  nextTick(() => {
    const dom = document.getElementById(`echart-${item.id}`)
    if (!dom || !item.genChart) return

    // 清理旧实例
    const oldInstance = chartInstances.get(item.id)
    if (oldInstance) {
      oldInstance.dispose()
      chartInstances.delete(item.id)
    }

    const option = safeParseChartConfig(item.genChart)
    if (!option) {
      ElMessage.error(`图表"${item.name}"配置解析失败`)
      return
    }

    const instance = echarts.init(dom)
    instance.setOption(option)
    chartInstances.set(item.id, instance)
  })
}

// 刷新单个图表
const refreshChart = (item: DashboardItem) => {
  const instance = chartInstances.get(item.id)
  instance?.resize()
}

// 移除图表
const removeChart = (id: string) => {
  const instance = chartInstances.get(id)
  if (instance) {
    instance.dispose()
    chartInstances.delete(id)
  }
  dashboardCharts.value = dashboardCharts.value.filter((d) => d.id !== id)
  saveLayout()
}

// 清空所有
const clearAll = () => {
  chartInstances.forEach((inst) => inst.dispose())
  chartInstances.clear()
  dashboardCharts.value = []
  saveLayout()
}

// ==================== 加载我的图表 ====================
const loadMyCharts = async () => {
  loadingCharts.value = true
  try {
    const res = await listMyChartVoByPage({
      current: 1,
      pageSize: 20,
    })
    if (res.data?.records) {
      myCharts.value = res.data.records
    }
  } catch (e) {
    ElMessage.error('加载图表列表失败')
  } finally {
    loadingCharts.value = false
  }
}

// ==================== 布局持久化 ====================
const saveLayout = () => {
  const data = dashboardCharts.value.map((d) => ({
    id: d.id,
    chartId: d.chartId,
    name: d.name,
    type: d.type,
    genChart: d.genChart,
    x: d.x,
    y: d.y,
    width: d.width,
    height: d.height,
  }))
  localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
}

const loadLayout = async () => {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return

  try {
    const data = JSON.parse(raw) as Partial<DashboardItem>[]
    dashboardCharts.value = data.map((d) => ({
      ...d,
      isDragging: false,
      isResizing: false,
      width: d.width || 340,
      height: d.height || 280,
    })) as DashboardItem[]

    // 逐个渲染图表
    nextTick(() => {
      dashboardCharts.value.forEach((item) => {
        if (item.genChart) {
          renderECharts(item)
        }
      })
    })
  } catch (e) {
    console.error('布局加载失败', e)
  }
}

// ==================== 窗口大小变化 ====================
const handleResize = () => {
  chartInstances.forEach((inst) => inst.resize())
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  window.addEventListener('keydown', onKeyDown)
  window.addEventListener('keyup', onKeyUp)
  loadLayout()
  loadMyCharts()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('keyup', onKeyUp)
  document.removeEventListener('mousemove', onCanvasPanMove)
  document.removeEventListener('mouseup', onCanvasPanEnd)
  chartInstances.forEach((inst) => inst.dispose())
  chartInstances.clear()
})

// 键盘事件：按住空格进入平移模式
let spacePressed = false
const onKeyDown = (e: KeyboardEvent) => {
  if (e.code === 'Space' && !spacePressed && e.target === document.body) {
    spacePressed = true
    document.body.style.cursor = 'grab'
    e.preventDefault()
  }
}

const onKeyUp = (e: KeyboardEvent) => {
  if (e.code === 'Space') {
    spacePressed = false
    document.body.style.cursor = ''
  }
}
</script>

<style lang="scss" scoped>
.dashboard-editor {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f4f4f5;
}

/* 工具栏 */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #e4e4e7;
  flex-shrink: 0;
  z-index: 10;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-title {
  font-size: 16px;
  font-weight: 700;
  color: #18181b;
  margin: 0;
}

.toolbar-hint {
  font-size: 12px;
  color: #a1a1aa;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;

  :deep(.el-button) {
    border-radius: 8px;
    font-weight: 500;
  }
}

.toolbar-center {
  display: flex;
  align-items: center;
  gap: 4px;
}

.zoom-label {
  font-size: 12px;
  font-weight: 600;
  color: #71717a;
  min-width: 40px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

/* 画布 */
.canvas-wrapper {
  flex: 1;
  overflow: hidden;
  cursor: grab;
  position: relative;
  background: #fafafa;
  background-image:
    radial-gradient(circle, #e4e4e7 1px, transparent 1px);
  background-size: 20px 20px;

  &:active {
    cursor: grabbing;
  }
}

.canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 4000px;
  height: 3000px;
}

.empty-canvas {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  color: #a1a1aa;
  pointer-events: none;

  p {
    margin: 12px 0 0;
    font-size: 14px;
  }
}

/* 图表卡片 */
.chart-card {
  position: absolute;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e4e4e7;
  overflow: hidden;
  transition:
    box-shadow 0.2s,
    border-color 0.2s;
  will-change: transform;

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

/* 缩放手柄 */
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

/* 图表选择弹窗 */
.chart-picker-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  max-height: 400px;
  overflow-y: auto;
}

.picker-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid #e4e4e7;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #18181b;
    background: #fafafa;
  }

  &--added {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.picker-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #f4f4f5;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #71717a;
  flex-shrink: 0;
}

.picker-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.picker-name {
  font-size: 13px;
  font-weight: 600;
  color: #18181b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picker-type {
  font-size: 11px;
  color: #a1a1aa;
}
</style>
