<template>
  <div class="page-shell">
    <!-- 组件错误兜底 -->
    <div v-if="componentError" class="error-fallback">
      <el-icon :size="64" color="#f56c6c"><CircleCloseFilled /></el-icon>
      <h3>页面渲染出错</h3>
      <p class="error-message">{{ componentError.message }}</p>
      <el-button type="primary" @click="handlePageRetry">
        <el-icon><Refresh /></el-icon> 重新加载
      </el-button>
    </div>

    <template v-else>
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
              <div v-if="chart.status === 'succeed'" class="card-actions">
                <el-button size="small" @click="showFilter = !showFilter">
                  <el-icon><Filter /></el-icon> 筛选
                </el-button>
                <el-button v-if="hasActiveFilters" size="small" type="info" plain @click="clearFilters">
                  清除筛选
                </el-button>
                <el-dropdown @command="handleExport">
                  <el-button size="small">
                    <el-icon><Download /></el-icon> 导出
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="png">
                        <el-icon><Picture /></el-icon> 导出 PNG
                      </el-dropdown-item>
                      <el-dropdown-item command="svg">
                        <el-icon><Picture /></el-icon> 导出 SVG
                      </el-dropdown-item>
                      <el-dropdown-item command="json" divided>
                        <el-icon><Document /></el-icon> 导出 JSON
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-button type="primary" size="small" @click="showEditor = true">
                  <el-icon><Edit /></el-icon> 编辑配置
                </el-button>
              </div>
            </div>
          </template>

          <!-- 筛选区域 -->
          <div v-if="showFilter" class="filter-section">
            <div class="filter-grid">
              <div v-for="col in filterableColumns" :key="col" class="filter-item">
                <label class="filter-label">{{ col }}</label>

                <!-- 日期列：日期范围 -->
                <div v-if="isDateColumn(col)" class="range-inputs">
                  <el-date-picker
                    v-model="dateRangeFilters[col]"
                    type="daterange"
                    range-separator="~"
                    start-placeholder="开始日期"
                    end-placeholder="结束日期"
                    size="small"
                    value-format="YYYY-MM-DD"
                    @change="handleFilter"
                  />
                </div>

                <!-- 数值列：范围筛选 -->
                <div v-else-if="isNumericColumn(col)" class="range-inputs">
                  <el-input
                    v-model="rangeFilters[col].min"
                    placeholder="最小"
                    size="small"
                    clearable
                    @change="handleFilter"
                  />
                  <span class="range-sep">~</span>
                  <el-input
                    v-model="rangeFilters[col].max"
                    placeholder="最大"
                    size="small"
                    clearable
                    @change="handleFilter"
                  />
                </div>

                <!-- 文本列：下拉筛选 -->
                <el-select
                  v-else
                  :model-value="filters[col]"
                  placeholder="全部"
                  clearable
                  filterable
                  size="small"
                  :reserve-keyword="false"
                  @update:model-value="(val: string) => handleFilterChange(col, val)"
                >
                  <el-option
                    v-for="val in (columnValues[col] || []).slice(0, 50)"
                    :key="val"
                    :label="val"
                    :value="val"
                  />
                  <el-option v-if="(columnValues[col] || []).length > 50" disabled>
                    <span style="color: #999">仅显示前 50 项，请搜索</span>
                  </el-option>
                </el-select>
              </div>
            </div>
            <div class="filter-summary">
              共 <strong>{{ tableData.length }}</strong> 条数据
              <span v-if="hasActiveFilters">（已筛选）</span>
            </div>
          </div>

          <!-- 生成中 -->
          <div
            v-if="chart.status === 'waiting' || chart.status === 'running'"
            class="chart-loading"
          >
            <el-icon class="loading-icon" :size="48"><Loading /></el-icon>
            <span class="loading-text">{{ statusTextMap[chart.status || ''] || '处理中' }}</span>
            <span class="loading-sub">AI 正在生成图表，请稍候...</span>
          </div>
          <!-- 已完成 -->
          <div
            v-else-if="chart.status === 'succeed'"
            id="detailChart"
            class="chart-container"
          ></div>
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
              <span>原始数据（共 {{ allTableData.length }} 条）</span>
            </div>
          </template>

          <!-- 数据表格 -->
          <el-table :data="pagedTableData" style="width: 100%" stripe>
            <el-table-column
              v-for="col in tableColumns"
              :key="col"
              :prop="col"
              :label="col"
              min-width="120"
            />
          </el-table>

          <!-- 分页 -->
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="tableData.length"
              layout="total, sizes, prev, pager, next"
              background
            />
          </div>
        </el-card>
      </div>

      <!-- 图表配置编辑器 -->
      <ChartEditor
        v-model:visible="showEditor"
        :chart-id="chart.id"
        :gen-chart="chart.genChart"
        @saved="loadChartData"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, onErrorCaptured } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  Loading,
  CircleCloseFilled,
  Edit,
  Refresh,
  Download,
  Picture,
  Document,
  Filter,
} from '@element-plus/icons-vue'
import { getChartVoById, getChartData, getColumnDistinctValues } from '@/api/chartController'
import { safeRenderChart } from '@/utils/chartValidator'
import ChartEditor from '@/components/ChartEditor.vue'
import * as echarts from 'echarts'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const chart = ref<API.ChartVO>({})
const tableData = ref<Record<string, string>[]>([])
const tableColumns = ref<string[]>([])
const showEditor = ref(false)
const componentError = ref<Error | null>(null)
let chartInstance: echarts.ECharts | null = null
let resizeHandler: (() => void) | null = null

