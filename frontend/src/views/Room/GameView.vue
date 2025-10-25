<script setup>
import { usePlayerStore } from '@/stores/player'
import { connect, isConnected, sendSubmit, subscribeRoom, unsubscribeAll } from '@/websocket/ws'
import { useToast } from 'primevue/usetoast'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ChatRoom from './ChatRoom.vue'
import QuestionCard from './QuestionCard.vue'
import { getRoomStatus } from '@/api'
import { useBreakpoints } from '@vueuse/core'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const breakpoints = useBreakpoints({
  mobile: 0,
  tablet: 768,
  desktop: 1024,
})
const isMobile = breakpoints.smaller('tablet')

const roomCode = ref(route.params.roomId)
const playerStore = usePlayerStore()
const room = ref(null)
const question = ref(null)
const subscriptions = ref([])
const hasSubmitted = ref(false)

// 🔥 响应式布局 - PC端默认显示聊天，移动端默认隐藏
const showChat = ref(!isMobile.value)

// 🔥 未读消息计数
const unreadCount = ref(0)

const questionStartTime = ref(null)
const timeLimit = ref(30)
const countdown = ref(30)
const countdownTimer = ref(null)

// 🔥 新增：聚焦聊天输入框
const chatRoomRef = ref(null)

const getSubmissionKey = () => {
  // 🔥 使用 currentIndex 而不是 question.id，避免重复题冲突
  if (!room.value || room.value.currentIndex === undefined) {
    return `submission_${roomCode.value}_unknown`
  }
  return `submission_${roomCode.value}_${room.value.currentIndex}`
}

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

    // 🔥 新增：恢复提交状态
    if (question.value) {
      const submissionKey = getSubmissionKey()
      const savedSubmission = localStorage.getItem(submissionKey)
      if (savedSubmission === 'true') {
        hasSubmitted.value = true
        console.log('✅ 恢复提交状态: 已提交')
      }
    }
    
    if (savedRoom.currentQuestion && savedRoom.questionStartTime) {
      questionStartTime.value = new Date(savedRoom.questionStartTime)
      timeLimit.value = savedRoom.timeLimit || 30
      resetCountdown()
    }
  }

  window.addEventListener('websocket-reconnecting', handleReconnecting)
  window.addEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)
  window.addEventListener('keydown', handleKeydown)
  connectWebSocket()
})

// 🔥 切换聊天显示
const toggleChat = () => {
  showChat.value = !showChat.value
  // 打开聊天室时清空未读计数
  if (showChat.value) {
    unreadCount.value = 0
  }
}

// 🔥 处理新消息
const handleNewMessage = () => {
  // 只在聊天室关闭时增加未读计数
  if (!showChat.value) {
    unreadCount.value++
  }
}

onUnmounted(() => {
  if (subscriptions.value.length > 0) {
    unsubscribeAll(subscriptions.value)
  }
  clearCountdown()
  const submissionKey = getSubmissionKey()
  localStorage.removeItem(submissionKey)
  window.removeEventListener('websocket-reconnecting', handleReconnecting)
  window.removeEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)
  window.removeEventListener('keydown', handleKeydown)
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

  await refreshRoomState()
}

