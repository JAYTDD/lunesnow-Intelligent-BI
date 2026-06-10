<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-copy">
        <div class="eyebrow">Create Account</div>
        <h1>注册一个账号</h1>
        <p>注册完成后即可登录使用模板。页面结构与登录页保持统一，便于后续扩展。</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="auth-form"
        @keyup.enter="handleRegister"
      >
        <div class="form-title">注册</div>
        <el-form-item prop="userAccount">
          <el-input
            v-model="form.userAccount"
            placeholder="请输入账号"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>
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
        <div class="auth-links">
          <router-link to="/user/login">返回登录</router-link>
        </div>
        <el-button
          type="primary"
          size="large"
          class="action-btn"
          :loading="loading"
          @click="handleRegister"
        >
          注册
        </el-button>
      </el-form>
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
  min-height: 100vh;
  padding: 24px;
  background: linear-gradient(180deg, #f6f8fb 0%, #eef2f7 100%);
}

.auth-card {
  width: min(1040px, 100%);
  margin: auto;
  display: flex;
  gap: 32px;
  align-items: stretch;
  padding: 28px;
  border: 1px solid #eceef2;
  border-radius: 24px;
  background: #fff;
  box-shadow: 0 20px 60px rgba(17, 24, 39, 0.08);
}

.auth-copy {
  flex: 1;
  padding: 24px;
  background: linear-gradient(180deg, #fafafa 0%, #fff 100%);
  border-radius: 18px;
}

.eyebrow {
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #8c8c8c;
  margin-bottom: 14px;
}

h1 {
  font-size: 42px;
  line-height: 1.1;
  margin: 0 0 14px;
  color: #111827;
}

p {
  max-width: 420px;
  color: #6b7280;
  line-height: 1.8;
}

.auth-form {
  width: min(380px, 100%);
  align-self: center;
  padding: 12px 0;
}

.form-title {
  font-size: 18px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 20px;
}

.auth-links {
  display: flex;
  justify-content: flex-end;
  margin: 2px 0 20px;
}

.auth-links a {
  color: #5f6368;
  text-decoration: none;
  font-size: 14px;
  transition: color 0.2s;

  &:hover {
    color: #409eff;
  }
}

.action-btn {
  width: 100%;
  font-weight: 600;
  letter-spacing: 0.3px;
}

@media (max-width: 900px) {
  .auth-card {
    flex-direction: column;
  }

  h1 {
    font-size: 32px;
  }
}
</style>
