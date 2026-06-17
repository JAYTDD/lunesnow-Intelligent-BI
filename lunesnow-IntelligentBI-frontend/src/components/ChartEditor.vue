<template>
  <el-dialog
    v-model="dialogVisible"
    title="编辑图表配置"
    width="900px"
    :close-on-click-modal="false"
  >
    <div class="editor-layout">
      <!-- 左侧：代码编辑器 -->
      <div class="editor-panel">
        <div class="panel-header">
          <span class="panel-title">ECharts 配置</span>
          <el-tag type="info" size="small">JSON</el-tag>
        </div>
        <div class="code-editor">
          <textarea ref="editorRef" v-model="code"></textarea>
        </div>
      </div>

      <!-- 右侧：实时预览 -->
      <div class="preview-panel">
        <div class="panel-header">
          <span class="panel-title">实时预览</span>
          <el-button size="small" @click="handlePreview">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
        <div ref="chartRef" class="chart-preview"></div>
      </div>
    </div>

    <!-- 错误提示 -->
    <el-alert
      v-if="errorMsg"
      :title="errorMsg"
      type="error"
      show-icon
      :closable="false"
      style="margin-top: 12px"
    />

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onUnmounted, onErrorCaptured } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { safeParseChartConfig, validateEChartsOption } from '@/utils/chartValidator'
import { editChartConfig } from '@/api/chartController'
import * as echarts from 'echarts'

interface Props {
  visible: boolean // 是否显示对话框
  chartId?: number // 图表 ID
  genChart?: string // 图表配置
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved'): void
}>()

const dialogVisible = ref(props.visible) // 对话框是否显示
const code = ref(props.genChart || '') // 图表配置
const errorMsg = ref('') // 错误提示
const saving = ref(false) // 保存中
const chartRef = ref<HTMLDivElement>() // 图表容器
const editorRef = ref<HTMLTextAreaElement>() // 编辑器
let chartInstance: echarts.ECharts | null = null

// 捕获子组件渲染错误
onErrorCaptured((err, instance, info) => {
  console.error('ChartEditor 组件错误:', err, info)
  errorMsg.value = `组件渲染出错: ${err.message}`
  return false // 阻止错误继续传播
})

// 监听 visible 变化
watch(
  () => props.visible,
  (val) => {
    dialogVisible.value = val
    if (val) {
      code.value = props.genChart || ''
      errorMsg.value = ''
      nextTick(() => {
        handlePreview()
        // 聚焦编辑器
        editorRef.value?.focus()
      })
    }
  },
)

// 监听 dialogVisible 变化（点击关闭按钮时）
watch(dialogVisible, (val) => {
  emit('update:visible', val)
})

// 监听 code 变化，自动预览（防抖）
let previewTimer: ReturnType<typeof setTimeout> | null = null
watch(code, () => {
  if (previewTimer) clearTimeout(previewTimer)
  previewTimer = setTimeout(() => {
    handlePreview()
  }, 500)
})

// 预览图表
const handlePreview = () => {
  errorMsg.value = ''

  if (!code.value.trim()) {
    errorMsg.value = '请输入图表配置'
    return
  }

  // 解析配置
  const option = safeParseChartConfig(code.value)
  if (!option) {
    errorMsg.value = '配置格式错误，请检查 JSON 语法'
    return
  }

  // 校验配置
  const { valid, error } = validateEChartsOption(option)
  if (!valid) {
    errorMsg.value = error || '配置校验失败'
    return
  }

  // 渲染图表
  if (!chartRef.value) return

  // 清理旧实例
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }

  try {
    chartInstance = echarts.init(chartRef.value)
    chartInstance.setOption(option)
  } catch (e: any) {
    errorMsg.value = `渲染失败: ${e.message}`
  }
}

// 保存配置
const handleSave = async () => {
  if (!props.chartId) {
    ElMessage.error('图表 ID 无效')
    return
  }

  if (!code.value.trim()) {
    ElMessage.error('图表配置不能为空')
    return
  }

  // 再次校验
  const option = safeParseChartConfig(code.value)
  if (!option) {
    ElMessage.error('配置格式错误，请检查后重试')
    return
  }

  const { valid, error } = validateEChartsOption(option)
  if (!valid) {
    ElMessage.error(error || '配置校验失败')
    return
  }

  saving.value = true
  try {
    await editChartConfig({
      id: props.chartId,
      genChart: code.value,
    })
    ElMessage.success('保存成功')
    emit('saved')
    handleClose()
  } catch (e: any) {
    ElMessage.error(`保存失败: ${e.message || '未知错误'}`)
  } finally {
    saving.value = false
  }
}

// 关闭对话框
const handleClose = () => {
  dialogVisible.value = false
  emit('update:visible', false)
  // 清理图表实例
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
}

// 组件卸载时清理
onUnmounted(() => {
  if (chartInstance) {
    chartInstance.dispose()
  }
  if (previewTimer) {
    clearTimeout(previewTimer)
  }
})
</script>

<style lang="scss" scoped>
.editor-layout {
  display: flex;
  gap: 16px;
  height: 450px;
}

.editor-panel,
.preview-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid #e4e4e7;
  border-radius: 12px;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #fafafa;
  border-bottom: 1px solid #f4f4f5;

  .panel-title {
    font-size: 13px;
    font-weight: 600;
    color: #3f3f46;
  }
}

.code-editor {
  flex: 1;
  overflow: hidden;

  textarea {
    width: 100%;
    height: 100%;
    border: none;
    outline: none;
    resize: none;
    padding: 14px;
    font-family: 'SF Mono', 'Consolas', 'Monaco', monospace;
    font-size: 13px;
    line-height: 1.6;
    background: #18181b;
    color: #e4e4e7;
    tab-size: 2;

    &::placeholder {
      color: #52525b;
    }
  }
}

.chart-preview {
  flex: 1;
  min-height: 0;
  background: #fff;
}
</style>
