<template>
  <div class="page-shell">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">我的图表</h1>
        <p class="page-desc">管理和查看你生成的所有图表</p>
      </div>
    </div>

    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" class="filter-form">
        <el-form-item label="图表名称">
          <el-input
            v-model="searchForm.name"
            placeholder="请输入图表名称"
            clearable
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="图表类型">
          <el-cascader
            v-model="chartTypeValue"
            :options="chartTypeOptions"
            :props="{ emitPath: false }"
            placeholder="全部类型"
            clearable
            @change="handleSearch"
          />
        </el-form-item>
        <el-form-item label="排序字段">
          <el-cascader
            v-model="sortFieldValue"
            :options="sortFieldOptions"
            :props="{ emitPath: false }"
            @change="handleSearch"
          />
        </el-form-item>
        <el-form-item label="排序方式">
          <el-cascader
            v-model="sortOrderValue"
            :options="sortOrderOptions"
            :props="{ emitPath: false }"
            @change="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div v-loading="loading" class="charts-container">
        <div v-if="tableData.length === 0 && !loading" class="empty-state">
          <el-empty description="暂无图表数据" />
        </div>
        <div v-else class="charts-grid">
          <el-card
            v-for="chart in tableData"
            :key="chart.id"
            class="chart-item-card"
            shadow="hover"
          >
            <template #header>
              <div class="chart-header">
                <div class="chart-info">
                  <span class="chart-name">{{ chart.name || '未命名图表' }}</span>
                  <el-tag :type="chartTypeTagMap[chart.chartType || ''] || 'info'" size="small">
                    {{ chart.chartType }}
                  </el-tag>
                </div>
                <div class="chart-actions">
                  <el-button link type="primary" size="small" @click="handleView(chart)"
                    >查看</el-button
                  >
                  <el-button
                    v-if="chart.status === 'failed'"
                    link
                    type="warning"
                    size="small"
                    @click="handleRetry(chart)"
                    >重新生成</el-button
                  >
                  <el-button link type="danger" size="small" @click="handleDelete(chart)"
                    >删除</el-button
                  >
                </div>
              </div>
            </template>
            <!-- 生成中/排队中 显示加载动画 -->
            <div
              v-if="chart.status === 'waiting' || chart.status === 'running'"
              class="chart-loading"
            >
              <el-icon class="loading-icon" :size="48"><Loading /></el-icon>
              <span class="loading-text">{{ statusTextMap[chart.status || ''] || '处理中' }}</span>
              <span class="loading-sub">AI 正在生成图表，请稍候...</span>
            </div>
            <!-- 已完成 显示图表 -->
            <div
              v-else-if="chart.status === 'succeed'"
              :id="`chart-${chart.id}`"
              class="chart-canvas"
            ></div>
            <!-- 失败 显示错误信息 -->
            <div v-else class="chart-failed">
              <el-icon :size="48" color="#f56c6c"><CircleCloseFilled /></el-icon>
              <span class="failed-text">生成失败</span>
              <span class="failed-sub">{{ chart.execMessage || '未知错误' }}</span>
            </div>
            <div class="chart-footer">
              <el-tag :type="statusTagMap[chart.status || ''] || 'info'" size="small">
                {{ statusTextMap[chart.status || ''] || chart.status }}
              </el-tag>
              <span class="chart-time">{{ formatTime(chart.createTime) }}</span>
            </div>
          </el-card>
        </div>
      </div>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          background
          @size-change="handleSizeChange"
          @current-change="loadChartList"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, CircleCloseFilled, Search } from '@element-plus/icons-vue'
import {
  listMyChartVoByPage,
  deleteChart,
  retryChartGen,
  getChartStatus,
} from '@/api/chartController'
import { safeRenderChart } from '@/utils/chartValidator'
import { usePolling } from '@/composables/usePolling'
import * as echarts from 'echarts'

const router = useRouter()

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

const searchForm = reactive<API.ChartQueryRequest>({
  name: '',
  chartType: '',
  sortField: 'createTime',
  sortOrder: 'descend',
})

const chartTypeValue = ref('')
const sortFieldValue = ref('createTime')
const sortOrderValue = ref('descend')

const chartTypeOptions = [
  { value: '折线图', label: '折线图' },
  { value: '柱状图', label: '柱状图' },
  { value: '饼图', label: '饼图' },
  { value: '散点图', label: '散点图' },
  { value: '雷达图', label: '雷达图' },
]

const sortFieldOptions = [
  { value: 'createTime', label: '创建时间' },
  { value: 'updateTime', label: '更新时间' },
]

const sortOrderOptions = [
  { value: 'descend', label: '降序' },
  { value: 'ascend', label: '升序' },
]

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const tableData = ref<API.ChartVO[]>([])
const loading = ref(false)

