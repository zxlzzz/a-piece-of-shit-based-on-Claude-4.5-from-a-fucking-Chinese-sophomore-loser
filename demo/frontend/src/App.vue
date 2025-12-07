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

const recentToasts = ref(new Map())

const showToast = (severity, summary, detail, life = TOAST_DEFAULT_LIFE) => {
  const key = `${severity}-${summary}-${detail}`
  const now = Date.now()
  const lastTime = recentToasts.value.get(key)

  
  if (lastTime && now - lastTime < TOAST_DEBOUNCE_TIME) {
    return
  }

  toast.add({ severity, summary, detail, life })
  recentToasts.value.set(key, now)

  setTimeout(() => {
    recentToasts.value.delete(key)
  }, life + TOAST_CLEANUP_DELAY)
}

const handleApiError = (event) => {
  const { message, status, isDev } = event.detail

  const severity = status === 401 || status === 403 ? 'warn' : 'error'
  const summary = status === 401 ? '未登录' : status === 403 ? '无权限' : '请求失败'

  showToast(severity, summary, message)

  if (isDev) {
    logger.error('[API Error]', event.detail)
  }
}

const handleWebSocketError = (event) => {
  const { type, data, error } = event.detail

  if (type === 'personal' && data?.message) {
    showToast('error', 'WebSocket 错误', data.message, 4000)
  } else {
    showToast('warn', '连接异常', '实时连接出现问题，尝试重新连接...')
  }
}

const handleRoomDeleted = (event) => {
  showToast('warn', '房间已关闭', '房主已关闭房间', 4000)
}

const handleWelcome = (event) => {
  if (event.detail?.message) {
    showToast('info', '欢迎', event.detail.message, 2000)
  }
}

const handleVueError = (event) => {
  showToast('error', '页面异常', event.detail.message, 5000)
}

// const connectGlobalWebSocket = async () => {
//     return
//   }
//
//   
//   if (isConnected()) {
//     return
//   }
//
//   try {
//   } catch (err) {
//   }
// }

// 不再全局连接，由各个页面按需连接
//   }
// }, { immediate: true })

onMounted(() => {
  window.addEventListener('api-error', handleApiError)
  window.addEventListener('websocket-error', handleWebSocketError)
  window.addEventListener('room-deleted', handleRoomDeleted)
  window.addEventListener('websocket-welcome', handleWelcome)
  window.addEventListener('vue-error', handleVueError)

  try {
    const savedRoom = localStorage.getItem('currentRoom')
    if (savedRoom) {
      const roomData = JSON.parse(savedRoom)
      const now = Date.now()
      const savedAt = roomData._savedAt || 0

      // 如果房间已结束，使用更短的过期时间（15秒）
      if (roomData.finished || roomData.status === 'FINISHED') {
        if (now - savedAt > 15000) {  // 15秒
          logger.info('🧹 已结束的房间缓存已过期，自动清理')
          localStorage.removeItem('currentRoom')
        }
      }
      else if (now - savedAt > ROOM_DATA_EXPIRY_TIME) {
        logger.info('🧹 房间缓存已过期，自动清理')
        localStorage.removeItem('currentRoom')
      }
    }
  } catch (error) {
    logger.error('清理 localStorage 失败:', error)
    localStorage.removeItem('currentRoom')
  }

  // connectGlobalWebSocket()
})

onUnmounted(() => {
  window.removeEventListener('api-error', handleApiError)
  window.removeEventListener('websocket-error', handleWebSocketError)
  window.removeEventListener('room-deleted', handleRoomDeleted)
  window.removeEventListener('websocket-welcome', handleWelcome)
  window.removeEventListener('vue-error', handleVueError)

  disconnect()
})
</script>

<template>
  <WebSocketStatus />
  <Toast />

  <router-view />

  <!--  全局ChatRoom - 桌面端固定在右侧 -->
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

  <!--  全局ChatRoom - 移动端抽屉 -->
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