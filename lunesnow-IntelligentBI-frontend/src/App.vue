<script setup lang="ts">
</script>

<template>
  <el-config-provider :size="'default'">
    <router-view />
  </el-config-provider>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body,
#app {
  height: 100%;
}

body {
  font-family: Inter, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: #111827;
  background: #f7f8fa;
}

a {
  color: inherit;
  text-decoration: none;
}
</style>
