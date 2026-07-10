import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/global-override.scss'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import './access'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.use(createPinia())
app.use(router)

app.use(ElementPlus)

// 全局错误兜底：防止未被 onErrorCaptured 拦截的渲染错误导致白屏
app.config.errorHandler = (err, instance, info) => {
  console.error('[全局错误兜底]', err, info)
  // 可选：上报到监控系统
}

app.mount('#app')
