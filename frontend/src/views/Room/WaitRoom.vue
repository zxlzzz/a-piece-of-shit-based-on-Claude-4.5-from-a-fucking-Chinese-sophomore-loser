<script setup>
import { updateRoomSettings, getRoomStatus } from '@/api'
import { usePlayerStore } from '@/stores/player'
import { generatePlayerColor } from '@/utils/player'
import { connect, disconnect, isConnected, sendLeave, sendReady, sendStart, subscribeRoom, unsubscribeAll } from '@/websocket/ws'
import { useToast } from 'primevue/usetoast'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ChatRoom from './ChatRoom.vue'
import CustomForm from './CustomForm.vue'

const playerStore = usePlayerStore()
const route = useRoute()
const router = useRouter()
const toast = useToast()

const roomCode = ref(route.params.roomId)
const room = ref(null)
const subscriptions = ref([])
const loading = ref(false)

const showCustomForm = ref(false)

// 🔥 改用 ref 而不是 computed，手动管理连接状态
const wsConnected = ref(false)

const chatRoomRef = ref(null)

const isAllReady = computed(() => {
  if (!room.value || !room.value.players) return false
  return room.value.players.every(p => p.ready)
})

const currentPlayerReady = computed(() => {
  if (!room.value || !room.value.players) return false
  const currentPlayer = room.value.players.find(p => p.playerId === playerStore.playerId)
  return currentPlayer?.ready || false
})

const isRoomOwner = computed(() => {
  if (!room.value || !room.value.players) return false
  return room.value.players[0]?.playerId === playerStore.playerId
})

onMounted(async () => {
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
    
    const isPlayerInRoom = savedRoom.players?.some(p => p.playerId === playerStore.playerId)
    if (!isPlayerInRoom) {
      toast.add({
        severity: 'error',
        summary: '错误',
        detail: '您不在此房间中',
        life: 3000
      })
      router.push('/find')
      return
    }
  } else {
    toast.add({
      severity: 'error',
      summary: '错误',
      detail: '房间信息不存在',
      life: 3000
    })
    router.push('/find')
    return
  }

  // 🔥 监听 WebSocket 错误事件
  window.addEventListener('room-deleted', handleRoomDeleted)
  window.addEventListener('websocket-error', handleWebSocketError)
  window.addEventListener('websocket-reconnecting', handleReconnecting)
  window.addEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)
  
  // 开始连接
  await connectWebSocket()
})

onUnmounted(() => {
  window.removeEventListener('room-deleted', handleRoomDeleted)
  window.removeEventListener('websocket-error', handleWebSocketError)
  window.removeEventListener('websocket-reconnecting', handleReconnecting)
  window.removeEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)
  
  if (subscriptions.value.length > 0) {
    unsubscribeAll(subscriptions.value)
    subscriptions.value = []
  }
})

const handleRoomDeleted = (event) => {
  toast.add({
    severity: 'warn',
    summary: '房间已解散',
    detail: '房主已离开，房间被解散',
    life: 3000
  })
  setTimeout(() => {
    router.push('/find')
  }, 1000)
}

// 🔥 新增：监听 WebSocket 错误
const handleWebSocketError = (event) => {
  console.error('🔥 WaitRoom 收到 WebSocket 错误:', event.detail)
  wsConnected.value = false
}

const handleReconnecting = (event) => {
  console.log('🔄 WebSocket 重连中...', event.detail)
  wsConnected.value = false
  
  toast.add({
    severity: 'warn',
    summary: '连接中断',
    detail: `正在尝试重连... (${event.detail.attempts}/5)`,
    life: 3000
  })
}

// 🔥 新增：处理重连失败
const handleMaxReconnectFailed = () => {
  console.error('❌ WebSocket 重连失败，已达到最大次数')
  wsConnected.value = false
  
  toast.add({
    severity: 'error',
    summary: '连接失败',
    detail: '连接已断开，请刷新页面',
    life: 0 // 不自动消失
  })
  
  // 给用户选择
  setTimeout(() => {
    if (confirm('连接已断开，是否重新连接？')) {
      window.location.reload()
    } else {
      router.push('/find')
    }
  }, 2000)
}

