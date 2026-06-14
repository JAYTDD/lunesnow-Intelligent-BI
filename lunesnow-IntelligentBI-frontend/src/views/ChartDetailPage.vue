<template>
  <div class="page-shell">
    <div class="page-header">
      <el-button link @click="router.back()">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <h2>图表详情</h2>
    </div>

    <div v-loading="loading">
      <!-- 基础信息 -->
      <el-card class="info-card" shadow="never">
        <div class="info-row">
          <div class="info-item">
            <span class="info-label">图表名称</span>
            <span class="info-value">{{ chart.name || '未命名' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">图表类型</span>
            <el-tag :type="chartTypeTagMap[chart.chartType || ''] || 'info'" size="small">
              {{ chart.chartType }}
            </el-tag>
          </div>
          <div class="info-item">
            <span class="info-label">状态</span>
            <el-tag :type="statusTagMap[chart.status || ''] || 'info'" size="small">
              {{ statusTextMap[chart.status || ''] || chart.status }}
            </el-tag>
          </div>
          <div class="info-item">
            <span class="info-label">创建时间</span>
            <span class="info-value">{{ formatTime(chart.createTime) }}</span>
          </div>
        </div>
      </el-card>

      <!-- 图表区域 -->
      <el-card class="chart-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>图表展示</span>
          </div>
        </template>
        <!-- 生成中 -->
        <div v-if="chart.status === 'waiting' || chart.status === 'running'" class="chart-loading">
          <el-icon class="loading-icon" :size="48"><Loading /></el-icon>
          <span class="loading-text">{{ statusTextMap[chart.status || ''] || '处理中' }}</span>
          <span class="loading-sub">AI 正在生成图表，请稍候...</span>
        </div>
        <!-- 已完成 -->
        <div v-else-if="chart.status === 'succeed'" id="detailChart" class="chart-container"></div>
        <!-- 失败 -->
        <div v-else class="chart-failed">
          <el-icon :size="64" color="#f56c6c"><CircleCloseFilled /></el-icon>
          <span class="failed-text">生成失败</span>
          <span class="failed-sub">{{ chart.execMessage || '未知错误' }}</span>
        </div>
      </el-card>

      <!-- 分析结果 -->
      <el-card v-if="chart.genResult" class="result-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>分析结果</span>
          </div>
        </template>
        <div class="result-content">{{ chart.genResult }}</div>
      </el-card>

      <!-- 原始数据 -->
      <el-card class="data-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>原始数据</span>
          </div>
        </template>
        <el-table :data="tableData" style="width: 100%" max-height="400" stripe>
          <el-table-column
            v-for="col in tableColumns"
            :key="col"
            :prop="col"
            :label="col"
            min-width="120"
          />
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Loading, CircleCloseFilled } from '@element-plus/icons-vue'
import { getChartVoById, getChartData } from '@/api/chartController'
import { safeRenderChart } from '@/utils/chartValidator'
import * as echarts from 'echarts'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const chart = ref<API.ChartVO>({})
const tableData = ref<Record<string, string>[]>([])
const tableColumns = ref<string[]>([])

const chartTypeTagMap: Record<string, string> = {
  折线图: 'primary',
  柱状图: 'success',
  饼图: 'warning',
  散点图: 'danger',
  雷达图: 'info',
}

const statusTextMap: Record<string, string> = {
  waiting: '排队中',
  running: '生成中',
  succeed: '已完成',
  failed: '生成失败',
}

const statusTagMap: Record<string, string> = {
  waiting: 'warning',
  running: '',
  succeed: 'success',
  failed: 'danger',
}

const formatTime = (time?: string) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

// 渲染图表（带校验）
const renderChart = () => {
  if (chart.value.status !== 'succeed' || !chart.value.genChart) return

  nextTick(() => {
    const chartDom = document.getElementById('detailChart')
    if (!chartDom) return

    // 先清理旧实例
    const existingInstance = echarts.getInstanceByDom(chartDom)
    if (existingInstance) existingInstance.dispose()

    // 安全渲染（解析 + 校验 + 渲染）
    const { success, error } = safeRenderChart(chart.value.genChart, (option) => {
      const myChart = echarts.init(chartDom)
      myChart.setOption(option)

      const handler = () => myChart.resize()
      window.addEventListener('resize', handler)
    })

    if (!success) {
      ElMessage.error(`图表渲染失败: ${error}`)
    }
  })
}

// 加载图表数据
const loadChartData = async () => {
  const id = route.params.id as number
  if (!id) {
    ElMessage.error('图表ID无效')
    router.back()
    return
  }

  loading.value = true
  try {
    // 获取图表信息
    const chartRes = await getChartVoById({ id })
    if (chartRes.data) {
      chart.value = chartRes.data
      // 成功后渲染图表
      if (chart.value.status === 'succeed') {
        renderChart()
      }
    }

    // 获取原始数据
    const dataRes = await getChartData({ chartId: id })
    console.log('@@@@', dataRes)
    if (dataRes.data && dataRes.data.length > 0) {
      tableData.value = dataRes.data
      tableColumns.value = Object.keys(dataRes.data[0])
    }
  } catch (error: unknown) {
    ElMessage.error(`加载失败：${error instanceof Error ? error.message : '未知错误'}`)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadChartData()
})
</script>

<style lang="scss" scoped>
.page-shell {
  min-height: calc(100vh - 120px);
  background: #f5f7fa;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;

  .el-button {
    font-size: 14px;
  }

  h2 {
    font-size: 24px;
    font-weight: 700;
    color: #1f2937;
    margin: 0;
  }
}

.info-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);

  .info-row {
    display: flex;
    flex-wrap: wrap;
    gap: 32px;
  }

  .info-item {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .info-label {
    font-size: 12px;
    color: #9ca3af;
    font-weight: 500;
  }

  .info-value {
    font-size: 14px;
    color: #1f2937;
    font-weight: 600;
  }
}

.chart-card,
.result-card,
.data-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.card-header {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.chart-container {
  width: 100%;
  height: 400px;
}

.chart-loading {
  width: 100%;
  height: 400px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.05) 0%, rgba(52, 211, 153, 0.05) 100%);
  border-radius: 8px;

  .loading-icon {
    color: #10b981;
    animation: spin 1.5s linear infinite;
  }

  .loading-text {
    font-size: 20px;
    font-weight: 700;
    color: #1a1a2e;
  }

  .loading-sub {
    font-size: 14px;
    color: #9ca3af;
  }
}

.chart-failed {
  width: 100%;
  height: 400px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: linear-gradient(135deg, rgba(245, 108, 108, 0.05) 0%, rgba(245, 108, 108, 0.05) 100%);
  border-radius: 8px;

  .failed-text {
    font-size: 18px;
    font-weight: 600;
    color: #f56c6c;
  }

  .failed-sub {
    font-size: 13px;
    color: #999;
    max-width: 300px;
    text-align: center;
    word-break: break-all;
  }
}

.result-content {
  font-size: 14px;
  line-height: 1.8;
  color: #4b5563;
  white-space: pre-wrap;
  word-break: break-word;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
