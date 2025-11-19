<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePlayerStore } from '@/stores/player'
import { useChatStore } from '@/stores/chat'
import { useToast } from 'primevue/usetoast'
import { useBreakpoints } from '@vueuse/core'
import { logger } from '@/utils/logger'

// 🔥 导入组件
import GameHeader from '@/components/game/GameHeader.vue'
import GameContent from '@/components/game/GameContent.vue'

// 🔥 导入 composables
import { useGameCountdown } from '@/composables/game/useGameCountdown'
import { useGameSubmit } from '@/composables/game/useGameSubmit'
import { useGameKeyboard } from '@/composables/game/useGameKeyboard'
import { useGameWebSocket } from '@/composables/game/useGameWebSocket'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const playerStore = usePlayerStore()
const chatStore = useChatStore()

const breakpoints = useBreakpoints({
  mobile: 0,
  tablet: 768,
  desktop: 1024,
})
const isMobile = breakpoints.smaller('desktop')
const isDesktop = breakpoints.greaterOrEqual('desktop')

// 基础状态
const roomCode = ref(route.params.roomId)
const room = ref(null)
const question = ref(null)

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
  getSubmissionKey,
  verifySubmissionState  // 🔥 P1-1: 验证提交状态
} = useGameSubmit(roomCode, playerStore, toast, question, room)

const {
  questionStartTime,
  timeLimit,
  countdown,
  resetCountdown,
  clearCountdown
} = useGameCountdown(handleAutoSubmit)

// 🔥 传递 isSpectator 防止观战者通过键盘提交
useGameKeyboard(computed(() => chatStore.visible), hasSubmitted, question, computed(() => playerStore.isSpectator))

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
  getSubmissionKey,
  verifySubmissionState  // 🔥 P1-1: 传递验证函数
)

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

  // 🔥 修复：清理所有旧的submission记录（游戏开始时）
  // 遍历localStorage，删除所有submission_${roomCode}_*的记录
  const submissionPrefix = `submission_${roomCode.value}_`
  const keysToRemove = []
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (key && key.startsWith(submissionPrefix)) {
      keysToRemove.push(key)
    }
  }
  keysToRemove.forEach(key => {
    localStorage.removeItem(key)
    logger.debug('🧹 清理旧的提交记录:', key)
  })

  const savedRoom = playerStore.loadRoom()
  if (savedRoom) {
    room.value = savedRoom
    question.value = savedRoom.currentQuestion

    // 🔥 新增：如果游戏已经结束，自动跳转到结果页面
    if (savedRoom.status === 'FINISHED' || savedRoom.finished === true) {
      toast.add({
        severity: 'info',
        summary: '游戏已结束',
        detail: '正在跳转到结果页面...',
        life: 2000
      })
      router.push(`/result/${roomCode.value}`)
      return
    }

    // 🔥 修复：游戏开始时不恢复提交状态（因为已经清理了所有记录）
    // restoreSubmitState() 会在WebSocket更新时根据实际情况恢复
    // if (question.value) {
    //   restoreSubmitState()
    // }
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
        logger.warn('倒计时时间不合理，已跳过恢复:', { elapsed, limit })
      }
    }
  },

  connectWebSocket()
)

onUnmounted(() => {
  clearCountdown()
  cleanupSubmission()

  // 🔥 离开房间时不清除ChatRoom，让聊天历史持续到下一个页面
  // chatStore.clearChat()
})
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-3 sm:p-6 transition-[padding] duration-300 ease-in-out"
       :class="chatStore.visible && isDesktop ? 'pr-[420px]' : ''">
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

    <div class="max-w-4xl mx-auto">
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
          :showChat="chatStore.visible"
          :hasUnreadMessages="chatStore.unreadPrivateCount > 0"
          :chatEnabled="room?.chatEnabled ?? true"
          @toggleChat="chatStore.toggleChat(isMobile)"
        />

        <!-- 🔥 新增：观战模式提示 -->
        <div v-if="playerStore.isSpectator"
             class="bg-purple-50 dark:bg-purple-900/20 border border-purple-200
                    dark:border-purple-800 rounded-lg p-3 sm:p-4 text-center">
          <i class="pi pi-eye text-purple-600 dark:text-purple-400"></i>
          <span class="ml-2 text-sm sm:text-base text-purple-700 dark:text-purple-400 font-medium">
            观战模式 - 您可以观看但不能答题
          </span>
        </div>

        <!-- 游戏内容 -->
        <GameContent
          :question="question"
          :hasSubmitted="hasSubmitted"
          :room="room"
          @choose="handleChoose"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 样式已移除，聊天室在全局App.vue中管理 */
</style>