<template>
  <div class="page-shell">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">新建图表</h1>
        <p class="page-desc">上传 Excel/CSV 文件，让 AI 为你生成可视化图表</p>
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
                accept=".xlsx,.xls,.csv"
                drag
              >
                <div class="upload-content">
                  <el-icon :size="40" color="#d1d5db"><UploadFilled /></el-icon>
                  <p class="upload-title">拖拽文件到这里，或点击上传</p>
                  <p class="upload-hint">支持 .xlsx / .xls / .csv 格式，最大 2MB</p>
                </div>
              </el-upload>
            </div>
          </div>
        </div>

        <!-- 提交按钮：重置 / 生成图表 -->
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
/**
 * 新建图表页面
 * 负责收集图表基本信息与数据文件，提交给后端 AI 生成图表
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { UploadFilled, Cpu } from '@element-plus/icons-vue'
import myAxios from '@/request'

const router = useRouter() // 路由实例

const formRef = ref<FormInstance>() // 表单引用
const uploadRef = ref() // 上传组件引用
const submitting = ref(false) // 提交中状态

const form = reactive<API.getChartByAIParams>({
  name: '', // 图表名称
  chartType: '', // 图表类型
  goal: '', // 分析目标
})

const selectedFile = ref<File | null>(null) // 选中的文件

const rules: FormRules = {
  name: [{ required: true, message: '请输入图表名称', trigger: 'blur' }], // 名称必填
  chartType: [{ required: true, message: '请选择图表类型', trigger: 'change' }], // 类型必填
  goal: [{ required: true, message: '请输入分析目标', trigger: 'blur' }], // 目标必填
}

const ALLOWED_TYPES = ['.xlsx', '.xls', '.csv'] // 允许的文件后缀
const ALLOWED_MIME = [
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', // .xlsx
  'application/vnd.ms-excel', // .xls
  'text/csv', // .csv
  'application/csv', // .csv
]
const MAX_SIZE = 2 * 1024 * 1024 // 最大文件大小 2MB

/** 文件选择变化回调 */
const handleFileChange = (uploadFile: UploadFile) => {
  const file = uploadFile.raw // 获取原始文件对象
  if (!file) return // 无原始文件则跳过

  const ext = file.name.substring(file.name.lastIndexOf('.')).toLowerCase() // 提取后缀

  // 1. 后缀名校验
  if (!ALLOWED_TYPES.includes(ext)) {
    // 后缀不在白名单
    ElMessage.error(`文件格式不支持，仅允许 ${ALLOWED_TYPES.join('、')} 格式`)
    uploadRef.value?.clearFiles() // 清除已选文件
    return
  }

  // 2. MIME type 校验（防止改后缀绕过）
  if (file.type && !ALLOWED_MIME.includes(file.type)) {
    // 内容与后缀不匹配
    ElMessage.error('文件内容与后缀不匹配，请确认文件未被修改')
    uploadRef.value?.clearFiles() // 清除已选文件
    return
  }

  // 3. 文件大小校验
  if (file.size === 0) {
    // 空文件
    ElMessage.error('文件为空，请选择有效的数据文件')
    uploadRef.value?.clearFiles() // 清除已选文件
    return
  }

  if (file.size > MAX_SIZE) {
    // 超过大小限制
    ElMessage.error(`文件大小超过限制（最大 ${MAX_SIZE / 1024 / 1024}MB）`)
    uploadRef.value?.clearFiles() // 清除已选文件
    return
  }

  selectedFile.value = uploadFile.raw || null // 校验通过，保存文件引用
}

/** 移除文件回调 */
const handleRemove = () => {
  ElMessageBox.confirm('确认删除选中的文件吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    selectedFile.value = null // 确认后清空文件引用
  })
}

/** 提交表单：校验 + 上传文件生成图表 */
const handleSubmit = async () => {
  if (!formRef.value) return // 表单引用不存在则跳过
  await formRef.value.validate(async (valid) => {
    if (!valid) return // 表单校验不通过则跳过

    if (!selectedFile.value) {
      // 未选择文件
      ElMessage.error('请选择 Excel/CSV 文件')
      return
    }

    submitting.value = true // 进入提交状态

    try {
      const formDataFile = new FormData() // 构建表单数据
      formDataFile.append('file', selectedFile.value) // 追加文件
      formDataFile.append('name', form.name) // 追加名称
      formDataFile.append('chartType', form.chartType) // 追加图表类型
      formDataFile.append('goal', form.goal) // 追加分析目标

      // 上传文件生成图表
      const res = await myAxios('/chart/gen', {
        method: 'POST',
        data: formDataFile,
      })
      if (res.code === 0) {
        // 提交成功
        ElMessage.success('图表已提交，正在生成中')
        router.push('/my/charts') // 跳转我的图表页
      }
    } catch (error: unknown) {
      ElMessage.error(error instanceof Error ? error.message : '生成失败') // 提交失败提示
    } finally {
      submitting.value = false // 无论成败都结束提交状态
    }
  })
}

/** 重置表单 */
const handleReset = () => {
  form.name = '' // 清空名称
  form.chartType = '' // 清空图表类型
  form.goal = '' // 清空分析目标
  selectedFile.value = null // 清空文件引用
  uploadRef.value?.clearFiles() // 清除上传组件文件
  formRef.value?.resetFields() // 重置表单校验状态
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
