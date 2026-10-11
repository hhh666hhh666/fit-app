import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'

// uni-app 的入口约定：必须 createSSRApp + 导出 createApp，不能像纯 Vue 那样 mount('#app')
export function createApp() {
  const app = createSSRApp(App)
  app.use(createPinia())
  return { app }
}
