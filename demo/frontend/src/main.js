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

//  全局错误处理（Vue 运行时错误）
app.config.errorHandler = (err, instance, info) => {
  // 开发环境：完整错误信息
  if (import.meta.env.DEV) {
    console.error('[Vue Error]', err, info)
    return
  }

  // 生产环境：仅显示简化错误信息，并触发友好提示
  console.error('[Vue Error]', err.message)
  window.dispatchEvent(new CustomEvent('vue-error', {
    detail: {
      message: '页面出现异常，请刷新重试',
      error: err.message,
      info
    }
  }))
}

app.mount('#app')