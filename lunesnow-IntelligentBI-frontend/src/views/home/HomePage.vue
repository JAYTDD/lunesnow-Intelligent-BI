<template>
  <div class="home-page">
    <!-- 顶部欢迎区 - 左对齐 -->
    <section class="hero">
      <div class="hero-content">
        <div class="hero-badge">
          <span class="badge-dot"></span>
          数据看板
        </div>
        <h1 class="hero-title">
          你好，{{ loginUserStore.loginUser.userName || '用户' }}
        </h1>
        <p class="hero-desc">管理和追踪你的 AI 图表生成任务</p>
      </div>
      <button class="hero-btn" @click="router.push('/add/chart')">
        <span class="btn-text">新建图表</span>
        <span class="btn-icon">
          <el-icon :size="16"><Plus /></el-icon>
        </span>
      </button>
    </section>

    <!-- 统计数据 - 不对称布局 -->
    <section class="stats-section">
      <div class="stats-grid">
        <!-- 大卡片：总数 -->
        <div class="stat-card stat-card--large">
          <div class="stat-card__header">
            <span class="stat-card__label">图表总数</span>
            <span class="stat-card__icon stat-card__icon--blue">
              <el-icon :size="18"><DataBoard /></el-icon>
            </span>
          </div>
          <div class="stat-card__value">{{ animatedTotal }}</div>
          <div class="stat-card__footer">
            <span class="stat-card__trend stat-card__trend--up">
              <el-icon :size="12"><Top /></el-icon>
              活跃
            </span>
            <span class="stat-card__sub">所有状态</span>
          </div>
        </div>

        <!-- 右侧小卡片组 -->
        <div class="stat-side">
          <div class="stat-card stat-card--small">
            <div class="stat-card__header">
              <span class="stat-card__label">成功</span>
              <span class="stat-card__icon stat-card__icon--green">
                <el-icon :size="14"><CircleCheck /></el-icon>
              </span>
            </div>
            <div class="stat-card__value stat-card__value--small">{{ animatedSuccess }}</div>
          </div>

          <div class="stat-card stat-card--small">
            <div class="stat-card__header">
              <span class="stat-card__label">进行中</span>
              <span class="stat-card__icon stat-card__icon--amber">
                <el-icon :size="14"><Loading /></el-icon>
              </span>
            </div>
            <div class="stat-card__value stat-card__value--small">{{ animatedRunning }}</div>
          </div>

          <div class="stat-card stat-card--small">
            <div class="stat-card__header">
              <span class="stat-card__label">成功率</span>
              <span class="stat-card__icon stat-card__icon--emerald">
                <el-icon :size="14"><TrendCharts /></el-icon>
              </span>
            </div>
            <div class="stat-card__value stat-card__value--small">{{ animatedRate }}%</div>
          </div>
        </div>
      </div>
    </section>

    <!-- 最近图表 - Bento 风格 -->
    <section class="recent-section">
      <div class="section-header">
        <h2 class="section-title">最近生成</h2>
        <button class="section-link" @click="router.push('/my/charts')">
          查看全部
          <el-icon :size="14"><ArrowRight /></el-icon>
        </button>
      </div>

      <div v-if="loading" class="skeleton-grid">
        <div v-for="i in 5" :key="i" class="skeleton-card">
          <div class="skeleton-chart"></div>
          <div class="skeleton-info">
            <div class="skeleton-line skeleton-line--long"></div>
            <div class="skeleton-line skeleton-line--short"></div>
          </div>
        </div>
      </div>

      <div v-else-if="recentCharts.length === 0" class="empty-state">
        <div class="empty-icon">
          <el-icon :size="48" color="#d1d5db"><PieChart /></el-icon>
        </div>
        <p class="empty-title">还没有图表</p>
        <p class="empty-desc">上传数据文件，让 AI 为你生成可视化图表</p>
        <button class="empty-btn" @click="router.push('/add/chart')">
          创建第一个图表
        </button>
      </div>

      <div v-else class="charts-grid">
        <div
          v-for="(chart, index) in recentCharts"
          :key="chart.id"
          class="chart-card"
          :style="{ '--delay': `${index * 60}ms` }"
          @click="router.push(`/chart/detail/${chart.id}`)"
        >
          <div class="chart-card__visual">
            <div
              v-if="chart.status === 'succeed'"
              :id="`recent-chart-${chart.id}`"
              class="mini-chart"
            ></div>
            <div v-else-if="chart.status === 'waiting' || chart.status === 'running'" class="chart-status">
              <span class="status-pulse status-pulse--amber"></span>
              <span class="status-text">生成中</span>
            </div>
            <div v-else class="chart-status">
              <span class="status-dot status-dot--red"></span>
              <span class="status-text">失败</span>
            </div>
          </div>

          <div class="chart-card__content">
            <h3 class="chart-card__name">{{ chart.name || '未命名图表' }}</h3>
            <div class="chart-card__meta">
              <span class="chart-type">{{ chart.chartType }}</span>
              <span class="chart-time">{{ formatTime(chart.createTime) }}</span>
            </div>
          </div>

          <!-- 悬浮指示器 -->
          <div class="chart-card__hover"></div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Plus,
  DataBoard,
  CircleCheck,
  Loading,
  TrendCharts,
  ArrowRight,
  Top,
  PieChart,
} from '@element-plus/icons-vue'
import { getStatistics } from '@/api/chartController'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { safeRenderChart } from '@/utils/chartValidator'
import * as echarts from 'echarts'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const loading = ref(true)
const statistics = ref<API.ChartStatisticsVO>({})
const recentCharts = ref<API.ChartVO[]>([])

