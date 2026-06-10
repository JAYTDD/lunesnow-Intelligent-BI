<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <div class="eyebrow">Chart</div>
        <h2>智能图表生成</h2>
      </div>
    </div>

    <div class="content-grid">
      <el-card class="form-section" shadow="never">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
          <el-form-item label="图表名称" prop="name">
            <el-input v-model="form.name" placeholder="请输入图表名称" />
          </el-form-item>

          <el-form-item label="图表类型" prop="chartType">
            <el-select v-model="form.chartType" placeholder="请选择图表类型" style="width: 100%">
              <el-option label="折线图" value="折线图" />
              <el-option label="柱状图" value="柱状图" />
              <el-option label="饼图" value="饼图" />
              <el-option label="散点图" value="散点图" />
              <el-option label="雷达图" value="雷达图" />
            </el-select>
          </el-form-item>

          <el-form-item label="分析目标" prop="goal">
            <el-input
              v-model="form.goal"
              type="textarea"
              :rows="3"
              :maxlength="200"
              placeholder="请输入分析目标，例如：分析2024年各季度销售额趋势"
            />
          </el-form-item>

          <el-form-item label="数据文件" prop="file">
            <el-upload
              ref="uploadRef"
              action="#"
              :auto-upload="false"
              :on-change="handleFileChange"
              :on-remove="handleRemove"
              :limit="1"
              accept=".xlsx,.xls"
            >
              <el-button type="primary">选择 Excel 文件</el-button>
              <template #tip>
                <div class="el-upload__tip">请上传 Excel 文件（.xlsx / .xls）</div>
              </template>
            </el-upload>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="handleSubmit">
              生成图表
            </el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card class="chart-section" shadow="never">
        <div class="preview-title">图表预览:</div>
        <div id="genChart"></div>
      </el-card>
    </div>

    <el-card class="result-section" shadow="never">
      <div id="genResult">分析结果: <br />{{ genResult }}</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import myAxios from '@/request'
import * as echarts from 'echarts'

// 生成结果
const genResult = ref('')
// 表单实例
const formRef = ref<FormInstance>()
// 上传组件实例
const uploadRef = ref()
const submitting = ref(false)
// 表单数据
const form = reactive<API.getChartByAIParams>({
  name: '111',
  chartType: '折线图',
  goal: '分析用户增长情况',
})

// 选中的文件
const selectedFile = ref<File | null>(null)
// 表单验证规则
const rules: FormRules = {
  name: [{ required: true, message: '请输入图表名称', trigger: 'blur' }],
  chartType: [{ required: true, message: '请选择图表类型', trigger: 'change' }],
  goal: [{ required: true, message: '请输入分析目标', trigger: 'blur' }],
}

const ALLOWED_TYPES = ['.xlsx', '.xls']
const MAX_SIZE = 2 * 1024 * 1024
// 处理文件选择
const handleFileChange = (uploadFile: UploadFile) => {
  const file = uploadFile.raw
  if (!file) return

  // 后缀校验
  const ext = file.name.substring(file.name.lastIndexOf('.')).toLowerCase()
  if (!ALLOWED_TYPES.includes(ext)) {
    ElMessage.error('仅支持 .xlsx / .xls 格式')
    uploadRef.value?.clearFiles()
    return
  }

  // 大小校验
  if (file.size > MAX_SIZE) {
    ElMessage.error('文件大小不能超过 10MB')
    uploadRef.value?.clearFiles()
    return
  }
  // 保存选中的文件
  selectedFile.value = uploadFile.raw || null
}
// 处理文件删除
const handleRemove = () => {
  ElMessageBox.confirm('确认删除选中的文件吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    selectedFile.value = null
  })
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    if (!selectedFile.value) {
      ElMessage.error('请选择 Excel 文件')
      return
    }

    submitting.value = true

    try {
      // 构建 multipart/form-data
      const formDataFile = new FormData()
      formDataFile.append('file', selectedFile.value)
      formDataFile.append('name', form.name)
      formDataFile.append('chartType', form.chartType)
      formDataFile.append('goal', form.goal)
      // 这里openapi接口不匹配，需要手动处理返回数据
      const res = await myAxios('/chart/gen', {
        method: 'POST',
        data: formDataFile,
      })
      if (res.code === 0) {
        const chartOption = new Function('return ' + res.data.genChart)()
        const myChart = echarts.init(document.getElementById('genChart'))
        myChart.setOption(chartOption)
        genResult.value = res.data.genResult || ''
        ElMessage.success('图表生成成功！')
      }
    } catch (error: any) {
      ElMessage.error(error.message || '生成失败')
    } finally {
      submitting.value = false
    }
  })
}