const connectWebSocket = async () => {
  console.log('🔌 WaitRoom: 开始连接流程')
  
  // 🔥 先更新状态
  wsConnected.value = isConnected()
  console.log('🔌 当前连接状态:', wsConnected.value)
  
  if (wsConnected.value) {
    console.log('✅ WaitRoom: WebSocket 显示已连接，验证连接状态...')
    
    // 🔥 尝试订阅，如果失败说明连接已断
    try {
      setupRoomSubscription()
      return // 订阅成功，直接返回
    } catch (err) {
      console.error('❌ 订阅失败，可能连接已断开，尝试重连', err)
      // 🔥 强制断开旧连接
      disconnect(true)
      wsConnected.value = false
    }
  }
  
  // 开始连接
  console.warn('⚠️ WaitRoom: WebSocket 未连接，开始连接...')
  
  try {
    loading.value = true
    
    // 主动调用 connect() 并等待完成
    await connect(playerStore.playerId)
    
    console.log('✅ WaitRoom: WebSocket 连接成功')
    
    // 🔥 更新状态
    wsConnected.value = true
    
    // 等待 100ms 让连接稳定
    await new Promise(resolve => setTimeout(resolve, 100))
    
  } catch (err) {
    console.error('❌ WaitRoom: WebSocket 连接失败', err)
    
    // 🔥 更新状态
    wsConnected.value = false
    
    toast.add({
      severity: 'error',
      summary: '连接失败',
      detail: err.message === '连接超时' 
        ? 'WebSocket 连接超时，请刷新页面' 
        : 'WebSocket 连接失败：' + err.message,
      life: 5000
    })
    
    loading.value = false
    
    // 给用户选择
    if (confirm('WebSocket 连接失败，是否重试？')) {
      await connectWebSocket() // 递归重试
      return
    } else {
      router.push('/find')
      return
    }
  } finally {
    loading.value = false
  }
  
  // 连接成功后，开始订阅
  setupRoomSubscription()

  await refreshRoomState()
}

// 🔥 提取订阅逻辑
const setupRoomSubscription = () => {
  console.log('📡 WaitRoom: 开始订阅房间:', roomCode.value)
  
  // 🔥 先清理旧订阅
  if (subscriptions.value.length > 0) {
    console.log('🧹 清理旧订阅')
    unsubscribeAll(subscriptions.value)
    subscriptions.value = []
  }
  
  try {
    const subs = subscribeRoom(
      roomCode.value,
      (roomUpdate) => {
        console.log("📥 房间更新:", roomUpdate)
        room.value = roomUpdate
        playerStore.setRoom(roomUpdate)
        
        if (roomUpdate.status === 'PLAYING') {
          toast.add({
            severity: 'info',
            summary: '游戏开始',
            detail: '正在进入游戏...',
            life: 2000
          })
          router.push(`/game/${roomCode.value}`)
        }
      },
      (error) => {
        console.error('🔥 房间错误:', error)
        toast.add({
          severity: 'error',
          summary: '房间错误',
          detail: error.error || '房间出现错误',
          life: 3000
        })
      }
    )
    
    if (subs && subs.length > 0) {
      subscriptions.value = subs
      console.log(`✅ WaitRoom: 订阅成功 (${subs.length} 个订阅)`)
    } else {
      console.error('❌ WaitRoom: 订阅返回空数组')
      throw new Error('订阅返回空数组')
    }
  } catch (err) {
    console.error('❌ WaitRoom: 订阅异常:', err)
    toast.add({
      severity: 'error',
      summary: '订阅失败',
      detail: '订阅房间时出现异常',
      life: 3000
    })
    // 🔥 抛出错误，让上层处理
    throw err
  }
}

