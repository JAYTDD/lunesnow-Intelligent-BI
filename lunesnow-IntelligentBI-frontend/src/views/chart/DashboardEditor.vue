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

        <!-- 可拖拽 + 可缩放的图表卡片（由 ChartCard 自行处理拖拽/缩放/渲染） -->
        <ChartCard
          v-for="item in dashboardCharts"
          :key="item.id"
          :item="item"
          :zoom="canvasZoom"
          @update:position="(x, y) => onUpdatePosition(item.id, x, y)"
          @update:size="(w, h) => onUpdateSize(item.id, w, h)"
          @remove="removeChart"
        />
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
import { ref, onMounted, onUnmounted } from 'vue'
import {
  Plus,
  DataBoard,
  PieChart,
  FullScreen,
  Aim,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listMyChartVoByPage } from '@/api/chartController'
import type { DashboardItem } from '@/types/dashboard'
import ChartCard from '@/components/ChartCard.vue'

const STORAGE_KEY = 'dashboard_layout'

const canvasWrapperRef = ref<HTMLDivElement>()
const showChartPicker = ref(false)
const loadingCharts = ref(false)
const myCharts = ref<API.ChartVO[]>([])
const dashboardCharts = ref<DashboardItem[]>([])

// 画布缩放和平移状态
const canvasZoom = ref(1)
const canvasOffset = ref({ x: 0, y: 0 })
const canvasPanState = {
  isPanning: false,
  startX: 0,
  startY: 0,
  startOffsetX: 0,
  startOffsetY: 0,
}

// ==================== 卡片事件（来自 ChartCard） ====================
const onUpdatePosition = (id: string, x: number, y: number) => {
  const item = dashboardCharts.value.find((d) => d.id === id)
  if (item) {
    item.x = x
    item.y = y
    saveLayout()
  }
}

const onUpdateSize = (id: string, w: number, h: number) => {
  const item = dashboardCharts.value.find((d) => d.id === id)
  if (item) {
    item.width = w
    item.height = h
    saveLayout()
  }
}

const removeChart = (id: string) => {
  dashboardCharts.value = dashboardCharts.value.filter((d) => d.id !== id)
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
  }

  dashboardCharts.value.push(newItem)
  saveLayout()
  ElMessage.success(`已添加: ${chart.name}`)
}

// 清空所有
const clearAll = () => {
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
  } catch {
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

const loadLayout = () => {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return

  try {
    const data = JSON.parse(raw) as Partial<DashboardItem>[]
    dashboardCharts.value = data.map((d) => ({
      ...d,
      width: d.width || 340,
      height: d.height || 280,
    })) as DashboardItem[]
    // 图表渲染由 ChartCard 在挂载时自行完成
  } catch (e) {
    console.error('布局加载失败', e)
  }
}

// ==================== 生命周期 ====================
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

onMounted(() => {
  window.addEventListener('keydown', onKeyDown)
  window.addEventListener('keyup', onKeyUp)
  loadLayout()
  loadMyCharts()
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('keyup', onKeyUp)
  document.removeEventListener('mousemove', onCanvasPanMove)
  document.removeEventListener('mouseup', onCanvasPanEnd)
})
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
