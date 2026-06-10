<template>
  <div class="basic-layout">
    <el-container class="shell">
      <el-header class="header">
        <GlobalHeader />
      </el-header>
      <el-container class="body">
        <el-aside class="sider" width="220px">
          <GlobalSider />
        </el-aside>
        <el-main class="content">
          <router-view />
        </el-main>
      </el-container>
      <el-footer class="footer">Intelligent BI &copy; 2026</el-footer>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import GlobalHeader from '@/components/layout/GlobalHeader.vue'
import GlobalSider from '@/components/layout/GlobalSider.vue'
</script>

<style lang="scss" scoped>
.basic-layout {
  min-height: 100vh;
}

.shell {
  min-height: 100vh;
  background: #f0f2f5;
}

.header {
  height: auto;
  min-height: 64px;
  padding: 0;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  position: sticky;
  top: 0;
  z-index: 100;
}

.body {
  flex: 1;
  align-items: flex-start;
}

.sider {
  position: sticky;
  top: 64px;
  height: calc(100vh - 64px);
  overflow-y: auto;
  background: #fff;
  box-shadow: 2px 0 8px 0 rgba(29, 35, 41, 0.05);
  z-index: 90;
}

.content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.footer {
  height: auto;
  padding: 24px;
  background: transparent;
  border-top: none;
  text-align: center;
  color: #8c8c8c;
  font-size: 14px;
}

@media (max-width: 768px) {
  .content {
    padding: 16px;
  }
  .footer {
    padding: 16px;
  }
}
</style>