const handleReady = async () => {
  if (currentPlayerReady.value) return
  
  // 🔥 先检查连接状态
  if (!wsConnected.value) {
    console.error('❌ WebSocket 未连接，无法设置准备状态')
    toast.add({
      severity: 'error',
      summary: '连接错误',
      detail: 'WebSocket 未连接，请稍后再试',
      life: 3000
    })
    return
  }
  
  loading.value = true
  try {
    sendReady({
      roomCode: roomCode.value,
      playerId: playerStore.playerId,
      ready: true
    })
    
    if (chatRoomRef.value) {
      chatRoomRef.value.sendReadyMessage(true)
    }
    
    toast.add({
      severity: 'success',
      summary: '成功',
      detail: '已设置为准备状态',
      life: 2000
    })
    
  } catch (error) {
    console.error("设置准备状态失败:", error)
    toast.add({
      severity: 'error',
      summary: '失败',
      detail: '设置准备状态失败',
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const handleStart = () => {
  if (!isAllReady.value) return
  
  // 🔥 先检查连接状态
  if (!wsConnected.value) {
    console.error('❌ WebSocket 未连接，无法开始游戏')
    toast.add({
      severity: 'error',
      summary: '连接错误',
      detail: 'WebSocket 未连接，无法开始游戏',
      life: 3000
    })
    return
  }
  
  sendStart({ roomCode: roomCode.value })
  toast.add({
    severity: 'info',
    summary: '开始游戏',
    detail: '正在启动游戏...',
    life: 2000
  })
}

const handleLeave = () => {
  if (wsConnected.value) {
    sendLeave({
      roomCode: roomCode.value,
      playerId: playerStore.playerId
    })
  }
  
  playerStore.clearRoom()
  router.push("/find")
}

const copyRoomCode = async () => {
  try {
    await navigator.clipboard.writeText(roomCode.value)
    toast.add({
      severity: 'success',
      summary: '已复制',
      detail: '房间码已复制到剪贴板',
      life: 2000
    })
  } catch (error) {
    console.error('复制失败:', error)
    toast.add({
      severity: 'error',
      summary: '复制失败',
      detail: '请手动复制房间码',
      life: 3000
    })
  }
}

const handleCustomFormSubmit = async (formData) => {
  loading.value = true
  try {
    // 🔥 调用后端 API
    const response = await updateRoomSettings(roomCode.value, {
      questionCount: formData.questionCount,
      rankingMode: formData.rankingMode,
      targetScore: formData.targetScore,
      winConditions: formData.winConditions
    })
    
    // 🔥 更新本地房间数据
    room.value = response.data
    playerStore.setRoom(response.data)
    
    toast.add({
      severity: 'success',
      summary: '成功',
      detail: '游戏设置已更新',
      life: 2000
    })
    
    showCustomForm.value = false
    
  } catch (error) {
    console.error('更新设置失败:', error)
    toast.add({
      severity: 'error',
      summary: '失败',
      detail: error.response?.data?.message || '更新游戏设置失败',
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const handleCustomFormCancel = () => {
  showCustomForm.value = false
}
// 🔥 新增：刷新房间状态（重连后使用）
const refreshRoomState = async () => {
  try {
    console.log('🔄 刷新房间状态...')
    const response = await getRoomStatus(roomCode.value)
    room.value = response.data
    playerStore.setRoom(response.data)
    
    console.log('✅ 房间状态已刷新:', room.value)
    
    // 🔥 如果游戏已开始，跳转到游戏页面
    if (room.value.status === 'PLAYING') {
      toast.add({
        severity: 'info',
        summary: '游戏进行中',
        detail: '正在进入游戏...',
        life: 2000
      })
      router.push(`/game/${roomCode.value}`)
    }
  } catch (error) {
    console.error('刷新房间状态失败:', error)
    // 不提示错误，因为订阅会自动更新
  }
}
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

    <!-- 主容器 -->
    <div class="max-w-7xl mx-auto">
      <!-- 🔥 改：移动端单列，大屏幕3列布局 -->
      <div class="grid gap-4 sm:gap-6 lg:grid-cols-3">
        
        <!-- 左侧：房间信息 + 玩家列表 -->
        <div class="lg:col-span-2 space-y-4 sm:space-y-6">
          
          <!-- 房间头部 -->
          <div class="bg-white dark:bg-gray-800 rounded-lg sm:rounded-xl border border-gray-200 dark:border-gray-700 p-4 sm:p-8">
            <div class="text-center">
              <div class="flex items-center justify-center gap-2 sm:gap-3 mb-2 sm:mb-3">
                <h1 class="text-2xl sm:text-3xl font-semibold text-gray-900 dark:text-white">
                  {{ roomCode }}
                </h1>
                <button 
                  @click="copyRoomCode"
                  class="p-1.5 sm:p-2 hover:bg-gray-100 dark:hover:bg-gray-700 
                         rounded-lg transition-colors"
                  title="复制房间码"
                >
                  <i class="pi pi-copy text-sm sm:text-base text-gray-500 dark:text-gray-400"></i>
                </button>
              </div>
              
              <div v-if="room" class="flex items-center justify-center gap-3 sm:gap-4 text-xs sm:text-sm text-gray-600 dark:text-gray-400">
                <span class="flex items-center gap-1.5">
                  <i class="pi pi-users"></i>
                  {{ room.currentPlayers }}/{{ room.maxPlayers }}
                </span>
                <span class="w-1 h-1 rounded-full bg-gray-300"></span>
                <span class="px-2 py-0.5 rounded-md text-xs font-medium"
                      :class="room.status === 'WAITING'
                        ? 'bg-yellow-50 text-yellow-700 dark:bg-yellow-900/20 dark:text-yellow-400'
                        : 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-400'">
                  {{ room.status === 'WAITING' ? '等待中' : room.status }}
                </span>
              </div>
            </div>

            <!-- 游戏信息 -->
            <div v-if="room" class="mt-4 sm:mt-6 pt-4 sm:pt-6 border-t border-gray-200 dark:border-gray-700">
              <div class="grid grid-cols-2 gap-3 sm:gap-4 text-xs sm:text-sm">
                <div class="text-center">
                  <p class="text-gray-500 dark:text-gray-400 mb-1">题目数量</p>
                  <p class="text-base sm:text-lg font-semibold text-gray-900 dark:text-white">
                    {{ room.questionCount || 10 }}
                  </p>
                </div>
                <div class="text-center">
                  <p class="text-gray-500 dark:text-gray-400 mb-1">准备状态</p>
                  <p class="text-base sm:text-lg font-semibold text-gray-900 dark:text-white">
                    {{ room.players?.filter(p => p.ready).length || 0 }}/{{ room.players?.length || 0 }}
                  </p>
                </div>
              </div>
              
              <!-- 排名模式和通关条件 -->
              <div v-if="room?.rankingMode !== 'standard' || room.winConditions" 
                   class="mt-3 sm:mt-4 pt-3 sm:pt-4 border-t border-gray-200 dark:border-gray-700">
                <div class="text-xs sm:text-sm space-y-2">
                  <!-- 排名模式 -->
                  <div v-if="room?.rankingMode !== 'standard'" 
                       class="flex items-center gap-2 text-gray-600 dark:text-gray-400">
                    <i class="pi pi-chart-line text-blue-500"></i>
                    <span>
                      目标：{{ 
                        room.rankingMode === 'closest_to_avg' ? '接近平均分' :
                        room.rankingMode === 'closest_to_target' ? `接近 ${room.targetScore} 分` :
                        '标准排名'
                      }}
                    </span>
                  </div>
                  <!-- 通关条件 -->
                  <div v-if="room?.winConditions" class="space-y-1">
                    <div v-if="room?.winConditions.minScorePerPlayer" 
                         class="flex items-center gap-2 text-gray-600 dark:text-gray-400">
                      <i class="pi pi-users text-green-500"></i>
                      <span>所有人 ≥ {{ room.winConditions.minScorePerPlayer }} 分</span>
                    </div>
                    <div v-if="room?.winConditions.minTotalScore" 
                         class="flex items-center gap-2 text-gray-600 dark:text-gray-400">
                      <i class="pi pi-flag text-purple-500"></i>
                      <span>总分 ≥ {{ room.winConditions.minTotalScore }} 分</span>
                    </div>
                    <div v-if="room?.winConditions.minAvgScore" 
                         class="flex items-center gap-2 text-gray-600 dark:text-gray-400">
                      <i class="pi pi-chart-bar text-orange-500"></i>
                      <span>平均分 ≥ {{ room.winConditions.minAvgScore }} 分</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 提示 -->
            <div class="mt-4 sm:mt-6 space-y-2">
              <p v-if="isRoomOwner" class="text-center text-xs text-gray-500 dark:text-gray-400">
                只有房主可以开始游戏
              </p>
              <p v-if="!isAllReady" class="text-center text-xs text-gray-500 dark:text-gray-400">
                等待所有玩家准备
              </p>
            </div>
          </div>

          <!-- 玩家列表 -->
          <div class="bg-white dark:bg-gray-800 rounded-lg sm:rounded-xl border border-gray-200 dark:border-gray-700 p-4 sm:p-6">
            <h2 class="text-base sm:text-lg font-semibold text-gray-900 dark:text-white mb-3 sm:mb-4">
              玩家
            </h2>
            
            <!-- 🔥 改：移动端单列，小屏幕双列 -->
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 sm:gap-3">
              <div 
                v-for="(player, index) in room?.players" 
                :key="player.playerId"
                class="p-2.5 sm:p-3 rounded-lg border transition-all relative"
                :class="[
                  player.ready 
                    ? 'bg-green-50 border-green-200 dark:bg-green-900/10 dark:border-green-800' 
                    : 'bg-gray-50 border-gray-200 dark:bg-gray-700/50 dark:border-gray-600',
                  player.playerId === playerStore.playerId
                    ? 'ring-1 ring-blue-500 dark:ring-blue-600'
                    : ''
                ]"
              >
                <!-- 房主标识 -->
                <div v-if="index === 0" 
                     class="absolute -top-1 -right-1 w-4 h-4 sm:w-5 sm:h-5 bg-yellow-400 rounded-full 
                            flex items-center justify-center text-xs">
                  👑
                </div>

                <div class="flex items-center gap-2 sm:gap-3">
                  <!-- 头像 -->
                  <div class="w-8 h-8 sm:w-10 sm:h-10 rounded-full bg-gray-200 dark:bg-gray-600 
                              flex items-center justify-center text-gray-700 dark:text-gray-300 
                              font-medium text-xs sm:text-sm"
                       :style="{ backgroundColor: generatePlayerColor(player.playerId) + '20', 
                                 color: generatePlayerColor(player.playerId) }">
                    {{ player.name.charAt(0).toUpperCase() }}
                  </div>

                  <!-- 信息 -->
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center gap-1.5 sm:gap-2">
                      <p class="font-medium text-gray-900 dark:text-white text-xs sm:text-sm truncate">
                        {{ player.name }}
                      </p>
                      <span v-if="player.playerId === playerStore.playerId" 
                            class="text-xs px-1 py-0.5 sm:px-1.5 bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400 rounded">
                        你
                      </span>
                    </div>
                    <p class="text-xs text-gray-500 dark:text-gray-400">
                      {{ player.ready ? '已准备' : '等待中' }}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 操作按钮 -->
          <!-- 🔥 改：移动端全宽堆叠，平板横向排列 -->
          <div class="flex flex-col sm:flex-row gap-2 sm:gap-3">
            <button 
              @click="handleLeave"
              class="w-full sm:w-auto px-4 sm:px-5 py-2.5 rounded-lg text-sm font-medium
                     bg-white dark:bg-gray-800 
                     text-gray-700 dark:text-gray-300
                     border border-gray-300 dark:border-gray-600
                     hover:bg-gray-50 dark:hover:bg-gray-700
                     transition-colors"
            >
              离开
            </button>

            <!-- 自定义按钮（仅房主可见） -->
            <button 
              v-if="isRoomOwner"
              @click="showCustomForm = true"
              :disabled="loading || !wsConnected"
              class="w-full sm:w-auto px-4 sm:px-5 py-2.5 rounded-lg text-sm font-medium
                    bg-white dark:bg-gray-800 
                    text-gray-700 dark:text-gray-300
                    border border-gray-300 dark:border-gray-600
                    hover:bg-gray-50 dark:hover:bg-gray-700
                    transition-colors
                    disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <i class="pi pi-cog mr-1"></i>
              自定义
            </button>

            <button 
              @click="handleReady"
              :disabled="currentPlayerReady || loading || !wsConnected"
              class="w-full sm:w-auto px-4 sm:px-5 py-2.5 rounded-lg text-sm font-medium
                     transition-colors
                     disabled:opacity-50 disabled:cursor-not-allowed"
              :class="currentPlayerReady 
                ? 'bg-green-100 text-green-700 border border-green-200 dark:bg-green-900/20 dark:text-green-400 dark:border-green-800' 
                : 'bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-600 dark:hover:bg-blue-700'"
            >
              {{ currentPlayerReady ? '已准备' : '准备' }}
            </button>

            <button 
              v-if="isRoomOwner"
              @click="handleStart"
              :disabled="!isAllReady || !wsConnected"
              class="w-full sm:w-auto px-4 sm:px-5 py-2.5 rounded-lg text-sm font-medium
                     bg-blue-600 hover:bg-blue-700
                     text-white transition-colors
                     disabled:opacity-50 disabled:cursor-not-allowed"
            >
              开始游戏
            </button>
          </div>
        </div>

        <!-- 右侧：聊天室 -->
        <div class="lg:col-span-1">
          <ChatRoom
            v-if="roomCode"
            ref="chatRoomRef"
            :roomCode="roomCode"
            :playerId="playerStore.playerId"
            :playerName="playerStore.playerName"
          />
        </div>
      </div>
    </div>

    <!-- 加载遮罩 -->
    <div v-if="loading" 
         class="fixed inset-0 bg-black/20 dark:bg-black/40 backdrop-blur-sm
                flex items-center justify-center z-50">
      <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6 text-center">
        <i class="pi pi-spin pi-spinner text-3xl text-blue-600 mb-3"></i>
        <p class="text-sm text-gray-600 dark:text-gray-300">处理中</p>
      </div>
    </div>

    <!-- 自定义表单弹窗 -->
    <CustomForm
      v-if="showCustomForm"
      :maxQuestions="20"
      :currentSettings="room"
      @submit="handleCustomFormSubmit"
      @cancel="handleCustomFormCancel"
    />
  </div>
</template>