<script setup>
import { logger } from '@/utils/logger'
import { createRoom, getAllActiveRooms, getRoomStatus, joinRoom, deleteRoom } from '@/api'
import { usePlayerStore } from '@/stores/player'
import { useToast } from 'primevue/usetoast'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import CreateRoomCard from '@/components/room/CreateRoomCard.vue'
import RoomCard from '@/components/room/RoomCard.vue'

const router = useRouter()
const route = useRoute() 
const toast = useToast()

const playerStore = usePlayerStore()
const currentRoom = ref(null)
const loading = ref(false)
const activeRooms = ref([])
const refreshing = ref(false)

const REFRESH_INTERVAL = 5000
let refreshTimer = null

const startAutoRefresh = () => {
  if (refreshTimer) return
  refreshTimer = setInterval(() => {
    loadActiveRooms()
  }, REFRESH_INTERVAL)
}

const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

onMounted(async () => {
  

  
  const error = route.query.error
  if (error === 'room_not_found') {
    toast.add({
      severity: 'warn',
      summary: '房间不存在',
      detail: '您访问的房间已不存在或已结束',
      life: 3000
    })
    router.replace({ name: 'find' })
  }

  await loadActiveRooms()
  startAutoRefresh() // 启动自动刷新

  const savedRoom = playerStore.loadRoom()
  if (savedRoom) {
    try {
      const response = await getRoomStatus(savedRoom.roomCode)
      currentRoom.value = response.data
      playerStore.setRoom(response.data)
    } catch (error) {
      if (error.response?.status === 404) {
      } else {
        logger.error('获取房间状态失败:', error)
      }
      playerStore.clearRoom()
      currentRoom.value = null
    }
  }
})

onUnmounted(() => {
  stopAutoRefresh()
})

const loadActiveRooms = async () => {
  refreshing.value = true
  try {
    const response = await getAllActiveRooms()
    activeRooms.value = response.data.filter(r =>
      !currentRoom.value || r.roomCode !== currentRoom.value.roomCode
    )
  } catch (error) {
    logger.error('加载房间列表失败:', error)
    if (!error.response || error.code === 'ECONNABORTED') {
      toast.add({
        severity: 'error',
        summary: '网络错误',
        detail: '加载房间列表失败，请检查网络后重试',
        life: 4000
      })
    }
  } finally {
    refreshing.value = false
  }
}

const handleCreate = async ({ questionCount, maxPlayers }) => {
  if (!playerStore.isLoggedIn) {
    toast.add({
      severity: 'warn',
      summary: '请先登录',
      detail: '请登录后再创建房间',
      life: 3000
    })
    router.push('/login')
    return
  }

  loading.value = true
  let createdRoomCode = null

  try {
    const createResponse = await createRoom(maxPlayers, questionCount, 30)
    const roomData = createResponse.data
    createdRoomCode = roomData.roomCode

    try {
      const joinResponse = await joinRoom(
        roomData.roomCode,
        playerStore.playerId,
        playerStore.playerName
      )

      currentRoom.value = joinResponse.data
      playerStore.setRoom(joinResponse.data)

      toast.add({
        severity: 'success',
        summary: '成功',
        detail: `房间 ${roomData.roomCode} 创建成功`,
        life: 2000
      })

      router.push(`/wait/${roomData.roomCode}`)
    } catch (joinError) {
      // 加入失败，清理已创建的"幽灵房间"
      logger.error("加入房间失败，尝试清理幽灵房间:", joinError)
      try {
        await deleteRoom(createdRoomCode)
        logger.info(` 已清理幽灵房间: ${createdRoomCode}`)
      } catch (deleteError) {
        logger.error("清理幽灵房间失败:", deleteError)
      }

      toast.add({
        severity: 'error',
        summary: '加入房间失败',
        detail: joinError.response?.data?.message || '无法加入刚创建的房间，房间已清理',
        life: 3000
      })
      throw joinError  // 重新抛出，让外层catch处理
    }

  } catch (error) {
    logger.error("创建房间失败:", error)
    if (!createdRoomCode) {
      toast.add({
        severity: 'error',
        summary: '创建失败',
        detail: error.response?.data?.message || '创建房间失败',
        life: 3000
      })
    }
  } finally {
    loading.value = false
  }
}