const chartObserver = ref<IntersectionObserver | null>(null)
const resizeHandlers = ref<Map<string, () => void>>(new Map())

// 释放所有图表
const disposeAllCharts = () => {
  tableData.value.forEach((chart) => {
    if (!chart.id) return
    const el = document.getElementById(`chart-${chart.id}`)
    if (el) {
      const instance = echarts.getInstanceByDom(el)
      if (instance) instance.dispose()
    }
    const handler = resizeHandlers.value.get(String(chart.id))
    if (handler) {
      window.removeEventListener('resize', handler)
      resizeHandlers.value.delete(String(chart.id))
    }
  })
}

// 渲染图表（带校验）
const renderChart = (chart: API.ChartVO) => {
  if (!chart.genChart || !chart.id) return
  const chartDom = document.getElementById(`chart-${chart.id}`)
  if (!chartDom) return

  // 先清理旧实例
  const existingInstance = echarts.getInstanceByDom(chartDom)
  if (existingInstance) existingInstance.dispose()

  // 安全渲染（解析 + 校验 + 渲染）
  const { success, error } = safeRenderChart(chart.genChart, (option) => {
    const myChart = echarts.init(chartDom)
    myChart.setOption(option)

    const handler = () => myChart.resize()
    window.addEventListener('resize', handler)
    resizeHandlers.value.set(String(chart.id), handler)
  })

  // 渲染失败
  if (!success) {
    console.error(`图表 ${chart.id} 渲染失败:`, error)
  }
}

// 观察图表
const observeChart = (chart: API.ChartVO) => {
  if (!chart.genChart || !chart.id) return
  const el = document.getElementById(`chart-${chart.id}`)
  if (!el) return
  if (echarts.getInstanceByDom(el)) return
  chartObserver.value?.observe(el)
}

// 初始化图表观察者
const initObserver = () => {
  chartObserver.value = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const chartId = entry.target.id.replace('chart-', '')
          const chart = tableData.value.find((c) => String(c.id) === chartId)
          if (chart) renderChart(chart)
          chartObserver.value?.unobserve(entry.target)
        }
      })
    },
    { rootMargin: '200px' },
  )
}

// 轮询回调
const pollCallback = async (): Promise<boolean> => {
  // 查找所有 waiting/running 的图表
  const pendingCharts = tableData.value.filter(
    (c) => c.status === 'waiting' || c.status === 'running',
  )
  if (pendingCharts.length === 0) {
    return true // 停止轮询
  }
  // 逐个查询状态
  for (const chart of pendingCharts) {
    if (!chart.id) continue
    try {
      const res = await getChartStatus({ id: chart.id })
      if (res.data) {
        chart.status = res.data.status
        chart.genChart = res.data.genChart
        chart.genResult = res.data.genResult
        chart.execMessage = res.data.execMessage
        // 如果有新结果，重新渲染图表
        if (res.data.status === 'succeed' && res.data.genChart) {
          await nextTick()
          renderChart(chart)
        }
      }
    } catch {
      // 静默处理
    }
  }
  return false // 继续轮询
}

// 使用轮询 Hook（指数退避 + Page Visibility）
const { start: startPolling, stop: stopPolling } = usePolling(pollCallback, {
  interval: 3000, // 初始 3 秒
  maxInterval: 30000, // 最大 30 秒
  backoff: 1.5, // 每次 *1.5
})

// 加载图表列表
const loadChartList = async () => {
  loading.value = true
  disposeAllCharts()
  try {
    const query: API.ChartQueryRequest = {
      current: currentPage.value,
      pageSize: pageSize.value,
      name: searchForm.name,
      chartType: searchForm.chartType,
      sortField: searchForm.sortField,
      sortOrder: searchForm.sortOrder,
    }
    const res = await listMyChartVoByPage(query)
    if (res.data?.records) {
      tableData.value = res.data.records
      total.value = Number(res.data.total) || 0
      await nextTick() // 等待DOM更新
      tableData.value.forEach((chart) => observeChart(chart)) // 观察所有图表
      // 加载完后启动轮询
      startPolling()
    }
  } catch (error: unknown) {
    ElMessage.error(`加载图表列表失败：${error instanceof Error ? error.message : '未知错误'}`)
  } finally {
    loading.value = false
  }
}

