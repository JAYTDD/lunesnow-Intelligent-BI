<template>
  <div class="basic-layout">
    <el-container class="shell">
      <el-aside class="sider" width="220px">
        <GlobalSider :connected="wsConnected" />
      </el-aside>
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { useWebSocket } from '@/composables/useWebSocket'
import GlobalSider from '@/components/layout/GlobalSider.vue'

// 建立 WebSocket 连接
const { connected: wsConnected } = useWebSocket()
</script>

<style lang="scss" scoped>
.basic-layout {
  min-height: 100vh;
}

.shell {
  min-height: 100vh;
  background: #fafafa;
}

.sider {
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
  background: #fff;
  border-right: 1px solid #e4e4e7;
}

.content {
  flex: 1;
  padding: 0;
  overflow-y: auto;
}

@media (max-width: 768px) {
  .content {
    padding: 0;
  }
}
</style>