const handleEnterRoom = () => {
  if (currentRoom.value) {
    router.push(`/wait/${currentRoom.value.roomCode}`)
  }
}

const handleJoinRoom = async (roomCode) => {

  if (!playerStore.isLoggedIn) {
    toast.add({
      severity: 'warn',
      summary: '请先登录',
      detail: '请登录后再加入房间',
      life: 3000
    })
    router.push('/login')
    return
  }

  loading.value = true
  try {
    const response = await joinRoom(
      roomCode,
      playerStore.playerId,
      playerStore.playerName
    )
    currentRoom.value = response.data
    playerStore.setRoom(response.data)
    toast.add({
      severity: 'success',
      summary: '成功',
      detail: `已加入房间 ${roomCode}`,
      life: 3000
    })

    router.push(`/wait/${roomCode}`)
  } catch (error) {
    logger.error('加入房间失败:', error)
    toast.add({
      severity: 'error',
      summary: '加入失败',
      detail: error.response?.data?.message || '加入房间失败',
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const handleLeaveRoom = () => {
  currentRoom.value = null
  playerStore.clearRoom()
  
  toast.add({
    severity: 'info',
    summary: '已离开房间',
    detail: '您已离开当前房间',
    life: 2000
  })
}

const handleLogout = () => {
  playerStore.clearPlayer()
  router.push('/login')
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 py-4 sm:py-8 px-3 sm:px-4">
    <div class="max-w-6xl mx-auto">
      
      <!-- 用户信息卡片 -->
      <div v-if="playerStore.isLoggedIn" 
          class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-4 sm:p-6 mb-4 sm:mb-8
                  border border-gray-100 dark:border-gray-700">
        <div class="flex items-center justify-between flex-wrap gap-3 sm:gap-0">
          <div class="flex items-center gap-3 sm:gap-4">
            <!-- 用户头像 -->
            <div class="w-10 h-10 sm:w-12 sm:h-12 rounded-full text-lg sm:text-xl bg-gradient-to-br from-blue-500 to-purple-600 
                        flex items-center justify-center text-white font-bold">
              {{ playerStore.playerName?.charAt(0).toUpperCase() || '?' }}
            </div>
            
            <!-- 用户信息 -->
            <div>
              <p class="text-base sm:text-lg font-semibold text-gray-800 dark:text-white">
                {{ playerStore.playerName || '未知用户' }}
              </p>
              <p class="text-xs sm:text-sm text-gray-500 dark:text-gray-400">
                ID: {{ playerStore.userId || '-' }}
              </p>
            </div>
          </div>
          
          <!-- 退出按钮 -->
          <button 
            @click="handleLogout"
            class="px-3 sm:px-4 py-2 text-xs sm:text-sm font-medium
                  text-gray-700 dark:text-gray-300
                  hover:bg-gray-100 dark:hover:bg-gray-700
                  rounded-lg transition-colors
                  flex items-center gap-2"
          >
            <i class="pi pi-sign-out"></i>
            <span class="hidden sm:inline">退出登录</span>
            <span class="sm:hidden">退出</span>
          </button>
        </div>
      </div>

      <!-- 主要内容区 -->
      <div class="grid gap-4 sm:gap-6 lg:grid-cols-3">
        
        <div class="lg:col-span-1 space-y-4 sm:space-y-6">
          <!-- 创建房间卡片 -->
          <CreateRoomCard @create="handleCreate" :loading="loading" />

          <!-- 当前房间（如果有） -->
          <RoomCard
            v-if="currentRoom"
            :room="currentRoom"
            @enter="handleEnterRoom"
            @leave="handleLeaveRoom"
            is-current
          />
        </div>

        <!-- 右侧：活跃房间列表 -->
        <div class="lg:col-span-2">
          <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm 
                      border border-gray-100 dark:border-gray-700 p-4 sm:p-6">
            
            <!-- 标题栏 -->
            <div class="flex items-center justify-between mb-4">
              <h2 class="text-xl font-bold text-gray-800 dark:text-white flex items-center gap-2">
                <i class="pi pi-home text-blue-500"></i>
                活跃房间
                <span class="text-sm font-normal text-gray-500 dark:text-gray-400">
                  ({{ activeRooms.length }})
                </span>
              </h2>

              <!-- 刷新按钮 -->
              <button
                @click="loadActiveRooms"
                :disabled="refreshing"
                class="p-2 hover:bg-gray-100 dark:hover:bg-gray-700
                       rounded-lg transition-colors"
                :class="{ 'animate-spin': refreshing }"
              >
                <i class="pi pi-refresh text-gray-600 dark:text-gray-400"></i>
              </button>
            </div>

            <!-- 房间列表 -->
            <div v-else-if="activeRooms.length > 0"
                 class="grid grid-cols-1 md:grid-cols-2 gap-3 sm:gap-4">
              <div
                v-for="room in activeRooms"
                :key="room.roomCode"
                class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 sm:p-4
                       border border-gray-200 dark:border-gray-600
                       hover:border-blue-300 dark:hover:border-blue-600
                       transition-all duration-200 group"
              >
                <!-- 房间头部 -->
                <div class="flex justify-between items-start mb-2 sm:mb-3">
                  <div>
                    <h3 class="font-bold text-base sm:text-lg text-gray-800 dark:text-white flex items-center gap-2">
                      {{ room.roomCode }}
                    </h3>
                    <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
                      <i class="pi pi-users text-xs"></i>
                      {{ room.currentPlayers }}/{{ room.maxPlayers }} 人
                    </p>
                  </div>

                  <!-- 状态标签 -->
                  <span
                    class="px-3 py-1 text-xs font-medium rounded-full"
                    :class="{
                      'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400':
                        room.status === 'WAITING',
                      'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400':
                        room.status === 'PLAYING',
                      'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-400':
                        room.status === 'FINISHED'
                    }"
                  >
                    {{ room.status === 'WAITING' ? '等待中' :
                       room.status === 'PLAYING' ? '游戏中' : '已结束' }}
                  </span>
                </div>

                <!-- 房间配置 -->
                <div class="flex flex-wrap gap-2 mb-3 text-xs">
                  <span class="flex items-center gap-1 px-2 py-1 bg-white dark:bg-gray-800 rounded-md text-gray-600 dark:text-gray-400">
                    <i class="pi pi-book text-blue-500"></i>
                    {{ room.questionCount }}题
                  </span>
                  <span class="flex items-center gap-1 px-2 py-1 bg-white dark:bg-gray-800 rounded-md text-gray-600 dark:text-gray-400">
                    <i class="pi pi-clock text-purple-500"></i>
                    {{ room.timeLimit }}s
                  </span>
                </div>

                <!-- 加入按钮 -->
                <button
                  @click="handleJoinRoom(room.roomCode)"
                  :disabled="room.status !== 'WAITING' ||
                            room.currentPlayers >= room.maxPlayers ||
                            loading"
                  class="w-full px-3 sm:px-4 py-2 sm:py-2.5 rounded-lg text-sm font-medium
                         bg-blue-500 hover:bg-blue-600
                         text-white transition-colors
                         disabled:opacity-50 disabled:cursor-not-allowed
                         disabled:hover:bg-blue-500
                         flex items-center justify-center gap-2"
                >
                  <i class="pi pi-arrow-right text-sm"></i>
                  {{ room.status === 'WAITING' && room.currentPlayers < room.maxPlayers
                     ? '加入房间' : '无法加入' }}
                </button>
              </div>
            </div>

            <!-- 空状态 -->
            <div v-else class="text-center py-8 sm:py-12">
              <i class="pi pi-inbox text-4xl sm:text-6xl text-gray-300 dark:text-gray-600 mb-3 sm:mb-4"></i>
              <p class="text-sm sm:text-base text-gray-500 dark:text-gray-400">
                暂无活跃房间，创建一个开始游戏吧！
              </p>
            </div>

            <!-- 加载中 -->
            <div v-if="loading && !refreshing" 
                 class="absolute inset-0 bg-white/80 dark:bg-gray-800/80 
                        rounded-lg flex items-center justify-center">
              <div class="text-center">
                <i class="pi pi-spin pi-spinner text-3xl text-blue-500 mb-2"></i>
                <p class="text-gray-600 dark:text-gray-400">处理中...</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>