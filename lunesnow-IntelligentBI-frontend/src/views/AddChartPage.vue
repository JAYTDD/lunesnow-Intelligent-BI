<template>
  <div class="page-shell">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">新建图表</h1>
        <p class="page-desc">上传 Excel 文件，让 AI 为你生成可视化图表</p>
      </div>
    </div>

    <!-- 表单卡片 -->
    <div class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <!-- 左侧：基本信息 -->
          <div class="form-section">
            <h3 class="section-title">基本信息</h3>

            <div class="form-group">
              <label class="form-label">图表名称</label>
              <el-form-item prop="name">
                <el-input v-model="form.name" placeholder="例如：2024年销售趋势" />
              </el-form-item>
            </div>

            <div class="form-group">
              <label class="form-label">图表类型</label>
              <el-form-item prop="chartType">
                <el-select v-model="form.chartType" placeholder="选择图表类型" style="width: 100%">
                  <el-option label="折线图" value="折线图" />
                  <el-option label="柱状图" value="柱状图" />
                  <el-option label="饼图" value="饼图" />
                  <el-option label="散点图" value="散点图" />
                  <el-option label="雷达图" value="雷达图" />
                </el-select>
              </el-form-item>
            </div>

            <div class="form-group">
              <label class="form-label">分析目标</label>
              <el-form-item prop="goal">
                <el-input
                  v-model="form.goal"
                  type="textarea"
                  :rows="4"
                  :maxlength="200"
                  placeholder="描述你想要分析的内容，例如：分析各季度销售额变化趋势，找出增长最快的品类"
                />
              </el-form-item>
            </div>
          </div>

          <!-- 右侧：文件上传 -->
          <div class="form-section">
            <h3 class="section-title">数据文件</h3>

            <div class="upload-area">
              <el-upload
                ref="uploadRef"
                action="#"
                :auto-upload="false"
                :on-change="handleFileChange"
                :on-remove="handleRemove"
                :limit="1"
                accept=".xlsx,.xls"
                drag
              >
                <div class="upload-content">
                  <el-icon :size="40" color="#d1d5db"><UploadFilled /></el-icon>
                  <p class="upload-title">拖拽文件到这里，或点击上传</p>
                  <p class="upload-hint">支持 .xlsx / .xls 格式，最大 2MB</p>
                </div>
              </el-upload>
            </div>
          </div>
        </div>

        <!-- 提交按钮 -->
        <div class="form-actions">
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            <el-icon v-if="!submitting"><Cpu /></el-icon>
            生成图表
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { UploadFilled, Cpu } from '@element-plus/icons-vue'
import myAxios from '@/request'

const router = useRouter()

const formRef = ref<FormInstance>()
const uploadRef = ref()
const submitting = ref(false)

const form = reactive<API.getChartByAIParams>({
  name: '',
  chartType: '',
  goal: '',
})

const selectedFile = ref<File | null>(null)

const rules: FormRules = {
  name: [{ required: true, message: '请输入图表名称', trigger: 'blur' }],
  chartType: [{ required: true, message: '请选择图表类型', trigger: 'change' }],
  goal: [{ required: true, message: '请输入分析目标', trigger: 'blur' }],
}

const ALLOWED_TYPES = ['.xlsx', '.xls']
const MAX_SIZE = 2 * 1024 * 1024

const handleFileChange = (uploadFile: UploadFile) => {
  const file = uploadFile.raw
  if (!file) return

  const ext = file.name.substring(file.name.lastIndexOf('.')).toLowerCase()
  if (!ALLOWED_TYPES.includes(ext)) {
    ElMessage.error('仅支持 .xlsx / .xls 格式')
    uploadRef.value?.clearFiles()
    return
  }

  if (file.size > MAX_SIZE) {
    ElMessage.error('文件大小不能超过 2MB')
    uploadRef.value?.clearFiles()
    return
  }

  selectedFile.value = uploadFile.raw || null
}

const handleRemove = () => {
  ElMessageBox.confirm('确认删除选中的文件吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    selectedFile.value = null
  })
}

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
      const formDataFile = new FormData()
      formDataFile.append('file', selectedFile.value)
      formDataFile.append('name', form.name)
      formDataFile.append('chartType', form.chartType)
      formDataFile.append('goal', form.goal)

      const res = await myAxios('/chart/gen', {
        method: 'POST',
        data: formDataFile,
      })
      if (res.code === 0) {
        ElMessage.success('图表已提交，正在生成中')
        router.push('/my/charts')
      }
    } catch (error: unknown) {
      ElMessage.error(error instanceof Error ? error.message : '生成失败')
    } finally {
      submitting.value = false
    }
  })
}

const handleReset = () => {
  form.name = ''
  form.chartType = ''
  form.goal = ''
  selectedFile.value = null
  uploadRef.value?.clearFiles()
  formRef.value?.resetFields()
}
</script>

<style lang="scss" scoped>
.page-shell {
  max-width: 1000px;
  margin: 0 auto;
  padding: 40px 24px;
  min-height: calc(100vh - 120px);
}

.page-header {
  margin-bottom: 32px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #18181b;
  margin: 0 0 6px 0;
  letter-spacing: -0.5px;
}

.page-desc {
  font-size: 14px;
  color: #71717a;
  margin: 0;
}

.form-card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #e4e4e7;
  padding: 32px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 40px;
}

.form-section {
  .section-title {
    font-size: 15px;
    font-weight: 600;
    color: #18181b;
    margin: 0 0 24px 0;
    padding-bottom: 12px;
    border-bottom: 1px solid #f4f4f5;
  }
}

.form-group {
  margin-bottom: 20px;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #3f3f46;
  margin-bottom: 8px;
}

:deep(.el-input__wrapper),
:deep(.el-textarea__inner) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #e4e4e7;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 0 0 1px #d4d4d8;
  }

  &.is-focus {
    box-shadow: 0 0 0 2px #18181b;
  }
}

:deep(.el-textarea__inner) {
  resize: none;
}

.upload-area {
  :deep(.el-upload) {
    width: 100%;

    .el-upload-dragger {
      width: 100%;
      height: 200px;
      border-radius: 12px;
      border: 2px dashed #e4e4e7;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;

      &:hover {
        border-color: #18181b;
        background: #fafafa;
      }
    }
  }
}

.upload-content {
  text-align: center;

  .upload-title {
    font-size: 14px;
    font-weight: 600;
    color: #3f3f46;
    margin: 12px 0 4px;
  }

  .upload-hint {
    font-size: 12px;
    color: #a1a1aa;
    margin: 0;
  }
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #f4f4f5;

  :deep(.el-button) {
    height: 42px;
    padding: 0 24px;
    border-radius: 10px;
    font-weight: 600;
    font-size: 14px;
  }

  :deep(.el-button--primary) {
    background: #18181b;
    border-color: #18181b;

    &:hover {
      background: #27272a;
      border-color: #27272a;
    }
  }
}

@media (max-width: 768px) {
  .form-grid {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .page-shell {
    padding: 24px 16px;
  }
}
</style>