// 重置表单
const handleReset = () => {
  form.name = ''
  form.chartType = ''
  form.goal = ''
  selectedFile.value = null
  uploadRef.value?.clearFiles()
  formRef.value?.resetFields()
  genResult.value = ''
  // 清空图表
  const myChart = echarts.init(document.getElementById('genChart'))
  myChart.clear()
}
</script>

<style lang="scss" scoped>
.page-shell {
  min-height: calc(100vh - 120px);
  background:
    radial-gradient(ellipse 80% 60% at 20% 10%, rgba(16, 185, 129, 0.05) 0%, transparent 60%),
    radial-gradient(ellipse 60% 50% at 85% 80%, rgba(245, 158, 11, 0.04) 0%, transparent 55%),
    #f5f6f8;
  border-radius: 20px;
  padding: 24px 40px 32px;
  display: grid;
  gap: 24px;
}

.page-header {
  .eyebrow {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.2em;
    color: #10b981;
    text-transform: uppercase;
    margin-bottom: 4px;

    &::before {
      content: '';
      width: 18px;
      height: 2px;
      background: #10b981;
      border-radius: 1px;
    }
  }

  h2 {
    font-size: 26px;
    font-weight: 800;
    margin: 0;
    color: #1a1a2e;
    letter-spacing: -0.03em;
    line-height: 1.2;
  }
}

.content-grid {
  display: grid;
  grid-template-columns: 440px 1fr;
  gap: 24px;
  align-items: stretch;
}

.form-section {
  background: #ffffff;
  border-radius: 20px;
  padding: 0;
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.04),
    0 4px 16px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  transition: box-shadow 0.35s cubic-bezier(0.22, 1, 0.36, 1);

  &:hover {
    box-shadow:
      0 2px 4px rgba(0, 0, 0, 0.04),
      0 8px 32px rgba(0, 0, 0, 0.06);
  }

  :deep(.el-card__body) {
    padding: 24px 28px 20px;
  }

  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item__label) {
    font-weight: 600;
    color: #4b5563;
    font-size: 13px;
    padding-bottom: 4px !important;
  }

  :deep(.el-input__wrapper),
  :deep(.el-textarea__inner) {
    background: #fafbfc;
    border: 1.5px solid #f0f0f0;
    border-radius: 14px;
    box-shadow: none !important;
    transition: all 0.35s cubic-bezier(0.22, 1, 0.36, 1);

    &:hover {
      border-color: #d1d5db;
      background: #ffffff;
    }
  }

  :deep(.el-input__wrapper.is-focus),
  :deep(.el-textarea__inner:focus) {
    border-color: #10b981;
    background: #ffffff;
    box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.12) !important;
  }

  :deep(.el-select) {
    width: 100%;
  }

  :deep(.el-textarea__inner) {
    resize: none;
  }

  :deep(.el-upload) {
    width: 100%;

    .el-button {
      width: 100%;
      height: 44px;
      border-radius: 14px;
      font-weight: 600;
      font-size: 14px;
      background: linear-gradient(135deg, #10b981 0%, #059669 100%);
      border: none;
      transition: all 0.35s cubic-bezier(0.22, 1, 0.36, 1);

      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 6px 20px rgba(16, 185, 129, 0.35);
      }

      &:active {
        transform: translateY(0);
      }
    }
  }

  :deep(.el-upload__tip) {
    color: #9ca3af;
    font-size: 12px;
    margin-top: 10px;
    text-align: center;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
    margin-top: 12px;
    padding-top: 20px;
    border-top: 1px solid #f0f0f0;

    .el-form-item__content {
      gap: 12px;
    }

    .el-button--primary {
      height: 44px;
      padding: 0 32px;
      border-radius: 14px;
      font-weight: 700;
      font-size: 15px;
      background: linear-gradient(135deg, #10b981 0%, #059669 100%);
      border: none;
      letter-spacing: 0.04em;
      transition: all 0.35s cubic-bezier(0.22, 1, 0.36, 1);

      &:not(.is-loading):hover {
        transform: translateY(-2px);
        box-shadow: 0 8px 24px rgba(16, 185, 129, 0.35);
      }

      &.is-loading {
        animation: pulse-ring 1.5s ease-in-out infinite;
      }
    }

    .el-button:not(.el-button--primary) {
      height: 44px;
      padding: 0 24px;
      border-radius: 14px;
      font-weight: 600;
      color: #4b5563;
      border: 1.5px solid #e5e7eb;
      background: transparent;
      transition: all 0.35s cubic-bezier(0.22, 1, 0.36, 1);

      &:hover {
        border-color: #d1d5db;
        background: #f9fafb;
      }
    }
  }
}

