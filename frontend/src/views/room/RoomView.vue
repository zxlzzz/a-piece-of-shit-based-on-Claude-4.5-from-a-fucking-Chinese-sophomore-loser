<script setup>
import { logger } from '@/utils/logger'
import { createRoom, getAllActiveRooms, getRoomStatus, joinRoom } from '@/api'
import { usePlayerStore } from '@/stores/player'
import { useToast } from 'primevue/usetoast'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CreateRoomCard from '@/components/room/CreateRoomCard.vue'
import RoomCard from '@/components/room/RoomCard.vue'
import SkeletonRoomCard from '@/components/common/SkeletonRoomCard.vue'

const router = useRouter()
const toast = useToast()

const playerStore = usePlayerStore()
const currentRoom = ref(null)
const loading = ref(false)
const activeRooms = ref([])
const refreshing = ref(false)
const spectatorModes = ref({})  // 观战模式状态 { roomCode: boolean }
const searchQuery = ref('') // 🔥 房间搜索关键词

// 自动刷新
const REFRESH_INTERVAL = 5000 // 5秒刷新一次
let refreshTimer = null

// 🔥 过滤后的房间列表（支持前缀匹配）
const filteredRooms = computed(() => {
  if (!searchQuery.value.trim()) {
    return activeRooms.value
  }
  const query = searchQuery.value.trim().toUpperCase()
  return activeRooms.value.filter(room =>
    room.roomCode.toUpperCase().startsWith(query)
  )
})

// 启动自动刷新
const startAutoRefresh = () => {
  if (refreshTimer) return
  refreshTimer = setInterval(() => {
    loadActiveRooms()
  }, REFRESH_INTERVAL)
}

// 停止自动刷新
const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

// 初始化
onMounted(async () => {
  // 检查登录状态
  if (!playerStore.isLoggedIn) {
    toast.add({
      severity: 'warn',
      summary: '请先登录',
      detail: '请先登录后再使用房间功能',
      life: 3000
    })
    router.push('/login')
    return
  }

  await loadActiveRooms()
  startAutoRefresh() // 启动自动刷新

  // 🔥 改进：尝试恢复房间，失败则自动清理
  const savedRoom = playerStore.loadRoom()
  if (savedRoom) {
    try {
      const response = await getRoomStatus(savedRoom.roomCode)
      currentRoom.value = response.data
      playerStore.setRoom(response.data)
    } catch (error) {
      // 🔥 静默处理404错误，不显示弹窗
      if (error.response?.status === 404) {
      } else {
        // 其他错误才提示
        logger.error('获取房间状态失败:', error)
      }
      // 清理失效的房间数据
      playerStore.clearRoom()
      currentRoom.value = null
    }
  }
})

// 清理定时器
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
    // 🔥 网络错误才显示提示（用户可以重试）
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

