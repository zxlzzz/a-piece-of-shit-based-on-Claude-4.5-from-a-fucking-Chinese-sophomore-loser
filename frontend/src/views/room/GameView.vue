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

const { connectWebSocket } = useGameWebSocket(
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
    
    if (savedRoom.currentQuestion && savedRoom.questionStartTime) {
      questionStartTime.value = new Date(savedRoom.questionStartTime)
      timeLimit.value = savedRoom.timeLimit || 30
      resetCountdown()
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