@keyframes pulse-ring {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.4);
  }
  50% {
    box-shadow: 0 0 0 10px rgba(16, 185, 129, 0);
  }
}

.chart-section {
  background: #ffffff;
  border-radius: 20px;
  padding: 0;
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.04),
    0 4px 16px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  transition: box-shadow 0.35s cubic-bezier(0.22, 1, 0.36, 1);

  &:hover {
    box-shadow:
      0 2px 4px rgba(0, 0, 0, 0.04),
      0 8px 32px rgba(0, 0, 0, 0.06);
  }

  :deep(.el-card__body) {
    padding: 20px 28px 24px;
  }

  .preview-title {
    font-size: 15px;
    font-weight: 700;
    color: #1a1a2e;
    margin-bottom: 16px;
    display: flex;
    align-items: center;
    gap: 10px;

    &::before {
      content: '';
      width: 4px;
      height: 20px;
      background: linear-gradient(180deg, #10b981, #34d399);
      border-radius: 2px;
      flex-shrink: 0;
    }
  }

  #genChart {
    width: 100%;
    height: 280px;
    border-radius: 14px;
    position: relative;

    &:empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 14px;
      background: linear-gradient(
        135deg,
        rgba(16, 185, 129, 0.03) 0%,
        rgba(52, 211, 153, 0.03) 100%
      );
      border: 2px dashed #d9dce2;
      min-height: 280px;

      &::after {
        content: '';
        display: block;
        width: 56px;
        height: 56px;
        background: linear-gradient(135deg, rgba(16, 185, 129, 0.1), rgba(52, 211, 153, 0.1));
        border-radius: 16px;
        mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.5'%3E%3Cpath d='M3 16V4a1 1 0 011-1h4l2 2h8a1 1 0 011 1v10a1 1 0 01-1 1H4a1 1 0 01-1-1z'/%3E%3Cpath d='M14 14l2 2 4-4'/%3E%3C/svg%3E")
          center / contain no-repeat;
        -webkit-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.5'%3E%3Cpath d='M3 16V4a1 1 0 011-1h4l2 2h8a1 1 0 011 1v10a1 1 0 01-1 1H4a1 1 0 01-1-1z'/%3E%3Cpath d='M14 14l2 2 4-4'/%3E%3C/svg%3E")
          center / contain no-repeat;
        animation: float 3s ease-in-out infinite;
      }
    }

    &:has(canvas) {
      background: transparent;

      canvas {
        animation: chart-in 0.8s cubic-bezier(0.22, 1, 0.36, 1) both;
      }
    }
  }
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-6px);
  }
}

.result-section {
  background: #ffffff;
  border-radius: 20px;
  padding: 0;
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.04),
    0 4px 16px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  transition: box-shadow 0.35s cubic-bezier(0.22, 1, 0.36, 1);

  &:hover {
    box-shadow:
      0 2px 4px rgba(0, 0, 0, 0.04),
      0 8px 32px rgba(0, 0, 0, 0.06);
  }

  :deep(.el-card__body) {
    padding: 20px 28px;
  }

  #genResult {
    font-size: 14px;
    line-height: 1.9;
    color: #4b5563;
    font-weight: 500;
    white-space: pre-wrap;
    word-break: break-word;

    &:empty::after {
      content: '✨  生成后在此展示 AI 分析结果';
      color: #9ca3af;
      font-weight: 400;
    }
  }
}

@keyframes chart-in {
  from {
    opacity: 0;
    transform: scale(0.92) translateY(12px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

@media (max-width: 1100px) {
  .page-shell {
    padding: 24px 20px;
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
