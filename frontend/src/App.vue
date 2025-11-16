<script setup>
import { logger } from '@/utils/logger'
import { useToast } from 'primevue/usetoast'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { usePlayerStore } from '@/stores/player'
import { useChatStore } from '@/stores/chat'
import WebSocketStatus from './components/common/WebSocketStatus.vue'
import ChatRoom from './components/chat/ChatRoom.vue'
import MobileChatDrawer from './components/game/MobileChatDrawer.vue'
import { TOAST_DEBOUNCE_TIME, TOAST_CLEANUP_DELAY, TOAST_DEFAULT_LIFE, ROOM_DATA_EXPIRY_TIME } from '@/config/constants'
import { connect, disconnect, isConnected } from '@/websocket/ws'
import { useBreakpoints } from '@vueuse/core'

const toast = useToast()
const playerStore = usePlayerStore()
const chatStore = useChatStore()

const breakpoints = useBreakpoints({
  mobile: 0,
  tablet: 768,
  desktop: 1024,
})
const isDesktop = breakpoints.greaterOrEqual('desktop')

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

// 🔥 全局 WebSocket 连接管理
const connectGlobalWebSocket = async () => {
  // 只有在有 playerId 时才连接
  if (!playerStore.playerId) {
    logger.debug('App: 没有 playerId，跳过 WebSocket 连接')
    return
  }

  // 如果已经连接，不重复连接
  if (isConnected()) {
    logger.debug('App: WebSocket 已连接')
    return
  }

  try {
    await connect(playerStore.playerId)
    logger.debug('App: 全局 WebSocket 连接成功')
  } catch (err) {
    logger.error('App: 全局 WebSocket 连接失败', err)
  }
}

// 监听 playerId 变化，自动建立连接
watch(() => playerStore.playerId, (newId, oldId) => {
  if (newId && newId !== oldId) {
    logger.debug('App: playerId 变化，建立 WebSocket 连接')
    connectGlobalWebSocket()
  }
}, { immediate: true })

onMounted(() => {
  // 注册全局事件监听
  window.addEventListener('api-error', handleApiError)
  window.addEventListener('websocket-error', handleWebSocketError)
  window.addEventListener('room-deleted', handleRoomDeleted)
  window.addEventListener('websocket-welcome', handleWelcome)
  window.addEventListener('vue-error', handleVueError)

  // 🔥 清理过期数据
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

  // 🔥 建立全局 WebSocket 连接
  connectGlobalWebSocket()
})

onUnmounted(() => {
  window.removeEventListener('api-error', handleApiError)
  window.removeEventListener('websocket-error', handleWebSocketError)
  window.removeEventListener('room-deleted', handleRoomDeleted)
  window.removeEventListener('websocket-welcome', handleWelcome)
  window.removeEventListener('vue-error', handleVueError)

  // 🔥 应用卸载时断开连接
  disconnect()
})
</script>

<template>
  <WebSocketStatus />
  <Toast />

  <router-view />

  <!-- 🔥 全局ChatRoom - 桌面端固定在右侧 -->
  <teleport to="body">
    <transition name="slide-left">
      <div v-show="chatStore.visible && chatStore.roomCode && isDesktop"
           class="fixed top-0 right-0 h-screen w-[400px] z-[60] shadow-2xl">
        <ChatRoom
          v-if="chatStore.roomCode"
          :roomCode="chatStore.roomCode"
          :playerId="playerStore.playerId"
          :playerName="playerStore.playerName"
          @close="chatStore.hideChat"
        />
      </div>
    </transition>
  </teleport>

  <!-- 🔥 全局ChatRoom - 移动端抽屉 -->
  <MobileChatDrawer
    v-if="chatStore.roomCode"
    :show="chatStore.visible && !isDesktop"
    :roomCode="chatStore.roomCode"
    :playerId="playerStore.playerId"
    :playerName="playerStore.playerName"
    @close="chatStore.hideChat"
  />
</template>

<style scoped>
.slide-left-enter-active, .slide-left-leave-active {
  transition: transform 0.3s ease;
}
.slide-left-enter-from, .slide-left-leave-to {
  transform: translateX(100%);
}
</style>