// 筛选相关
const showFilter = ref(false)
const filters = ref<Record<string, string>>({})
const rangeFilters = ref<Record<string, { min: string; max: string }>>({})
const dateRangeFilters = ref<Record<string, [string, string] | null>>({})
const columnValues = ref<Record<string, string[]>>({})
const allTableData = ref<Record<string, string>[]>([])

// 分页
const currentPage = ref(1)
const pageSize = ref(20)

// 分页数据
const pagedTableData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return tableData.value.slice(start, start + pageSize.value)
})

// 可筛选的列（全部列）
const filterableColumns = computed(() => {
  return tableColumns.value
})

// 判断是否为日期列
const isDateColumn = (col: string) => {
  if (allTableData.value.length === 0) return false
  const sample = allTableData.value.slice(0, 10)
  const datePattern = /^\d{4}[-/]\d{1,2}[-/]\d{1,2}/
  return sample.every((row) => {
    const val = row[col]
    return val === '' || val === null || datePattern.test(val)
  })
}

// 判断是否为数值列
const isNumericColumn = (col: string) => {
  if (allTableData.value.length === 0) return false
  if (isDateColumn(col)) return false // 日期列不算数值列
  const sample = allTableData.value.slice(0, 10)
  return sample.every((row) => {
    const val = row[col]
    return val === '' || val === null || !isNaN(Number(val))
  })
}

// 是否有激活的筛选
const hasActiveFilters = computed(() => {
  const hasTextFilter = Object.values(filters.value).some((v) => v && v !== '')
  const hasRangeFilter = Object.values(rangeFilters.value).some(
    (r) => (r.min && r.min !== '') || (r.max && r.max !== '')
  )
  const hasDateFilter = Object.values(dateRangeFilters.value).some(
    (r) => r && r[0] && r[1]
  )
  return hasTextFilter || hasRangeFilter || hasDateFilter
})

// 捕获子组件渲染错误
onErrorCaptured((err, instance, info) => {
  console.error('组件渲染错误:', err, info)
  componentError.value = err
  return false // 阻止错误继续向上传播
})

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
      chartInstance = echarts.init(chartDom)

      // 用筛选后的数据更新图表
      const updatedOption = updateChartWithData(option, tableData.value)
      chartInstance.setOption(updatedOption)

      // Remove previous resize listener before adding a new one
      if (resizeHandler) {
        window.removeEventListener('resize', resizeHandler)
      }
      resizeHandler = () => chartInstance?.resize()
      window.addEventListener('resize', resizeHandler)
    })

    if (!success) {
      ElMessage.error(`图表渲染失败: ${error}`)
    }
  })
}

