<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-copy">
        <div class="eyebrow">Intelligent BI</div>
        <h1>极简后台模板</h1>
        <p>快速开发属于自己的前端项目。登录后进入后台管理页，支持用户管理、注册和权限控制。</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="auth-form"
        @keyup.enter="handleLogin"
      >
        <div class="form-title">账户密码登录</div>
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
        <div class="auth-links">
          <router-link to="/user/register">新用户注册</router-link>
        </div>
        <el-button
          type="primary"
          size="large"
          class="action-btn"
          :loading="loading"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { userLogin } from '@/api/userController'

const router = useRouter()
const route = useRoute()
const userStore = useLoginUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const rules: FormRules = {
  userAccount: [{ required: true, message: '账号是必填项！', trigger: 'blur' }],
  userPassword: [{ required: true, message: '密码是必填项！', trigger: 'blur' }],
}

const handleLogin = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const res = await userLogin(form)
      if (!res.data) {
        ElMessage.error('登录返回数据为空')
        return
      }

      userStore.setLoginUser(res.data)
      ElMessage.success('登录成功')
      const role = res.data?.userRole ?? 'user'
      const redirect =
        (route.query.redirect as string) || (role === 'admin' ? '/admin/userManage' : '/')
      router.replace(redirect)
    } catch (error: unknown) {
      ElMessage.error(error instanceof Error ? error.message : '登录失败')
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