// 重新生成
const handleRetry = async (row: API.ChartVO) => {
  if (!row.id) return
  try {
    await ElMessageBox.confirm('确定要重新生成该图表吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await retryChartGen({ id: row.id })
    ElMessage.success('已重新提交任务')
    loadChartList()
  } catch (error: unknown) {
    if (error !== 'cancel') {
      ElMessage.error(`重试失败：${error instanceof Error ? error.message : '未知错误'}`)
    }
  }
}

// 时间格式化
const formatTime = (time?: string) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const handleSearch = () => {
  searchForm.chartType = chartTypeValue.value
  searchForm.sortField = sortFieldValue.value
  searchForm.sortOrder = sortOrderValue.value
  currentPage.value = 1
  loadChartList()
}

const handleReset = () => {
  searchForm.name = ''
  searchForm.chartType = ''
  searchForm.sortField = 'createTime'
  searchForm.sortOrder = 'descend'
  chartTypeValue.value = ''
  sortFieldValue.value = 'createTime'
  sortOrderValue.value = 'descend'
  currentPage.value = 1
  loadChartList()
}

// 分页大小改变
const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadChartList()
}

// 查看图表
const handleView = (row: API.ChartVO) => {
  if (row.id) {
    router.push(`/chart/detail/${row.id}`)
  }
}

// 删除图表
const handleDelete = async (row: API.ChartVO) => {
  if (!row.id) return
  try {
    await ElMessageBox.confirm('确定要删除该图表吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteChart({ id: row.id })
    ElMessage.success('删除成功！')
    loadChartList()
  } catch (error: unknown) {
    if (error !== 'cancel') {
      ElMessage.error(`删除失败：${error instanceof Error ? error.message : '未知错误'}`)
    }
  }
}

onMounted(() => {
  initObserver()
  loadChartList()
})

onUnmounted(() => {
  stopPolling()
  chartObserver.value?.disconnect() // 取消图表观察者
  resizeHandlers.value.forEach((handler) => window.removeEventListener('resize', handler)) // 取消窗口大小监听
  resizeHandlers.value.clear() // 清空监听器
})
</script>

<style lang="scss" scoped>
.page-shell {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 24px;
  min-height: calc(100vh - 120px);
}

.page-header {
  margin-bottom: 28px;
}

.header-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #18181b;
  margin: 0;
  letter-spacing: -0.5px;
}

.page-desc {
  font-size: 14px;
  color: #71717a;
  margin: 0;
}

.filter-card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #e4e4e7;
  margin-bottom: 20px;

  :deep(.el-card__body) {
    padding: 20px 24px;
  }
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: flex-end;

  :deep(.el-form-item) {
    margin-bottom: 0;
  }

  :deep(.el-form-item__label) {
    font-weight: 600;
    color: #3f3f46;
    font-size: 13px;
  }

  :deep(.el-input__wrapper),
  :deep(.el-cascader__search-input) {
    border-radius: 8px;
    box-shadow: 0 0 0 1px #e4e4e7;

    &:hover {
      box-shadow: 0 0 0 1px #d4d4d8;
    }

    &.is-focus {
      box-shadow: 0 0 0 2px #18181b;
    }
  }
}

.table-card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #e4e4e7;

  :deep(.el-card__body) {
    padding: 0;
  }
}

.charts-container {
  min-height: 200px;
}

.empty-state {
  padding: 60px 0;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  padding: 20px;
}

.chart-item-card {
  border-radius: 12px;
  border: 1px solid #e4e4e7;
  transition: all 0.2s;

  &:hover {
    border-color: transparent;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
  }

  :deep(.el-card__header) {
    padding: 14px 16px;
    border-bottom: 1px solid #f4f4f5;
  }

  :deep(.el-card__body) {
    padding: 16px;
  }
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.chart-name {
  font-size: 14px;
  font-weight: 600;
  color: #18181b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chart-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.chart-canvas {
  width: 100%;
  height: 240px;
}

.chart-loading {
  width: 100%;
  height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.05) 0%, rgba(52, 211, 153, 0.05) 100%);
  border-radius: 8px;

  .loading-icon {
    color: #10b981;
    animation: spin 1.5s linear infinite;
  }

  .loading-text {
    font-size: 18px;
    font-weight: 700;
    color: #18181b;
  }

  .loading-sub {
    font-size: 13px;
    color: #71717a;
  }
}

.chart-failed {
  width: 100%;
  height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.05) 0%, rgba(239, 68, 68, 0.05) 100%);
  border-radius: 8px;

  .failed-text {
    font-size: 16px;
    font-weight: 600;
    color: #ef4444;
  }

  .failed-sub {
    font-size: 12px;
    color: #71717a;
    max-width: 200px;
    text-align: center;
    word-break: break-all;
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.chart-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f4f4f5;
}

.chart-time {
  font-size: 12px;
  color: #71717a;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 20px;
  border-top: 1px solid #f4f4f5;
}

@media (max-width: 768px) {
  .page-shell {
    padding: 16px;
  }

  .charts-grid {
    grid-template-columns: 1fr;
    padding: 16px;
  }
}
</style>
