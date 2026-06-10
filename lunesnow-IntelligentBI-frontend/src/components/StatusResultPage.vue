<template>
  <div class="status-page">
    <el-result icon="warning" :title="title" :sub-title="subTitle">
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </template>
    </el-result>
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
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(180deg, #f6f8fb 0%, #eef2f7 100%);
}
</style>
