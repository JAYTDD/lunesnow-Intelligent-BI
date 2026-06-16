<template>
  <div class="page-shell">
    <div class="page-header">
      <h1 class="page-title">个人中心</h1>
      <p class="page-desc">管理你的账户信息</p>
    </div>

    <div class="profile-grid">
      <!-- 左侧：头像卡片 -->
      <div class="avatar-card">
        <div class="avatar-section">
          <div class="avatar-wrapper" @click="triggerUpload">
            <img
              v-if="loginUserStore.loginUser.userAvatar"
              :src="loginUserStore.loginUser.userAvatar"
              class="avatar-img"
              alt="头像"
            />
            <div v-else class="avatar-placeholder">
              {{ avatarText }}
            </div>
            <div class="avatar-overlay">
              <el-icon :size="20"><Camera /></el-icon>
              <span>更换头像</span>
            </div>
          </div>
          <input
            ref="fileInputRef"
            type="file"
            accept="image/*"
            style="display: none"
            @change="handleAvatarChange"
          />
          <h3 class="user-name">{{ loginUserStore.loginUser.userName || '未设置用户名' }}</h3>
          <span class="user-role-tag">
            {{ loginUserStore.loginUser.userRole === 'admin' ? '管理员' : '普通用户' }}
          </span>
        </div>
      </div>

      <!-- 右侧：编辑表单 -->
      <div class="form-card">
        <h3 class="card-title">编辑资料</h3>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <div class="form-group">
            <label class="form-label">用户名</label>
            <el-form-item prop="userName">
              <el-input
                v-model="form.userName"
                placeholder="请输入用户名"
                size="large"
              />
            </el-form-item>
          </div>

          <div class="form-actions">
            <el-button @click="handleReset">取消</el-button>
            <el-button type="primary" :loading="saving" @click="handleSave">
              保存修改
            </el-button>
          </div>
        </el-form>

        <!-- 账户信息（只读） -->
        <div class="account-info">
          <h4 class="info-title">账户信息</h4>
          <div class="info-row">
            <span class="info-label">账号</span>
            <span class="info-value">{{ loginUserStore.loginUser.userAccount || '-' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">角色</span>
            <span class="info-value">{{ loginUserStore.loginUser.userRole === 'admin' ? '管理员' : '用户' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">用户 ID</span>
            <span class="info-value info-value--mono">{{ loginUserStore.loginUser.id || '-' }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { updateMyUser } from '@/api/userController'
import myAxios from '@/request'

const loginUserStore = useLoginUserStore()

const formRef = ref<FormInstance>()
const fileInputRef = ref<HTMLInputElement>()
const saving = ref(false)
const uploading = ref(false)

// 头像文字
const avatarText = computed(() => {
  const name = loginUserStore.loginUser.userName || ''
  return name.charAt(0).toUpperCase() || '?'
})

// 表单数据
const form = reactive({
  userName: loginUserStore.loginUser.userName || '',
})

// 表单校验
const rules: FormRules = {
  userName: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度 2-20 个字符', trigger: 'blur' },
  ],
}

// 触发文件选择
const triggerUpload = () => {
  fileInputRef.value?.click()
}

// 处理头像变更
const handleAvatarChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  // 校验文件类型
  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }

  // 校验文件大小（2MB）
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 2MB')
    return
  }

  uploading.value = true
  try {
    // 1. 上传文件
    const formData = new FormData()
    formData.append('file', file)

    const uploadRes = await myAxios<{ code: number; data: string }>('/file/upload?biz=user_avatar', {
      method: 'POST',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })

    if (uploadRes.code !== 0 || !uploadRes.data) {
      ElMessage.error('头像上传失败')
      return
    }

    const avatarUrl = uploadRes.data

    // 2. 更新用户头像
    await updateMyUser({ userAvatar: avatarUrl })

    // 3. 更新 Store
    loginUserStore.setLoginUser({
      ...loginUserStore.loginUser,
      userAvatar: avatarUrl,
    })

    ElMessage.success('头像更新成功')
  } catch (error: unknown) {
    ElMessage.error(error instanceof Error ? error.message : '头像上传失败')
  } finally {
    uploading.value = false
    // 清空 input 值，允许重新选择同一文件
    target.value = ''
  }
}

// 保存用户名
const handleSave = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    saving.value = true
    try {
      await updateMyUser({ userName: form.userName })

      // 更新 Store
      loginUserStore.setLoginUser({
        ...loginUserStore.loginUser,
        userName: form.userName,
      })

      ElMessage.success('保存成功')
    } catch (error: unknown) {
      ElMessage.error(error instanceof Error ? error.message : '保存失败')
    } finally {
      saving.value = false
    }
  })
}

// 重置表单
const handleReset = () => {
  form.userName = loginUserStore.loginUser.userName || ''
  formRef.value?.resetFields()
}
</script>

<style lang="scss" scoped>
.page-shell {
  max-width: 800px;
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

.profile-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 24px;
}

/* 左侧头像卡片 */
.avatar-card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #e4e4e7;
  padding: 32px 24px;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.avatar-wrapper {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  margin-bottom: 16px;

  &:hover .avatar-overlay {
    opacity: 1;
  }
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  background: #18181b;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 700;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
  color: #fff;

  span {
    font-size: 11px;
    font-weight: 500;
  }
}

.user-name {
  font-size: 18px;
  font-weight: 700;
  color: #18181b;
  margin: 0 0 8px 0;
}

.user-role-tag {
  display: inline-block;
  padding: 4px 12px;
  background: #f4f4f5;
  border-radius: 100px;
  font-size: 12px;
  font-weight: 600;
  color: #71717a;
}

/* 右侧表单卡片 */
.form-card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #e4e4e7;
  padding: 32px;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: #18181b;
  margin: 0 0 24px 0;
  padding-bottom: 16px;
  border-bottom: 1px solid #f4f4f5;
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

:deep(.el-input__wrapper) {
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

.form-actions {
  display: flex;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid #f4f4f5;

  :deep(.el-button) {
    height: 40px;
    padding: 0 20px;
    border-radius: 10px;
    font-weight: 600;
    font-size: 13px;
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

/* 账户信息 */
.account-info {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #f4f4f5;
}

.info-title {
  font-size: 14px;
  font-weight: 600;
  color: #18181b;
  margin: 0 0 16px 0;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f4f4f5;

  &:last-child {
    border-bottom: none;
  }
}

.info-label {
  font-size: 13px;
  color: #71717a;
}

.info-value {
  font-size: 13px;
  font-weight: 600;
  color: #18181b;

  &--mono {
    font-family: 'SF Mono', 'Consolas', monospace;
    font-size: 12px;
    color: #71717a;
  }
}

@media (max-width: 768px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }

  .avatar-card {
    order: -1;
  }
}
</style>
