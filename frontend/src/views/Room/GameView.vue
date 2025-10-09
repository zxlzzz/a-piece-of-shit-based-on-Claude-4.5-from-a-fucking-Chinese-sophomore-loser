<script setup>
import { usePlayerStore } from '@/stores/player'
import { generatePlayerColor } from '@/utils/player'
import { connect, isConnected, sendSubmit, subscribeRoom, unsubscribeAll } from '@/websocket/ws'
import { useToast } from 'primevue/usetoast'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ChatRoom from './ChatRoom.vue'
import QuestionCard from './QuestionCard.vue'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const roomCode = ref(route.params.roomId)
const playerStore = usePlayerStore()
const room = ref(null)
const question = ref(null)
const subscriptions = ref([])
const hasSubmitted = ref(false)
const showChat = ref(true)

const questionStartTime = ref(null)
const timeLimit = ref(30)
const countdown = ref(30)
const countdownTimer = ref(null)

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

onMounted(() => {
  // 🔥 改用 Pinia
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

  // 🔥 统一用 playerStore 加载房间
  const savedRoom = playerStore.loadRoom()
  if (savedRoom) {
    room.value = savedRoom
    question.value = savedRoom.currentQuestion
    
    if (savedRoom.currentQuestion && savedRoom.questionStartTime) {
      questionStartTime.value = new Date(savedRoom.questionStartTime)
      timeLimit.value = savedRoom.timeLimit || 30
      resetCountdown()
    }
  }

  connectWebSocket()
})

onUnmounted(() => {
  if (subscriptions.value.length > 0) {
    unsubscribeAll(subscriptions.value)
  }
  clearCountdown()
})

const connectWebSocket = async () => {
  // 🔥 检查 WebSocket 状态
  if (!isConnected()) {
    console.warn('⚠️ GameView: WebSocket 未连接，尝试连接...')
    
    try {
      // 🔥 等待连接完成
      await connect(playerStore.playerId)
      console.log('✅ GameView: WebSocket 连接成功')
    } catch (err) {
      console.error('❌ GameView: WebSocket 连接失败', err)
      toast.add({
        severity: 'error',
        summary: '连接失败',
        detail: 'WebSocket 连接失败，请刷新页面',
        life: 5000
      })
      return
    }
  }
  
  // 🔥 连接成功后，开始订阅
  setupRoomSubscription()
}

const setupRoomSubscription = () => {
  const subs = subscribeRoom(
    roomCode.value,
    (update) => {
      console.log("房间更新:", update)
      
      // 🔥 改这里：用 id 而不是 playerId
      const oldQuestionId = question.value?.id
      const newQuestionId = update.currentQuestion?.id
      
      room.value = update
      
      if (newQuestionId && oldQuestionId !== newQuestionId) {
        // 🔥 题目切换了，重置状态
        clearCountdown()
        hasSubmitted.value = false  // 🔥 关键：重置提交状态
        question.value = update.currentQuestion
        
        if (update.questionStartTime) {
          questionStartTime.value = new Date(update.questionStartTime)
          timeLimit.value = update.timeLimit || 30
          resetCountdown()
        }
      } else {
        // 🔥 同一题，只更新数据
        question.value = update.currentQuestion
      }
      
      // 统一用 playerStore 存储
      playerStore.setRoom(update)
      
      if (update.finished || update.status === 'FINISHED') {
        clearCountdown()
        toast.add({
          severity: 'info',
          summary: '游戏结束',
          detail: '正在跳转到结果页面...',
          life: 2000
        })
        setTimeout(() => {
          router.push(`/result/${roomCode.value}`)
        }, 1000)
      }
    },
    (error) => {
      console.error('房间错误:', error)
      toast.add({
        severity: 'error',
        summary: '房间错误',
        detail: error.error || '房间出现错误',
        life: 3000
      })
    }
  )
  
  subscriptions.value = subs
}

const resetCountdown = () => {
  clearCountdown()
  startCountdown()
}

const startCountdown = () => {
  updateCountdown()
  countdownTimer.value = setInterval(() => {
    updateCountdown()
  }, 100)
}

const updateCountdown = () => {
  if (!questionStartTime.value) {
    countdown.value = timeLimit.value
    return
  }
  
  const now = new Date()
  const elapsed = Math.floor((now - questionStartTime.value) / 1000)
  const remaining = Math.max(0, timeLimit.value - elapsed)
  
  countdown.value = remaining
  
  if (remaining <= 0) {
    clearCountdown()
    if (!hasSubmitted.value && question.value) {
      handleAutoSubmit()
    }
  }
}

const clearCountdown = () => {
  if (countdownTimer.value) {
    clearInterval(countdownTimer.value)
    countdownTimer.value = null
  }
}

const handleChoose = (choice) => {
  if (hasSubmitted.value) {
    toast.add({
      severity: 'warn',
      summary: '提示',
      detail: '您已经提交过答案了',
      life: 2000
    })
    return
  }
  
  // 🔥 改用 playerStore
  sendSubmit({ 
    roomCode: roomCode.value, 
    playerId: playerStore.playerId, 
    choice: choice.toString()
  })
  
  hasSubmitted.value = true
  
  toast.add({
    severity: 'success',
    summary: '提交成功',
    detail: '已提交答案',
    life: 2000
  })
}

