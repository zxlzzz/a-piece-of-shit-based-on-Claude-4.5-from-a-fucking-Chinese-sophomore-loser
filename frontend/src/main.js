import { createPinia } from 'pinia'; // 新增
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

import 'primeicons/primeicons.css'
import './assets/tailwind.css'

import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import Sidebar from 'primevue/sidebar'
import Toast from 'primevue/toast'

const app = createApp(App)
const pinia = createPinia() // 新增

app.use(pinia) // 新增，必须在 router 前
app.use(router)
app.use(PrimeVue, { unstyled: false })
app.use(ToastService)

app.component('Button', Button)
app.component('Toast', Toast)
app.component('Sidebar', Sidebar)
app.component('InputNumber', InputNumber)

// 🔥 全局错误处理（Vue 运行时错误）
app.config.errorHandler = (err, instance, info) => {
  console.error('[Vue Error]', err, info)

  // 开发环境：不拦截，让错误在控制台显示
  if (import.meta.env.DEV) {
    return
  }

  // 生产环境：显示友好提示
  window.dispatchEvent(new CustomEvent('vue-error', {
    detail: {
      message: '页面出现异常，请刷新重试',
      error: err.message,
      info
    }
  }))
}

app.mount('#app')