<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <div class="eyebrow">My Charts</div>
        <h2>我的图表</h2>
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
          <el-button type="primary" @click="handleSearch">搜索</el-button>
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
                  <el-button link type="danger" size="small" @click="handleDelete(chart)"
                    >删除</el-button
                  >
                </div>
              </div>
            </template>
            <div :id="`chart-${chart.id}`" class="chart-canvas"></div>
            <div class="chart-footer">
              <el-tag :type="chart.genResult ? 'success' : 'warning'" size="small">
                {{ chart.genResult ? '已完成' : '生成中' }}
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMyChartVoByPage, deleteChart } from '@/api/chartController'
import * as echarts from 'echarts'

const chartTypeTagMap: Record<string, string> = {
  折线图: 'primary',
  柱状图: 'success',
  饼图: 'warning',
  散点图: 'danger',
  雷达图: 'info',
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

// 渲染图表
const renderChart = (chart: API.ChartVO) => {
  if (!chart.genChart || !chart.id) return
  const chartDom = document.getElementById(`chart-${chart.id}`)
  if (!chartDom) return

  try {
    const existingInstance = echarts.getInstanceByDom(chartDom)
    if (existingInstance) existingInstance.dispose()

    let option
    try {
      option = JSON.parse(chart.genChart)
    } catch {
      option = new Function('return ' + chart.genChart)()
    }

    const myChart = echarts.init(chartDom)
    myChart.setOption(option)

    const handler = () => myChart.resize()
    window.addEventListener('resize', handler)
    resizeHandlers.value.set(String(chart.id), handler)
  } catch (error) {
    console.error(`渲染图表 ${chart.id} 失败:`, error)
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
      await nextTick()
      tableData.value.forEach((chart) => observeChart(chart))
    }
  } catch (error: unknown) {
    ElMessage.error(`加载图表列表失败：${error instanceof Error ? error.message : '未知错误'}`)
  } finally {
    loading.value = false
  }
}

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

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadChartList()
}

const handleView = (row: API.ChartVO) => {
  ElMessage.info(`查看图表：${row.id}`)
}

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
  chartObserver.value?.disconnect()
  resizeHandlers.value.forEach((handler) => window.removeEventListener('resize', handler))
  resizeHandlers.value.clear()
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
  .eyebrow {
    font-size: 12px;
    font-weight: 600;
    color: #10b981;
    text-transform: uppercase;
    letter-spacing: 0.1em;
    margin-bottom: 4px;
  }

  h2 {
    font-size: 24px;
    font-weight: 700;
    color: #1f2937;
    margin: 0;
  }
}

.filter-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);

  :deep(.el-card__body) {
    padding: 20px;
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
    font-weight: 500;
    color: #374151;
  }
}

.table-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);

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
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
  padding: 20px;
}

.chart-item-card {
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  transition: all 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }

  :deep(.el-card__header) {
    padding: 12px 16px;
    border-bottom: 1px solid #f3f4f6;
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
  color: #1f2937;
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

.chart-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f3f4f6;
}

.chart-time {
  font-size: 12px;
  color: #9ca3af;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 20px;
  border-top: 1px solid #f3f4f6;

  :deep(.el-pagination) {
    --el-pagination-hover-color: #10b981;
  }

  :deep(.el-pager li.is-active) {
    background-color: #10b981;
  }
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