// 用筛选后的数据更新图表配置
const updateChartWithData = (option: any, data: Record<string, string>[]) => {
  if (!data || data.length === 0) return option

  const columns = Object.keys(data[0])
  const newOption = JSON.parse(JSON.stringify(option)) // 深拷贝

  // 1. 处理 dataset.source
  if (newOption.dataset && newOption.dataset.source) {
    newOption.dataset.source = data
  }

  // 2. 处理 series[].data（根据图表类型）
  if (newOption.series && Array.isArray(newOption.series)) {
    newOption.series.forEach((series: any) => {
      if (series.type === 'pie') {
        // 饼图：取第一个和第二个列
        const nameCol = columns[0]
        const valueCol = columns[1]
        series.data = data.map((row) => ({
          name: row[nameCol] || '',
          value: Number(row[valueCol]) || 0,
        }))
      } else if (series.type === 'scatter') {
        // 散点图：取前两个数值列
        const numCols = columns.filter((col) =>
          data.every((row) => row[col] === '' || !isNaN(Number(row[col])))
        )
        if (numCols.length >= 2) {
          series.data = data.map((row) => [
            Number(row[numCols[0]]) || 0,
            Number(row[numCols[1]]) || 0,
          ])
        }
      } else {
        // 柱状图/折线图：取数值列
        const valueCol = columns.find(
          (col) => col !== newOption.xAxis?.data?.[0] && !isNaN(Number(data[0]?.[col]))
        ) || columns[columns.length - 1]
        series.data = data.map((row) => Number(row[valueCol]) || 0)
      }
    })
  }

  // 3. 更新 xAxis/yAxis 的分类数据
  if (newOption.xAxis && newOption.xAxis.type === 'category') {
    const catCol = columns[0]
    newOption.xAxis.data = data.map((row) => row[catCol] || '')
  }

  return newOption
}