// 数字动画
const animatedTotal = ref(0)
const animatedSuccess = ref(0)
const animatedRunning = ref(0)
const animatedRate = ref(0)

const animateNumber = (target: number, setter: (val: number) => void, duration = 800) => {
  const start = 0
  const startTime = Date.now()
  const animate = () => {
    const elapsed = Date.now() - startTime
    const progress = Math.min(elapsed / duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3)
    setter(Math.round(start + (target - start) * eased))
    if (progress < 1) requestAnimationFrame(animate)
  }
  animate()
}

const formatTime = (time?: string) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 60 * 60 * 1000) {
    const minutes = Math.floor(diff / (60 * 1000))
    return `${minutes || 1}分钟前`
  }
  if (diff < 24 * 60 * 60 * 1000) {
    const hours = Math.floor(diff / (60 * 60 * 1000))
    return `${hours}小时前`
  }
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

const renderMiniCharts = () => {
  recentCharts.value.forEach((chart) => {
    if (chart.status !== 'succeed' || !chart.genChart) return
    nextTick(() => {
      const chartDom = document.getElementById(`recent-chart-${chart.id}`)
      if (!chartDom) return
      const existingInstance = echarts.getInstanceByDom(chartDom)
      if (existingInstance) existingInstance.dispose()
      safeRenderChart(chart.genChart, (option) => {
        const myChart = echarts.init(chartDom)
        myChart.setOption(option)
      })
    })
  })
}

const loadStatistics = async () => {
  loading.value = true
  try {
    const res = await getStatistics()
    if (res.data) {
      statistics.value = res.data
      recentCharts.value = res.data.recentCharts || []

      // 触发数字动画
      nextTick(() => {
        animateNumber(res.data.totalCount || 0, (v) => (animatedTotal.value = v))
        animateNumber(res.data.successCount || 0, (v) => (animatedSuccess.value = v))
        animateNumber(res.data.runningCount || 0, (v) => (animatedRunning.value = v))
        animateNumber(res.data.successRate || 0, (v) => (animatedRate.value = v))
        renderMiniCharts()
      })
    }
  } catch (error: unknown) {
    ElMessage.error(`加载失败：${error instanceof Error ? error.message : '未知错误'}`)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadStatistics()
})

