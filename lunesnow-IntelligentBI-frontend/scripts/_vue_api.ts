// 桥接文件：重新导出真实 vue 的 API，供浏览器 Console 动态 import 使用。
// 因为浏览器原生 import('vue') 无法解析裸说明符，而本项目内的 .ts 会被 Vite 正确转译。
export { createApp, h } from 'vue'