const handleAutoSubmit = () => {
  if (!question.value) return
  
  let defaultChoice
  if (question.value.type === 'choice') {
    defaultChoice = question.value.options?.[0]?.key || 'A'
  } else if (question.value.type === 'bid') {
    defaultChoice = question.value.min || 0
  }
  
  // 🔥 改用 playerStore
  sendSubmit({ 
    roomCode: roomCode.value, 
    playerId: playerStore.playerId, 
    choice: defaultChoice.toString(),
    force: true
  })
  
  hasSubmitted.value = true
  
  toast.add({
    severity: 'info',
    summary: '自动提交',
    detail: '时间到，已自动提交默认答案',
    life: 3000
  })
}

const toggleChat = () => {
  showChat.value = !showChat.value
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-6">
    
    <div class="max-w-7xl mx-auto">
      <div class="grid gap-6"
           :class="showChat ? 'lg:grid-cols-[1fr_400px]' : 'lg:grid-cols-1'">
        
        <!-- 游戏主区域 -->
        <div class="space-y-6">
          
          <!-- 顶部信息栏 -->
          <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
            <div class="flex items-center justify-between flex-wrap gap-4">
              <!-- 左侧 -->
              <div class="flex items-center gap-4 flex-wrap">
                <h1 class="text-xl font-semibold text-gray-900 dark:text-white">
                  {{ roomCode }}
                </h1>
                <div class="px-3 py-1 bg-blue-50 dark:bg-blue-900/20 
                            text-blue-700 dark:text-blue-300 rounded-md text-sm font-medium">
                  {{ currentQuestionIndex }}/{{ totalQuestions }}
                </div>
              </div>
              
              <!-- 右侧 -->
              <div class="flex items-center gap-3 flex-wrap">
                <!-- 倒计时 -->
                <div class="px-3 py-1 rounded-md font-semibold text-sm"
                     :class="countdown <= 10 
                       ? 'bg-red-50 text-red-700 dark:bg-red-900/20 dark:text-red-400' 
                       : 'bg-green-50 text-green-700 dark:bg-green-900/20 dark:text-green-400'">
                  {{ countdown }}s
                </div>
                
                <!-- 提交状态 -->
                <div class="px-3 py-1 bg-gray-100 dark:bg-gray-700 
                            text-gray-700 dark:text-gray-300 rounded-md text-sm">
                  {{ submittedPlayers }}/{{ totalPlayers }}
                </div>
                
                <!-- 聊天切换 -->
                <button 
                  @click="toggleChat"
                  class="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 
                         rounded-lg transition-colors"
                >
                  <i :class="showChat ? 'pi pi-times' : 'pi pi-comment'" 
                     class="text-gray-600 dark:text-gray-400"></i>
                </button>
              </div>
            </div>
          </div>

          <!-- 玩家状态栏 -->
          <div v-if="room?.players" 
               class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4 overflow-x-auto">
            <div class="flex gap-3">
              <div
                v-for="player in room.players"
                :key="player.playerId"
                class="flex flex-col items-center gap-2 p-2 rounded-lg min-w-[80px]"
               :class="[
                  player.ready 
                    ? 'bg-green-50 dark:bg-green-900/10' 
                    : 'bg-gray-50 dark:bg-gray-700/50',
                  player.playerId === playerStore.playerId
                    ? 'ring-1 ring-blue-500 dark:ring-blue-600'
                    : ''
                ]"
              >
                <div class="w-10 h-10 rounded-full bg-gray-200 dark:bg-gray-600 
                            flex items-center justify-center text-sm font-medium"
                     :style="{ backgroundColor: generatePlayerColor(player.playerId) + '20', 
                               color: generatePlayerColor(player.playerId) }">
                  {{ player.name.charAt(0).toUpperCase() }}
                </div>
                <div class="text-xs font-medium text-gray-700 dark:text-gray-300 text-center truncate max-w-[70px]">
                  {{ player.name }}
                </div>
                <i class="text-sm"
                   :class="player.ready 
                     ? 'pi pi-check-circle text-green-600 dark:text-green-400' 
                     : 'pi pi-clock text-gray-400'">
                </i>
              </div>
            </div>
          </div>

          <!-- 题目卡片 -->
          <div class="flex justify-center">
            <QuestionCard
              v-if="question"
              :question="question"
              :disabled="hasSubmitted"
              @choose="handleChoose"
            />
            
            <div v-else 
                 class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-12 text-center">
              <i class="pi pi-spin pi-spinner text-4xl text-gray-400 mb-3"></i>
              <p class="text-gray-600 dark:text-gray-400">等待下一题</p>
            </div>
          </div>

          <!-- 已提交提示 -->
          <transition name="fade">
            <div v-if="hasSubmitted" 
                 class="fixed bottom-6 left-1/2 -translate-x-1/2 z-50
                        bg-green-600 text-white px-5 py-2.5 rounded-lg text-sm font-medium">
                已提交
            </div>
          </transition>
        </div>

        <!-- 聊天区域 -->
        <transition name="slide">
          <div v-if="showChat">
            <ChatRoom
              v-if="roomCode"
              :roomCode="roomCode"
              :playerId="playerStore.playerId"
              :playerName="playerStore.playerName"
            />
          </div>
        </transition>
      </div>
    </div>
  </div>
</template>

<style scoped>
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

.slide-enter-active, .slide-leave-active {
  transition: all 0.3s;
}
.slide-enter-from, .slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
</style>