onUnmounted(() => {
  // Dispose all ECharts instances attached to mini-chart DOM elements
  recentCharts.value.forEach((chart) => {
    if (chart.status !== 'succeed') return
    const chartDom = document.getElementById(`recent-chart-${chart.id}`)
    if (chartDom) {
      const instance = echarts.getInstanceByDom(chartDom)
      if (instance) instance.dispose()
    }
  })
})
</script>

<style lang="scss" scoped>
/* ========== 基础变量 ========== */
$bg-primary: #fafafa;
$bg-card: #ffffff;
$text-primary: #18181b;
$text-secondary: #71717a;
$text-tertiary: #a1a1aa;
$border-light: #e4e4e7;
$accent-blue: #3b82f6;
$accent-green: #22c55e;
$accent-amber: #f59e0b;
$accent-emerald: #10b981;
$radius-sm: 12px;
$radius-lg: 20px;
$radius-xl: 24px;

/* ========== 页面容器 ========== */
.home-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px;
  min-height: calc(100vh - 120px);
}

/* ========== Hero 区域 ========== */
.hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 40px;
  padding-bottom: 32px;
  border-bottom: 1px solid $border-light;
}

.hero-content {
  .hero-badge {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 6px 14px;
    background: rgba(16, 185, 129, 0.08);
    border-radius: 100px;
    font-size: 12px;
    font-weight: 600;
    color: $accent-emerald;
    letter-spacing: 0.5px;
    margin-bottom: 16px;
  }

  .badge-dot {
    width: 6px;
    height: 6px;
    background: $accent-emerald;
    border-radius: 50%;
    animation: pulse-dot 2s ease-in-out infinite;
  }

  .hero-title {
    font-size: 36px;
    font-weight: 700;
    color: $text-primary;
    margin: 0 0 8px 0;
    letter-spacing: -0.5px;
  }

  .hero-desc {
    font-size: 15px;
    color: $text-secondary;
    margin: 0;
  }
}

.hero-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 28px;
  background: $text-primary;
  color: #fff;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 8px 24px rgba(24, 24, 27, 0.25);
  }

  &:active {
    transform: translateY(0) scale(0.98);
  }

  .btn-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    background: rgba(255, 255, 255, 0.15);
    border-radius: 6px;
  }
}

/* ========== 统计区域 ========== */
.stats-section {
  margin-bottom: 48px;
}

.stats-grid {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 16px;
}

.stat-card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 28px;
  border: 1px solid $border-light;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.06);
  }

  &--large {
    display: flex;
    flex-direction: column;
    gap: 16px;

    .stat-card__value {
      font-size: 56px;
      font-weight: 700;
      color: $text-primary;
      letter-spacing: -2px;
      line-height: 1;
    }
  }

  &--small {
    padding: 20px 24px;

    .stat-card__value--small {
      font-size: 28px;
      font-weight: 700;
      color: $text-primary;
      letter-spacing: -1px;
    }
  }

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  &__label {
    font-size: 13px;
    font-weight: 500;
    color: $text-tertiary;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border-radius: 10px;

    &--blue {
      background: rgba(59, 130, 246, 0.1);
      color: $accent-blue;
    }

    &--green {
      background: rgba(34, 197, 94, 0.1);
      color: $accent-green;
    }

    &--amber {
      background: rgba(245, 158, 11, 0.1);
      color: $accent-amber;
    }

    &--emerald {
      background: rgba(16, 185, 129, 0.1);
      color: $accent-emerald;
    }
  }

  &__footer {
    display: flex;
    align-items: center;
    gap: 12px;
    padding-top: 12px;
    border-top: 1px solid $border-light;
  }

  &__trend {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    font-weight: 600;

    &--up {
      color: $accent-green;
    }
  }

  &__sub {
    font-size: 12px;
    color: $text-tertiary;
  }
}

.stat-side {
  display: grid;
  grid-template-rows: repeat(3, 1fr);
  gap: 16px;
}

