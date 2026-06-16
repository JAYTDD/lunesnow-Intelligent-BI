<template>
  <div class="auth-page">
    <div class="auth-card">
      <!-- 左侧品牌区 -->
      <div class="auth-brand">
        <div class="brand-content">
          <div class="brand-logo">
            <svg width="40" height="40" viewBox="0 0 28 28" fill="none">
              <rect width="28" height="28" rx="8" fill="#18181b"/>
              <path d="M8 18V12M12 18V10M16 18V14M20 18V8" stroke="#22c55e" stroke-width="2.5" stroke-linecap="round"/>
            </svg>
          </div>
          <div class="brand-label">Intelligent BI</div>
        </div>
        <h1 class="brand-title">开始你的<br/>数据之旅</h1>
        <p class="brand-desc">创建账户，解锁 AI 驱动的智能图表生成能力。<br/>免费体验，无需信用卡。</p>
        <div class="brand-features">
          <div class="feature-item">
            <span class="feature-dot"></span>
            <span>免费额度体验</span>
          </div>
          <div class="feature-item">
            <span class="feature-dot"></span>
            <span>支持多种图表类型</span>
          </div>
          <div class="feature-item">
            <span class="feature-dot"></span>
            <span>数据安全加密</span>
          </div>
        </div>
      </div>

      <!-- 右侧表单区 -->
      <div class="auth-form-wrapper">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          class="auth-form"
          @keyup.enter="handleRegister"
        >
          <h2 class="form-title">创建账户</h2>
          <p class="form-subtitle">填写以下信息完成注册</p>

          <div class="form-group">
            <label class="form-label">账号</label>
            <el-form-item prop="userAccount">
              <el-input
                v-model="form.userAccount"
                placeholder="请输入账号"
                size="large"
                :prefix-icon="User"
              />
            </el-form-item>
          </div>

          <div class="form-group">
            <label class="form-label">密码</label>
            <el-form-item prop="userPassword">
              <el-input
                v-model="form.userPassword"
                type="password"
                placeholder="请输入密码"
                size="large"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>
          </div>

          <div class="form-group">
            <label class="form-label">确认密码</label>
            <el-form-item prop="checkPassword">
              <el-input
                v-model="form.checkPassword"
                type="password"
                placeholder="请确认密码"
                size="large"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>
          </div>

          <div class="form-footer">
            <router-link to="/user/login" class="form-link">已有账户？去登录</router-link>
          </div>

          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            @click="handleRegister"
          >
            注册
          </el-button>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { userRegister } from '@/api/userController'

const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const validateConfirmPassword = (rule: any, value: any, callback: any) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.userPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  userAccount: [{ required: true, message: '账号是必填项！', trigger: 'blur' }],
  userPassword: [{ required: true, message: '密码是必填项！', trigger: 'blur' }],
  checkPassword: [{ required: true, validator: validateConfirmPassword, trigger: 'blur' }],
}

const handleRegister = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const registerData: API.UserRegisterRequest = {
        userAccount: form.userAccount,
        userPassword: form.userPassword,
        checkPassword: form.checkPassword,
      }
      await userRegister(registerData)
      ElMessage.success('注册成功')
      router.push('/user/login')
    } catch (error: unknown) {
      ElMessage.error(error instanceof Error ? error.message : '注册失败')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style lang="scss" scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background: #fafafa;
}

.auth-card {
  width: 100%;
  max-width: 960px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #fff;
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 20px 60px rgba(0, 0, 0, 0.06);
}

.auth-brand {
  padding: 48px 40px;
  background: #18181b;
  color: #fff;
  display: flex;
  flex-direction: column;
}

.brand-content {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 48px;
}

.brand-logo svg {
  width: 32px;
  height: 32px;
}

.brand-label {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: rgba(255, 255, 255, 0.9);
}

.brand-title {
  font-size: 32px;
  font-weight: 700;
  line-height: 1.2;
  margin: 0 0 16px 0;
  letter-spacing: -0.5px;
}

.brand-desc {
  font-size: 14px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.6);
  margin: 0 0 40px 0;
}

.brand-features {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
}

.feature-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
}

.auth-form-wrapper {
  padding: 48px 40px;
  display: flex;
  align-items: center;
}

.auth-form {
  width: 100%;
  max-width: 320px;
  margin: 0 auto;
}

.form-title {
  font-size: 24px;
  font-weight: 700;
  color: #18181b;
  margin: 0 0 6px 0;
}

.form-subtitle {
  font-size: 14px;
  color: #71717a;
  margin: 0 0 32px 0;
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

.form-footer {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 24px;
}

.form-link {
  font-size: 13px;
  color: #71717a;
  text-decoration: none;
  transition: color 0.2s;

  &:hover {
    color: #18181b;
  }
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 14px;
  font-weight: 600;
  background: #18181b;
  border-color: #18181b;
  border-radius: 10px;
  transition: all 0.2s;

  &:hover {
    background: #27272a;
    border-color: #27272a;
  }
}

@media (max-width: 768px) {
  .auth-card {
    grid-template-columns: 1fr;
    max-width: 420px;
  }

  .auth-brand {
    display: none;
  }

  .auth-form-wrapper {
    padding: 40px 24px;
  }
}
</style>
