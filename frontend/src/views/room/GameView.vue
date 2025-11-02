<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePlayerStore } from '@/stores/player'
import { useToast } from 'primevue/usetoast'
import { useBreakpoints } from '@vueuse/core'

// 🔥 导入组件
import GameHeader from '@/components/game/GameHeader.vue'
import GameContent from '@/components/game/GameContent.vue'
import MobileChatDrawer from '@/components/game/MobileChatDrawer.vue'
import ChatRoom from '@/components/chat/ChatRoom.vue'

// 🔥 导入 composables
import { useGameCountdown } from '@/composables/game/useGameCountdown'
import { useGameSubmit } from '@/composables/game/useGameSubmit'
import { useGameKeyboard } from '@/composables/game/useGameKeyboard'
import { useGameWebSocket } from '@/composables/game/useGameWebSocket'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const playerStore = usePlayerStore()

const breakpoints = useBreakpoints({
  mobile: 0,
  tablet: 768,
  desktop: 1024,
})
const isMobile = breakpoints.smaller('tablet')

// 基础状态
const roomCode = ref(route.params.roomId)
const room = ref(null)
const question = ref(null)
const showChat = ref(!isMobile.value)
const unreadCount = ref(0)
const hasUnreadMessages = computed(() => unreadCount.value > 0)

// 计算属性
const currentQuestionIndex = computed(() => {
  if (!room.value) return 0
  return (room.value.currentIndex ?? 0) + 1
})

const totalQuestions = computed(() => {
  return room.value?.questionCount || 0
})

const submittedPlayers = computed(() => {
  if (!room.value?.players) return 0
  return room.value.players.filter(p => p.ready).length
})

const totalPlayers = computed(() => {
  return room.value?.players?.length || 0
})

// 🔥 使用 components
const {
  hasSubmitted,
  handleChoose,
  handleAutoSubmit,
  resetSubmitState,
  restoreSubmitState,
  cleanupSubmission,
  getSubmissionKey
} = useGameSubmit(roomCode, playerStore, toast, question, room)

const {
  questionStartTime,
  timeLimit,
  countdown,
  resetCountdown,
  clearCountdown
} = useGameCountdown(handleAutoSubmit)

// 🔥 传递 isSpectator 防止观战者通过键盘提交
useGameKeyboard(showChat, hasSubmitted, question, computed(() => playerStore.isSpectator))

const { connectWebSocket, wsConnected } = useGameWebSocket(
  roomCode,
  playerStore,
  toast,
  router,
  room,
  question,
  questionStartTime,
  timeLimit,
  resetCountdown,
  clearCountdown,
  resetSubmitState,
  restoreSubmitState,
  getSubmissionKey
)

// 聊天相关
watch(showChat, (newVal) => {
  if (newVal) {
    unreadCount.value = 0
  }
})

const handleNewMessage = (message) => {
  if (!showChat.value) {
    unreadCount.value++
  }
}

const toggleChat = () => {
  showChat.value = !showChat.value
}

// 生命周期
onMounted(() => {
  if (!playerStore.isLoggedIn) {
    toast.add({
      severity: 'error',
      summary: '错误',
      detail: '请先登录',
      life: 3000
    })
    router.push('/login')
    return
  }

  const savedRoom = playerStore.loadRoom()
  if (savedRoom) {
    room.value = savedRoom
    question.value = savedRoom.currentQuestion

    if (question.value) {
      restoreSubmitState()
    }

    // 🔥 改进：验证时间合理性后再恢复倒计时
    if (savedRoom.currentQuestion && savedRoom.questionStartTime) {
      const startTime = new Date(savedRoom.questionStartTime)
      const elapsed = (Date.now() - startTime.getTime()) / 1000
      const limit = savedRoom.timeLimit || 30

      // 只有在合理时间范围内才恢复倒计时（时间未到且未超时）
      if (elapsed < limit && elapsed >= 0) {
        questionStartTime.value = startTime
        timeLimit.value = limit
        resetCountdown()
      } else {
        console.warn('倒计时时间不合理，已跳过恢复:', { elapsed, limit })
      }
    }
  }

  connectWebSocket()
})

onUnmounted(() => {
  clearCountdown()
  cleanupSubmission()
})
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-3 sm:p-6">
    <!-- 连接状态 -->
    <div class="fixed top-3 right-3 sm:top-6 sm:right-6 z-50">
      <div class="px-2 sm:px-3 py-1 sm:py-1.5 rounded-full text-xs font-medium border"
           :class="wsConnected
             ? 'bg-green-50 text-green-700 border-green-200 dark:bg-green-900/20 dark:text-green-400 dark:border-green-800'
             : 'bg-red-50 text-red-700 border-red-200 dark:bg-red-900/20 dark:text-red-400 dark:border-red-800'">
        <i :class="wsConnected ? 'pi pi-check-circle' : 'pi pi-exclamation-circle'"></i>
        <span class="hidden sm:inline ml-1">
          {{ wsConnected ? '已连接' : '连接中' }}
        </span>
      </div>
    </div>

    <div class="max-w-7xl mx-auto">
      <div class="grid gap-4 sm:gap-6"
           :class="showChat && !isMobile ? 'lg:grid-cols-[1fr_400px]' : 'lg:grid-cols-1'">

        <!-- 游戏主区域 -->
        <div class="space-y-4 sm:space-y-6">
          <!-- 顶部信息栏 -->
          <GameHeader
            :roomCode="roomCode"
            :currentQuestionIndex="currentQuestionIndex"
            :totalQuestions="totalQuestions"
            :countdown="countdown"
            :submittedPlayers="submittedPlayers"
            :totalPlayers="totalPlayers"
            :showChat="showChat"
            :hasUnreadMessages="hasUnreadMessages"
            @toggleChat="toggleChat"
          />

          <!-- 游戏内容 -->
          <GameContent
            :question="question"
            :hasSubmitted="hasSubmitted"
            @choose="handleChoose"
          />
        </div>

        <!-- PC 端聊天 -->
        <transition name="slide">
          <div v-show="showChat && !isMobile" class="hidden lg:block">
            <ChatRoom
              v-if="roomCode"
              :roomCode="roomCode"
              :playerId="playerStore.playerId"
              :playerName="playerStore.playerName"
              @newMessage="handleNewMessage"
            />
          </div>
        </transition>
      </div>
    </div>

    <!-- 移动端聊天抽屉 -->
    <MobileChatDrawer
      :show="showChat && isMobile"
      :roomCode="roomCode"
      :playerId="playerStore.playerId"
      :playerName="playerStore.playerName"
      @newMessage="handleNewMessage"
      @close="toggleChat"
    />
  </div>
</template>

<style scoped>
.slide-enter-active, .slide-leave-active {
  transition: all 0.3s;
}
.slide-enter-from, .slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
</style>