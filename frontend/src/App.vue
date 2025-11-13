<script setup>
import { logger } from '@/utils/logger'
import { useToast } from 'primevue/usetoast'
import { onMounted, onUnmounted, ref } from 'vue'
import WebSocketStatus from './components/common/WebSocketStatus.vue'
import { TOAST_DEBOUNCE_TIME, TOAST_CLEANUP_DELAY, TOAST_DEFAULT_LIFE, ROOM_DATA_EXPIRY_TIME } from '@/config/constants'

const toast = useToast()

// 🔥 Toast 去重：记录最近显示的消息（key: message, value: timestamp）
const recentToasts = ref(new Map())

// 通用 Toast 显示函数（带去重）
const showToast = (severity, summary, detail, life = TOAST_DEFAULT_LIFE) => {
  const key = `${severity}-${summary}-${detail}`
  const now = Date.now()
  const lastTime = recentToasts.value.get(key)

  // 如果在去重时间窗口内显示过相同消息，忽略
  if (lastTime && now - lastTime < TOAST_DEBOUNCE_TIME) {
    return
  }

  toast.add({ severity, summary, detail, life })
  recentToasts.value.set(key, now)

  // 清理过期记录
  setTimeout(() => {
    recentToasts.value.delete(key)
  }, life + TOAST_CLEANUP_DELAY)
}

// 监听 API 错误（api.js 触发的）
const handleApiError = (event) => {
  const { message, status, isDev } = event.detail

  // 根据状态码调整严重程度
  const severity = status === 401 || status === 403 ? 'warn' : 'error'
  const summary = status === 401 ? '未登录' : status === 403 ? '无权限' : '请求失败'

  showToast(severity, summary, message)

  // 开发环境额外打印详情
  if (isDev) {
    logger.error('[API Error]', event.detail)
  }
}

// 监听 WebSocket 错误（ws.js 触发的）
const handleWebSocketError = (event) => {
  const { type, data, error } = event.detail

  if (type === 'personal' && data?.message) {
    showToast('error', 'WebSocket 错误', data.message, 4000)
  } else {
    showToast('warn', '连接异常', '实时连接出现问题，尝试重新连接...')
  }
}

// 监听房间删除（ws.js 触发的）
const handleRoomDeleted = (event) => {
  showToast('warn', '房间已关闭', '房主已关闭房间', 4000)
}

// 监听欢迎消息（ws.js 触发的）
const handleWelcome = (event) => {
  if (event.detail?.message) {
    showToast('info', '欢迎', event.detail.message, 2000)
  }
}

// 监听 Vue 运行时错误（main.js 触发的）
const handleVueError = (event) => {
  showToast('error', '页面异常', event.detail.message, 5000)
}

onMounted(() => {
  // 注册全局事件监听
  window.addEventListener('api-error', handleApiError)
  window.addEventListener('websocket-error', handleWebSocketError)
  window.addEventListener('room-deleted', handleRoomDeleted)
  window.addEventListener('websocket-welcome', handleWelcome)
  window.addEventListener('vue-error', handleVueError)

  // 🔥 新增：检查并清理过期数据
  try {
    const savedRoom = localStorage.getItem('currentRoom')
    if (savedRoom) {
      const roomData = JSON.parse(savedRoom)
      // 如果房间数据超过设定时间，清除
      if (roomData._savedAt && Date.now() - roomData._savedAt > ROOM_DATA_EXPIRY_TIME) {
        localStorage.removeItem('currentRoom')
      }
    }
  } catch (error) {
    logger.error('清理 localStorage 失败:', error)
    localStorage.removeItem('currentRoom')
  }
  // 🔥 新增：页面加载时清理旧连接状态（注册在 onMounted 而不是 window.load）
  import('@/websocket/ws').then(({ disconnect }) => {
    disconnect()
  })
})

onUnmounted(() => {
  window.removeEventListener('api-error', handleApiError)
  window.removeEventListener('websocket-error', handleWebSocketError)
  window.removeEventListener('room-deleted', handleRoomDeleted)
  window.removeEventListener('websocket-welcome', handleWelcome)
  window.removeEventListener('vue-error', handleVueError)
})
</script>

<template>
  <WebSocketStatus />
  <Toast />
  <router-view />
</template>

<style scoped></style>