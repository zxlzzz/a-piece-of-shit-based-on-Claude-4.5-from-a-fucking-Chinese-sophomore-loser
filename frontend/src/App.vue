<script setup>
import { usePlayerStore } from '@/stores/player'
import { disconnect } from '@/websocket/ws'
import { useToast } from 'primevue/usetoast'
import { onMounted, onUnmounted } from 'vue'

const toast = useToast()
const playerStore = usePlayerStore()

// 监听 API 错误（api.js 触发的）
const handleApiError = (event) => {
  toast.add({
    severity: 'error',
    summary: '请求错误',
    detail: event.detail.message,
    life: 3000
  })
}

// 监听 WebSocket 错误（ws.js 触发的）
const handleWebSocketError = (event) => {
  const { type, data, error } = event.detail
  
  if (type === 'personal' && data?.message) {
    toast.add({
      severity: 'error',
      summary: 'WebSocket 错误',
      detail: data.message,
      life: 4000
    })
  } else {
    toast.add({
      severity: 'warn',
      summary: '连接异常',
      detail: '实时连接出现问题，尝试重新连接...',
      life: 3000
    })
  }
}

// 监听房间删除（ws.js 触发的）
const handleRoomDeleted = (event) => {
  toast.add({
    severity: 'warn',
    summary: '房间已关闭',
    detail: '房主已关闭房间',
    life: 4000
  })
}

// 监听欢迎消息（ws.js 触发的）
const handleWelcome = (event) => {
  if (event.detail?.message) {
    toast.add({
      severity: 'info',
      summary: '欢迎',
      detail: event.detail.message,
      life: 2000
    })
  }
}

onMounted(() => {
  const handleBeforeUnload = () => {
    console.log('🔄 页面即将刷新/关闭，断开 WebSocket')
    disconnect()
  }
  // 注册全局事件监听
  window.addEventListener('api-error', handleApiError)
  window.addEventListener('websocket-error', handleWebSocketError)
  window.addEventListener('room-deleted', handleRoomDeleted)
  window.addEventListener('websocket-welcome', handleWelcome)

  // 🔥 新增：检查并清理过期数据
  try {
    const savedRoom = localStorage.getItem('currentRoom')
    if (savedRoom) {
      const roomData = JSON.parse(savedRoom)
      // 如果房间数据超过1小时，清除
      if (roomData._savedAt && Date.now() - roomData._savedAt > 60 * 60 * 1000) {
        console.log('🧹 清理过期房间数据')
        localStorage.removeItem('currentRoom')
      }
    }
  } catch (error) {
    console.error('清理 localStorage 失败:', error)
    localStorage.removeItem('currentRoom')
  }
  // 🔥 新增：页面加载时清理旧连接状态（注册在 onMounted 而不是 window.load）
  import('@/websocket/ws').then(({ disconnect }) => {
    console.log('🧹 App 挂载：清理可能的旧连接')
    disconnect()
  })
})

onUnmounted(() => {

  window.removeEventListener('api-error', handleApiError)
  window.removeEventListener('websocket-error', handleWebSocketError)
  window.removeEventListener('room-deleted', handleRoomDeleted)
  window.removeEventListener('websocket-welcome', handleWelcome)
})
</script>

<template>
  <Toast />
  <router-view />
</template>

<style scoped></style>