const setupRoomSubscription = () => {
  const subs = subscribeRoom(
    roomCode.value,
    (update) => {
      console.log("房间更新:", update)
      
      // 🔥 改用 currentIndex 判断是否切题
      const oldIndex = room.value?.currentIndex
      const newIndex = update.currentIndex
      
      room.value = update
      
      if (newIndex !== undefined && oldIndex !== newIndex) {
        // 🔥 题目切换时，清除旧题目的提交记录
        if (oldIndex !== undefined) {
          const oldSubmissionKey = `submission_${roomCode.value}_${oldIndex}`
          localStorage.removeItem(oldSubmissionKey)
          console.log('🧹 清除旧题目提交记录:', oldSubmissionKey)
        }
        
        clearCountdown()
        
        // 重置提交状态
        hasSubmitted.value = false
        question.value = update.currentQuestion
        
        // 🔥 检查新题目是否已提交
        const newSubmissionKey = `submission_${roomCode.value}_${newIndex}`
        const savedSubmission = localStorage.getItem(newSubmissionKey)
        if (savedSubmission === 'true') {
          hasSubmitted.value = true
          console.log('✅ 新题目已提交过')
        } else {
          console.log('🆕 新题目未提交，可以作答')
        }
        
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

      const isGameFinished = update.finished === true || update.status === 'FINISHED'

      if (isGameFinished) {
        console.log('🎮 游戏结束，准备跳转')
        clearCountdown()
        toast.add({
          severity: 'info',
          summary: '游戏结束',
          detail: '正在跳转到结果页面...',
          life: 2000
        })
        setTimeout(() => {
          console.log('🚀 跳转到结果页:', `/result/${roomCode.value}`)
          router.push(`/result/${roomCode.value}`)
        }, 1000)
      }
    },
    (error) => {
        console.error('🔥 房间错误:', error)
        
        // 🔥 添加：房间不存在的处理
        if (error.error?.includes('房间不存在') || error.error?.includes('不存在')) {
          toast.add({
            severity: 'warn',
            summary: '房间已关闭',
            detail: '房间已被删除或游戏已结束',
            life: 3000
          })
          
          // 清理房间信息
          playerStore.clearRoom()
          
          // 3秒后跳转
          setTimeout(() => {
            router.push('/find')
          }, 3000)
        } else {
          toast.add({
            severity: 'error',
            summary: '房间错误',
            detail: error.error || '房间出现错误',
            life: 3000
          })
        }
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
  
  // 🔥 优化：只在倒计时刚好归零时触发一次
  if (remaining === 0 && !hasSubmitted.value && question.value) {
    clearCountdown()  // 🔥 先清除定时器，防止再次触发
    handleAutoSubmit()
  }
}

const clearCountdown = () => {
  if (countdownTimer.value) {
    clearInterval(countdownTimer.value)
    countdownTimer.value = null
  }
}

const handleChoose = (choice) => {
  // 🔥 防护0：检查是否为观战者
  if (playerStore.isSpectator) {
    toast.add({
      severity: 'info',
      summary: '观战模式',
      detail: '观战者无法提交答案',
      life: 2000
    })
    return
  }

  // 🔥 防护1：检查是否已提交
  if (hasSubmitted.value) {
    toast.add({
      severity: 'warn',
      summary: '提示',
      detail: '您已经提交过答案了',
      life: 2000
    })
    return
  }

  // 🔥 防护2：检查题目是否存在
  if (!question.value || !question.value.id) {
    toast.add({
      severity: 'error',
      summary: '错误',
      detail: '题目数据异常，无法提交',
      life: 3000
    })
    return
  }
  
  // 🔥 防护3：立即设置状态和保存记录（在发送前）
  hasSubmitted.value = true
  const submissionKey = getSubmissionKey()
  localStorage.setItem(submissionKey, 'true')
  console.log('💾 提交前保存状态:', submissionKey)
  
  // 发送提交
  try {
    sendSubmit({ 
      roomCode: roomCode.value, 
      playerId: playerStore.playerId, 
      choice: choice.toString()
    })
    
    toast.add({
      severity: 'success',
      summary: '提交成功',
      detail: '已提交答案',
      life: 2000
    })
  } catch (error) {
    console.error('❌ 提交失败:', error)
    // 🔥 发送失败，回滚状态
    hasSubmitted.value = false
    localStorage.removeItem(submissionKey)
    
    toast.add({
      severity: 'error',
      summary: '提交失败',
      detail: '网络错误，请重试',
      life: 3000
    })
  }
}

const handleAutoSubmit = () => {
  // 🔥 防护0：检查是否为观战者
  if (playerStore.isSpectator) {
    console.log('⚠️ 观战模式，跳过自动提交')
    return
  }

  // 🔥 防护1：检查是否已提交
  if (hasSubmitted.value) {
    console.log('⚠️ 已提交，跳过自动提交')
    return
  }

  // 🔥 防护2：检查题目是否存在
  if (!question.value || !question.value.id) {
    console.error('❌ 题目不存在，无法自动提交')
    return
  }
  
  // 🔥 防护3：立即设置状态，防止重复触发
  hasSubmitted.value = true
  
  let defaultChoice
  if (question.value.type === 'CHOICE') {
    defaultChoice = question.value.options?.[0]?.key || 'A'
  } else if (question.value.type === 'BID') {
    defaultChoice = question.value.min || 0
  }
  
  // 🔥 保存提交状态（在发送前保存，防止竞态）
  const submissionKey = getSubmissionKey()
  localStorage.setItem(submissionKey, 'true')
  console.log('💾 自动提交前保存状态:', submissionKey)
  
  // 发送提交
  try {
    sendSubmit({ 
      roomCode: roomCode.value, 
      playerId: playerStore.playerId, 
      choice: defaultChoice.toString(),
      force: true
    })
    
    toast.add({
      severity: 'info',
      summary: '自动提交',
      detail: '时间到，已自动提交默认答案',
      life: 3000
    })
  } catch (error) {
    console.error('❌ 自动提交失败:', error)
    // 🔥 发送失败，回滚状态
    hasSubmitted.value = false
    localStorage.removeItem(submissionKey)
  }
}

const toggleChat = () => {
  showChat.value = !showChat.value
}

const handleKeydown = (e) => {
  // 🔥 如果在输入框中，只处理 Esc，其他键都不拦截
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
    // Esc: 取消输入框焦点
    if (e.key === 'Escape') {
      e.target.blur()
      showChat.value = false
    }
    // 🔥 其他键（包括 Enter）都让输入框自己处理
    return
  }
  
  // 🔥 Space: 聚焦聊天输入框
  if (e.key === ' ') {
    e.preventDefault()
    focusChatInput()
    return
  }
  
  // 🔥 Esc: 关闭聊天
  if (e.key === 'Escape') {
    showChat.value = false
    return
  }
  
  // 🔥 如果已提交或没有题目，其他键不处理
  if (hasSubmitted.value || !question.value) {
    return
  }
  
  // 🔥 Choice题: 1/2/3/4 触发选择
  if (question.value.type === 'CHOICE') {
    const keyMap = { '1': 'A', '2': 'B', '3': 'C', '4': 'D' }
    if (keyMap[e.key]) {
      e.preventDefault()
      const event = new CustomEvent('select-option', { 
        detail: { key: keyMap[e.key] } 
      })
      window.dispatchEvent(event)
      return
    }
  }
  
  // 🔥 Bid题: 数字键 0-9 聚焦并输入
  if (question.value.type === 'BID') {
    if (/^[0-9]$/.test(e.key)) {
      const numberInput = document.querySelector('.p-inputnumber-input')
      if (numberInput) {
        numberInput.focus()
        
        // 如果还没聚焦，手动输入
        if (document.activeElement !== numberInput) {
          e.preventDefault()
          setTimeout(() => {
            numberInput.value = e.key
            numberInput.dispatchEvent(new Event('input', { bubbles: true }))
          }, 0)
        }
      }
      return
    }
  }
  
  // 🔥 Enter: 提交答案（只在没有焦点在输入框时）
  if (e.key === 'Enter') {
    e.preventDefault()
    const event = new CustomEvent('submit-answer')
    window.dispatchEvent(event)
  }
}

const focusChatInput = () => {
  showChat.value = true
  setTimeout(() => {
    const chatInput = 
      document.querySelector('.chat-input') ||
      document.querySelector('input[placeholder*="消息"]') ||
      document.querySelector('input[type="text"]')
    
    if (chatInput) {
      chatInput.focus()
      console.log('✅ 已聚焦到聊天输入框')
    } else {
      console.warn('⚠️ 未找到聊天输入框')
    }
  }, 100)
}

// 🔥 新增：处理重连中
const handleReconnecting = (event) => {
  console.log('🔄 GameView: WebSocket 重连中...', event.detail)
  
  toast.add({
    severity: 'warn',
    summary: '连接中断',
    detail: `正在尝试重连... (${event.detail.attempts}/5)`,
    life: 3000
  })
}

// 🔥 新增：处理重连失败
const handleMaxReconnectFailed = () => {
  console.error('❌ GameView: WebSocket 重连失败')
  
  toast.add({
    severity: 'error',
    summary: '连接失败',
    detail: '连接已断开，请刷新页面',
    life: 0
  })
  
  // 清除倒计时，避免误操作
  clearCountdown()
  
  setTimeout(() => {
    if (confirm('连接已断开，是否重新连接？')) {
      window.location.reload()
    } else {
      router.push('/find')
    }
  }, 2000)
}

// 🔥 新增：刷新房间状态（重连后使用）
const refreshRoomState = async () => {
  try {
    console.log('🔄 GameView: 刷新房间状态...')
    const response = await getRoomStatus(roomCode.value)
    const updatedRoom = response.data
    
    console.log('✅ GameView: 房间状态已刷新:', updatedRoom)
    
    // 更新房间数据
    room.value = updatedRoom
    question.value = updatedRoom.currentQuestion
    playerStore.setRoom(updatedRoom)
    
    // 🔥 恢复倒计时
    if (updatedRoom.questionStartTime) {
      questionStartTime.value = new Date(updatedRoom.questionStartTime)
      timeLimit.value = updatedRoom.timeLimit || 30
      resetCountdown()
    }
    
    // 🔥 检查游戏状态
    if (updatedRoom.status === 'FINISHED' || updatedRoom.finished) {
      toast.add({
        severity: 'info',
        summary: '游戏已结束',
        detail: '正在跳转到结果页面...',
        life: 2000
      })
      setTimeout(() => {
        router.push(`/result/${roomCode.value}`)
      }, 1000)
    } else if (updatedRoom.status === 'WAITING') {
      toast.add({
        severity: 'info',
        summary: '游戏未开始',
        detail: '正在返回等待房间...',
        life: 2000
      })
      setTimeout(() => {
        router.push(`/wait/${roomCode.value}`)
      }, 1000)
    }
    
  } catch (error) {
    console.error('❌ GameView: 刷新房间状态失败:', error)
    // 不提示错误，因为订阅会自动更新
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-3 sm:p-6">
    
    <div class="max-w-7xl mx-auto">
      <!-- 🔥 移动端：聊天改成底部弹出，不占用网格 -->
      <div class="grid gap-4 sm:gap-6"
           :class="showChat && !isMobile ? 'lg:grid-cols-[1fr_400px]' : 'lg:grid-cols-1'">
        
        <!-- 游戏主区域 -->
        <div class="space-y-4 sm:space-y-6">
          
          <!-- 顶部信息栏 -->
          <div class="bg-white dark:bg-gray-800 rounded-lg sm:rounded-xl 
                      border border-gray-200 dark:border-gray-700 p-3 sm:p-5">
            <div class="flex items-center justify-between flex-wrap gap-2 sm:gap-4">
              <!-- 左侧 -->
              <div class="flex items-center gap-2 sm:gap-4 flex-wrap">
                <h1 class="text-lg sm:text-xl font-semibold text-gray-900 dark:text-white">
                  {{ roomCode }}
                </h1>
                <div class="px-2 sm:px-3 py-0.5 sm:py-1 
                            bg-blue-50 dark:bg-blue-900/20 
                            text-blue-700 dark:text-blue-300 
                            rounded-md text-xs sm:text-sm font-medium">
                  {{ currentQuestionIndex }}/{{ totalQuestions }}
                </div>
              </div>
              
              <!-- 右侧 -->
              <div class="flex items-center gap-2 sm:gap-3 flex-wrap">
                <!-- 倒计时 -->
                <div class="px-2 sm:px-3 py-0.5 sm:py-1 rounded-md font-semibold text-xs sm:text-sm"
                     :class="countdown <= 10 
                       ? 'bg-red-50 text-red-700 dark:bg-red-900/20 dark:text-red-400' 
                       : 'bg-green-50 text-green-700 dark:bg-green-900/20 dark:text-green-400'">
                  {{ countdown }}s
                </div>
                
                <!-- 提交状态 -->
                <div class="px-2 sm:px-3 py-0.5 sm:py-1 
                            bg-gray-100 dark:bg-gray-700 
                            text-gray-700 dark:text-gray-300 
                            rounded-md text-xs sm:text-sm">
                  {{ submittedPlayers }}/{{ totalPlayers }}
                </div>
                
                <!-- 聊天切换 -->
                <button
                  @click="toggleChat"
                  class="p-1.5 sm:p-2 hover:bg-gray-100 dark:hover:bg-gray-700
                         rounded-lg transition-colors relative"
                >
                  <i :class="showChat ? 'pi pi-times' : 'pi pi-comment'"
                     class="text-sm sm:text-base text-gray-600 dark:text-gray-400"></i>
                  <!-- 🔥 红点提示 -->
                  <span v-if="unreadCount > 0 && !showChat"
                        class="absolute -top-0.5 -right-0.5 w-4 h-4 bg-red-500 text-white text-xs
                               rounded-full flex items-center justify-center font-bold">
                    {{ unreadCount > 9 ? '9+' : unreadCount }}
                  </span>
                </button>
              </div>
            </div>
          </div>

          <!-- 题目卡片 -->
          <div class="flex justify-center">
            <QuestionCard
              v-if="question"
              :question="question"
              :disabled="hasSubmitted || playerStore.isSpectator"
              @choose="handleChoose"
              class="w-full"
            />
            
            <div v-else 
                 class="bg-white dark:bg-gray-800 rounded-lg sm:rounded-xl 
                        border border-gray-200 dark:border-gray-700 
                        p-8 sm:p-12 text-center w-full">
              <i class="pi pi-spin pi-spinner text-3xl sm:text-4xl text-gray-400 mb-3"></i>
              <p class="text-sm sm:text-base text-gray-600 dark:text-gray-400">等待下一题</p>
            </div>
          </div>

          <!-- 已提交提示 -->
          <transition name="fade">
            <div v-if="hasSubmitted" 
                 class="fixed bottom-4 sm:bottom-6 left-1/2 -translate-x-1/2 z-50
                        bg-green-600 text-white px-4 sm:px-5 py-2 sm:py-2.5 
                        rounded-lg text-xs sm:text-sm font-medium shadow-lg">
                已提交
            </div>
          </transition>
        </div>

        <!-- 🔥 PC端聊天区域（大屏幕显示在右侧） -->
        <transition name="slide">
          <div v-if="showChat && !isMobile" class="hidden lg:block">
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

    <!-- 🔥 移动端聊天弹窗（底部弹出） -->
    <transition name="slide-up">
      <div v-if="showChat && isMobile"
           class="fixed inset-x-0 bottom-0 z-50 lg:hidden
                  bg-white dark:bg-gray-800 
                  border-t border-gray-200 dark:border-gray-700
                  rounded-t-2xl shadow-2xl
                  max-h-[70vh] flex flex-col">
        <!-- 拖动条 -->
        <div class="flex justify-center py-2 border-b border-gray-200 dark:border-gray-700">
          <div class="w-12 h-1 bg-gray-300 dark:bg-gray-600 rounded-full"></div>
        </div>

        <ChatRoom
          v-if="roomCode"
          :roomCode="roomCode"
          :playerId="playerStore.playerId"
          :playerName="playerStore.playerName"
          @newMessage="handleNewMessage"
          class="flex-1 overflow-hidden"
        />
      </div>
    </transition>

    <!-- 🔥 移动端遮罩层 -->
    <transition name="fade">
      <div v-if="showChat && isMobile"
           @click="toggleChat"
           class="fixed inset-0 bg-black/50 z-40 lg:hidden"></div>
    </transition>

    <!-- 🔥 移动端浮动聊天按钮 -->
    <button
      v-if="isMobile"
      @click="toggleChat"
      class="fixed bottom-20 right-6 z-50 w-14 h-14 bg-blue-600 hover:bg-blue-700
             text-white rounded-full shadow-lg flex items-center justify-center
             transition-colors relative lg:hidden"
    >
      <i :class="showChat ? 'pi pi-times text-xl' : 'pi pi-comment text-xl'"></i>
      <!-- 🔥 移动端红点提示 -->
      <span v-if="unreadCount > 0 && !showChat"
            class="absolute -top-1 -right-1 w-6 h-6 bg-red-500 text-white text-xs
                   rounded-full flex items-center justify-center font-bold">
        {{ unreadCount > 99 ? '99+' : unreadCount }}
      </span>
    </button>
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

/* 🔥 新增：底部弹出动画 */
.slide-up-enter-active, .slide-up-leave-active {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-up-enter-from, .slide-up-leave-to {
  transform: translateY(100%);
}
</style>