/* ========== 最近图表区域 ========== */
.recent-section {
  margin-bottom: 32px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.section-title {
  font-size: 20px;
  font-weight: 700;
  color: $text-primary;
  margin: 0;
}

.section-link {
  display: flex;
  align-items: center;
  gap: 6px;
  background: none;
  border: none;
  font-size: 13px;
  font-weight: 600;
  color: $text-secondary;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 8px;
  transition: all 0.2s;

  &:hover {
    color: $text-primary;
    background: rgba(0, 0, 0, 0.04);
  }
}

/* ========== 图表卡片 ========== */
.charts-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

.chart-card {
  position: relative;
  background: $bg-card;
  border-radius: $radius-sm;
  border: 1px solid $border-light;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  animation: fadeInUp 0.5s ease-out backwards;
  animation-delay: var(--delay);

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.08);
    border-color: transparent;

    .chart-card__hover {
      opacity: 1;
    }
  }

  &__hover {
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg, rgba(16, 185, 129, 0.02) 0%, rgba(59, 130, 246, 0.02) 100%);
    opacity: 0;
    transition: opacity 0.3s;
    pointer-events: none;
  }

  &__visual {
    height: 130px;
    background: $bg-primary;
    display: flex;
    align-items: center;
    justify-content: center;
    border-bottom: 1px solid $border-light;

    .mini-chart {
      width: 100%;
      height: 100%;
    }
  }

  &__content {
    padding: 16px;
  }

  &__name {
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
    margin: 0 0 8px 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
}

.chart-type {
  font-size: 11px;
  font-weight: 500;
  color: $accent-blue;
  background: rgba(59, 130, 246, 0.08);
  padding: 3px 8px;
  border-radius: 4px;
}

.chart-time {
  font-size: 11px;
  color: $text-tertiary;
}

/* ========== 状态指示器 ========== */
.chart-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.status-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;

  &--amber {
    background: $accent-amber;
    animation: pulse 2s ease-in-out infinite;
  }
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;

  &--red {
    background: #ef4444;
  }
}

.status-text {
  font-size: 12px;
  color: $text-tertiary;
  font-weight: 500;
}

/* ========== 骨架屏 ========== */
.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

.skeleton-card {
  background: $bg-card;
  border-radius: $radius-sm;
  border: 1px solid $border-light;
  overflow: hidden;
}

.skeleton-chart {
  height: 130px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-info {
  padding: 16px;
}

.skeleton-line {
  height: 12px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 4px;
  margin-bottom: 8px;

  &--long {
    width: 80%;
  }

  &--short {
    width: 50%;
  }
}

/* ========== 空状态 ========== */
.empty-state {
  text-align: center;
  padding: 64px 24px;
  background: $bg-card;
  border-radius: $radius-lg;
  border: 1px solid $border-light;
}

.empty-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;
  background: $bg-primary;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  margin: 0 0 8px 0;
}

.empty-desc {
  font-size: 14px;
  color: $text-secondary;
  margin: 0 0 24px 0;
}

.empty-btn {
  padding: 12px 24px;
  background: $text-primary;
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(24, 24, 27, 0.2);
  }
}

/* ========== 动画 ========== */
@keyframes pulse-dot {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.4;
  }
}

@keyframes pulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.5);
    opacity: 0.5;
  }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

/* ========== 响应式 ========== */
@media (max-width: 1024px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .stat-side {
    grid-template-rows: none;
    grid-template-columns: repeat(3, 1fr);
  }

  .charts-grid,
  .skeleton-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .home-page {
    padding: 24px 16px;
  }

  .hero {
    flex-direction: column;
    align-items: flex-start;
    gap: 20px;
  }

  .hero-title {
    font-size: 28px !important;
  }

  .stat-side {
    grid-template-columns: 1fr;
  }

  .stat-card--large .stat-card__value {
    font-size: 40px !important;
  }

  .charts-grid,
  .skeleton-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .charts-grid,
  .skeleton-grid {
    grid-template-columns: 1fr;
  }
}
</style>
