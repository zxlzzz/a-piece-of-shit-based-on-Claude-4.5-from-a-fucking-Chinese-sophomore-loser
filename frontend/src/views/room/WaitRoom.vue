<script setup>
import { updateRoomSettings } from '@/api'
import { usePlayerStore } from '@/stores/player'
import { sendLeave, sendReady, sendStart } from '@/websocket/ws'
import { useToast } from 'primevue/usetoast'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ChatRoom from '@/components/chat/ChatRoom.vue'
import CustomForm from '@/components/room/CustomForm.vue'
import PlayerListCard from '@/components/room/PlayerListCard.vue'
import RoomInfoCard from '@/components/room/RoomInfoCard.vue'
import WaitRoomActions from '@/components/room/WaitRoomActions.vue'
import { useWaitRoomWebSocket } from '@/composables/room/useWaitRoomWebSocket'

const playerStore = usePlayerStore()
const route = useRoute()
const router = useRouter()
const toast = useToast()

const roomCode = ref(route.params.roomId)
const room = ref(null)
const showCustomForm = ref(false)
const chatRoomRef = ref(null)

const { wsConnected, loading, connectWebSocket, cleanup, init } = useWaitRoomWebSocket(
  roomCode,
  playerStore,
  router,
  toast
)

const isAllReady = computed(() => {
  if (!room.value || !room.value.players) return false
  const nonSpectators = room.value.players.filter(p => !p.spectator)
  if (nonSpectators.length === 0) return false
  return nonSpectators.every(p => p.ready)
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
      // 🔥 静默跳转，不显示toast（用户无法处理）
      console.log('⚠️ 玩家不在房间中，返回房间列表')
      router.push('/find')
      return
    }
  } else {
    // 🔥 静默跳转，不显示toast（用户无法处理）
    console.log('⚠️ 没有房间信息，返回房间列表')
    router.push('/find')
    return
  }

  init()
  await connectWebSocket(room, (roomUpdate) => {
    room.value = roomUpdate
    playerStore.setRoom(roomUpdate)
  })
})

onUnmounted(() => {
  cleanup()
})

const handleReady = async () => {
  if (currentPlayerReady.value) return

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
    const response = await updateRoomSettings(roomCode.value, {
      questionCount: formData.questionCount,
      rankingMode: formData.rankingMode,
      targetScore: formData.targetScore,
      winConditions: formData.winConditions
    })

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
      <div class="grid gap-4 sm:gap-6 lg:grid-cols-3">

        <!-- 左侧：房间信息 + 玩家列表 -->
        <div class="lg:col-span-2 space-y-4 sm:space-y-6">

          <RoomInfoCard
            :room-code="roomCode"
            :room="room"
            :is-room-owner="isRoomOwner"
            :is-all-ready="isAllReady"
            :on-copy-room-code="copyRoomCode"
          />

          <PlayerListCard
            :room="room"
            :current-player-id="playerStore.playerId"
          />

          <WaitRoomActions
            :is-room-owner="isRoomOwner"
            :is-spectator="playerStore.isSpectator"
            :current-player-ready="currentPlayerReady"
            :is-all-ready="isAllReady"
            :loading="loading"
            :ws-connected="wsConnected"
            @ready="handleReady"
            @start="handleStart"
            @leave="handleLeave"
            @show-custom="showCustomForm = true"
          />
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