const handleCreate = async ({ questionCount, maxPlayers, questionTagIds }) => {
  loading.value = true
  try {
    const createResponse = await createRoom(maxPlayers, questionCount, questionTagIds)
    const roomData = createResponse.data
    
    
    const joinResponse = await joinRoom(
      roomData.roomCode,
      playerStore.playerId,
      playerStore.playerName,
      false,  // 房主不能是观战者
      password  // 房主加入时传入密码
    )

    currentRoom.value = joinResponse.data
    playerStore.setRoom(joinResponse.data)
    playerStore.setSpectator(false)
    
    toast.add({
      severity: 'success',
      summary: '成功',
      detail: `房间 ${roomData.roomCode} 创建成功`,
      life: 2000
    })

    router.push(`/wait/${roomData.roomCode}`)
    
  } catch (error) {
    logger.error("创建房间失败:", error)
    toast.add({
      severity: 'error',
      summary: '创建失败',
      detail: error.response?.data?.message || '创建房间失败',
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const handleEnterRoom = () => {
  if (currentRoom.value) {
    router.push(`/wait/${currentRoom.value.roomCode}`)
  }
}

const handleJoinRoom = async (roomCode, hasPassword, spectator = false) => {
  let password = null

  // 如果房间有密码，提示输入
  if (hasPassword) {
    password = prompt('此房间需要密码，请输入密码：')
    if (password === null) {
      // 用户取消输入
      return
    }
  }

  loading.value = true
  try {
    // 🔥 改用 playerStore
    const response = await joinRoom(
      roomCode,
      playerStore.playerId,
      playerStore.playerName,
      spectator,
      password
    )
    currentRoom.value = response.data
    // 🔥 统一用 playerStore 存储
    playerStore.setRoom(response.data)

    // 🔥 保存观战模式到 store
    playerStore.setSpectator(spectator)

    toast.add({
      severity: 'success',
      summary: '成功',
      detail: spectator ? `已加入房间 ${roomCode}（观战模式）` : `已加入房间 ${roomCode}`,
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
  // 🔥 统一用 playerStore 清除
  playerStore.clearRoom()
  
  toast.add({
    severity: 'info',
    summary: '已离开房间',
    detail: '您已离开当前房间',
    life: 2000
  })
}

const handleLogout = () => {
  // 🔥 用 Pinia 清除（会自动清除房间）
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
        
        <!-- 左侧：创建房间 + 当前房间 -->
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

            <!-- 🔥 搜索框 -->
            <div class="mb-4">
              <div class="relative">
                <input
                  v-model="searchQuery"
                  type="text"
                  placeholder="搜索房间码（支持前缀匹配，如输入 'AB' 可搜索到 'ABC123'）"
                  class="w-full px-4 py-2.5 pl-10
                         bg-gray-50 dark:bg-gray-700/50
                         border border-gray-200 dark:border-gray-600
                         rounded-lg
                         text-gray-800 dark:text-white
                         placeholder-gray-400 dark:placeholder-gray-500
                         focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
                         transition-all"
                />
                <i class="pi pi-search absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"></i>
                <button
                  v-if="searchQuery"
                  @click="searchQuery = ''"
                  class="absolute right-3 top-1/2 -translate-y-1/2
                         text-gray-400 hover:text-gray-600 dark:hover:text-gray-300
                         transition-colors"
                >
                  <i class="pi pi-times"></i>
                </button>
              </div>
              <p v-if="searchQuery && filteredRooms.length === 0"
                 class="mt-2 text-sm text-gray-500 dark:text-gray-400">
                未找到匹配的房间
              </p>
            </div>

            <!-- 骨架屏（首次加载） -->
            <div v-if="refreshing && activeRooms.length === 0"
                 class="grid grid-cols-1 md:grid-cols-2 gap-3 sm:gap-4">
              <SkeletonRoomCard v-for="i in 4" :key="i" />
            </div>

            <!-- 房间列表 -->
            <div v-else-if="filteredRooms.length > 0"
                 class="grid grid-cols-1 md:grid-cols-2 gap-3 sm:gap-4">
              <div
                v-for="room in filteredRooms"
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
                      <i v-if="room.hasPassword" class="pi pi-lock text-orange-500 text-sm" title="需要密码"></i>
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

                <!-- 观战模式选项 -->
                <div class="mb-2 flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    :id="`spectator-${room.roomCode}`"
                    v-model="spectatorModes[room.roomCode]"
                    class="w-4 h-4 text-blue-600 bg-gray-100 border-gray-300 rounded
                           focus:ring-blue-500 dark:focus:ring-blue-600
                           dark:bg-gray-700 dark:border-gray-600"
                  />
                  <label
                    :for="`spectator-${room.roomCode}`"
                    class="text-gray-600 dark:text-gray-400 cursor-pointer select-none"
                  >
                    观战模式（不参与答题）
                  </label>
                </div>

                <!-- 加入按钮 -->
                <button
                  @click="handleJoinRoom(room.roomCode, room.hasPassword, spectatorModes[room.roomCode] || false)"
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