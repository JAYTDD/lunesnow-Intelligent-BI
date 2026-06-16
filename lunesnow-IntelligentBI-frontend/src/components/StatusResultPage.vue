<template>
  <div class="status-page">
    <div class="status-card">
      <div class="status-icon">
        <slot name="icon">
          <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
            <circle cx="32" cy="32" r="32" fill="#f4f4f5"/>
            <path d="M32 20V36M32 40V44" stroke="#a1a1aa" stroke-width="3" stroke-linecap="round"/>
          </svg>
        </slot>
      </div>
      <h1 class="status-title">{{ title }}</h1>
      <p class="status-subtitle">{{ subTitle }}</p>
      <button class="status-btn" @click="goHome">返回首页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

defineProps<{
  title: string
  subTitle: string
}>()

const router = useRouter()

const goHome = () => {
  router.push('/')
}
</script>

<style lang="scss" scoped>
.status-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background: #fafafa;
}

.status-card {
  text-align: center;
  max-width: 400px;
}

.status-icon {
  margin-bottom: 24px;
}

.status-title {
  font-size: 72px;
  font-weight: 800;
  color: #18181b;
  margin: 0 0 12px 0;
  letter-spacing: -3px;
  line-height: 1;
}

.status-subtitle {
  font-size: 15px;
  color: #71717a;
  margin: 0 0 32px 0;
  line-height: 1.6;
}

.status-btn {
  display: inline-flex;
  align-items: center;
  padding: 12px 28px;
  background: #18181b;
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: #27272a;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(24, 24, 27, 0.2);
  }

  &:active {
    transform: translateY(0);
  }
}
</style>