// 下载文件（支持 data URL）
const downloadFile = (dataUrl: string, filename: string) => {
  const a = document.createElement('a')
  a.href = dataUrl
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

// 下载文本文件
const downloadText = (content: string, filename: string, mimeType: string) => {
  const blob = new Blob([content], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

// 导出图表
const handleExport = (command: string) => {
  if (!chartInstance) {
    ElMessage.error('图表未渲染，无法导出')
    return
  }

  const chartName = chart.value.name || '图表'

  switch (command) {
    case 'png': {
      // getDataURL 返回 data:image/png;base64,... 格式，直接用于下载
      const dataUrl = chartInstance.getDataURL({
        type: 'png',
        pixelRatio: 2,
        backgroundColor: '#fff',
      })
      downloadFile(dataUrl, `${chartName}.png`)
      ElMessage.success('PNG 导出成功')
      break
    }
    case 'svg': {
      // getDataURL 返回 data:image/svg+xml,... 格式
      const dataUrl = chartInstance.getDataURL({
        type: 'svg',
        pixelRatio: 2,
      })
      downloadFile(dataUrl, `${chartName}.svg`)
      ElMessage.success('SVG 导出成功')
      break
    }
    case 'json': {
      const option = chartInstance.getOption()
      const jsonStr = JSON.stringify(option, null, 2)
      downloadText(jsonStr, `${chartName}.json`, 'application/json')
      ElMessage.success('JSON 导出成功')
      break
    }
  }
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
      allTableData.value = dataRes.data
      tableData.value = dataRes.data
      tableColumns.value = Object.keys(dataRes.data[0])

      // 加载可筛选列的唯一值
      loadColumnValues()
    }
  } catch (error: unknown) {
    ElMessage.error(`加载失败：${error instanceof Error ? error.message : '未知错误'}`)
  } finally {
    loading.value = false
  }
}

// 加载列唯一值 + 初始化范围筛选
const loadColumnValues = async () => {
  const id = route.params.id as number

  for (const col of tableColumns.value) {
    if (isDateColumn(col)) {
      // 日期列：初始化日期范围筛选
      dateRangeFilters.value[col] = null
    } else if (isNumericColumn(col)) {
      // 数值列：初始化范围筛选
      rangeFilters.value[col] = { min: '', max: '' }
    } else {
      // 文本列：加载唯一值
      try {
        const res = await getColumnDistinctValues({ chartId: id, columnName: col })
        if (res.data) {
          columnValues.value = { ...columnValues.value, [col]: res.data }
        }
      } catch {
        // 忽略
      }
    }
  }
}

// 处理筛选变化（文本列）
const handleFilterChange = (col: string, val: string) => {
  filters.value = { ...filters.value, [col]: val || '' }
  handleFilter()
}

// 处理筛选（本地过滤，不请求后端）
const handleFilter = () => {
  let result = [...allTableData.value]

  // 1. 文本筛选（模糊匹配）
  for (const [key, value] of Object.entries(filters.value)) {
    if (value && value !== '') {
      result = result.filter((row) => {
        const cell = (row[key] || '').toLowerCase()
        return cell.includes(value.toLowerCase())
      })
    }
  }

  // 2. 数值范围筛选
  for (const [key, range] of Object.entries(rangeFilters.value)) {
    if (range.min !== '' && range.min != null) {
      const min = Number(range.min)
      if (!isNaN(min)) {
        result = result.filter((row) => Number(row[key]) >= min)
      }
    }
    if (range.max !== '' && range.max != null) {
      const max = Number(range.max)
      if (!isNaN(max)) {
        result = result.filter((row) => Number(row[key]) <= max)
      }
    }
  }

  // 3. 日期范围筛选
  for (const [key, range] of Object.entries(dateRangeFilters.value)) {
    if (range && range[0] && range[1]) {
      const start = new Date(range[0])
      const end = new Date(range[1])
      result = result.filter((row) => {
        const cellDate = new Date(row[key])
        return cellDate >= start && cellDate <= end
      })
    }
  }

  tableData.value = result
  currentPage.value = 1 // 重置到第一页

  // 筛选后重新渲染图表
  if (chart.value.status === 'succeed' && chartInstance) {
    const updatedOption = updateChartWithData(chartInstance.getOption(), result)
    chartInstance.setOption(updatedOption, true)
  }
}

// 清除筛选
const clearFilters = () => {
  filters.value = {}
  // 重置数值范围筛选
  for (const key of Object.keys(rangeFilters.value)) {
    rangeFilters.value[key] = { min: '', max: '' }
  }
  // 重置日期范围筛选
  for (const key of Object.keys(dateRangeFilters.value)) {
    dateRangeFilters.value[key] = null
  }
  tableData.value = allTableData.value
  currentPage.value = 1
}

// 页面重试
const handlePageRetry = () => {
  componentError.value = null
  loadChartData()
}

onMounted(() => {
  loadChartData()
})

onUnmounted(() => {
  // Remove resize listener
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
    resizeHandler = null
  }
  // Dispose chart instance
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style lang="scss" scoped>
.page-shell {
  min-height: calc(100vh - 120px);
  background: #fafafa;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;

  h2 {
    font-size: 24px;
    font-weight: 700;
    color: #18181b;
    margin: 0;
  }
}

.error-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  gap: 16px;
  background: #fff;
  border-radius: 16px;
  border: 1px solid #e4e4e7;

  h3 {
    font-size: 18px;
    font-weight: 600;
    color: #18181b;
    margin: 0;
  }

  .error-message {
    font-size: 13px;
    color: #71717a;
    max-width: 400px;
    text-align: center;
    word-break: break-all;
  }
}

.info-card {
  border-radius: 16px;
  border: 1px solid #e4e4e7;

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
    color: #71717a;
    font-weight: 500;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  .info-value {
    font-size: 14px;
    color: #18181b;
    font-weight: 600;
  }
}

.chart-card,
.result-card,
.data-card {
  border-radius: 16px;
  border: 1px solid #e4e4e7;
}

.card-header {
  font-size: 16px;
  font-weight: 600;
  color: #18181b;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-section {
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
  margin-bottom: 16px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.filter-label {
  font-size: 12px;
  font-weight: 600;
  color: #71717a;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.range-inputs {
  display: flex;
  align-items: center;
  gap: 4px;
}

.range-sep {
  color: #a1a1aa;
  font-size: 12px;
}

.filter-summary {
  margin-top: 12px;
  font-size: 13px;
  color: #71717a;

  strong {
    color: #18181b;
  }
}

.pagination-wrapper {
  padding: 16px 0 0;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #f4f4f5;
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
    color: #18181b;
  }

  .loading-sub {
    font-size: 14px;
    color: #71717a;
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
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.05) 0%, rgba(239, 68, 68, 0.05) 100%);
  border-radius: 8px;

  .failed-text {
    font-size: 18px;
    font-weight: 600;
    color: #ef4444;
  }

  .failed-sub {
    font-size: 13px;
    color: #71717a;
    max-width: 300px;
    text-align: center;
    word-break: break-all;
  }
}

.result-content {
  font-size: 14px;
  line-height: 1.8;
  color: #3f3f46;
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
