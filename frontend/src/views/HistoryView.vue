<script setup>
import { getHistoryDetail, getHistoryList } from '@/api'
import { usePlayerStore } from '@/stores/player'
import Dialog from 'primevue/dialog'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ResultContent from './ResultContent.vue'

const playerStore = usePlayerStore()
const router = useRouter()
const games = ref([])
const loading = ref(false)
const filter = ref('all')
const showDetail = ref(false)
const selectedGame = ref(null)
const detailLoading = ref(false)

const filteredGames = computed(() => {
  if (filter.value === 'all') return games.value
  
  const days = filter.value === 'week' ? 7 : 30
  const now = new Date()
  
  return games.value.filter(game => {
    const gameDate = new Date(game.endTime)
    const diffDays = Math.abs((now - gameDate) / (1000 * 60 * 60 * 24))
    return diffDays <= days
  })
})

const loadHistory = async () => {
  loading.value = true
  try {
    const response = await getHistoryList(playerStore.playerId)
    games.value = response.data
  } catch (error) {
    console.error('加载历史记录失败:', error)
  } finally {
    loading.value = false
  }
}

const viewDetail = async (gameId) => {
  showDetail.value = true
  detailLoading.value = true
  selectedGame.value = null
  
  try {
    const response = await getHistoryDetail(gameId)
    selectedGame.value = response.data
  } catch (error) {
    console.error('加载游戏详情失败:', error)
  } finally {
    detailLoading.value = false
  }
}

const closeDetail = () => {
  showDetail.value = false
  selectedGame.value = null
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  
  try {
    const date = new Date(dateStr)
    if (isNaN(date.getTime())) return dateStr
    
    const now = new Date()
    const diff = now - date
    const absDays = Math.abs(Math.floor(diff / (1000 * 60 * 60 * 24)))
    
    if (diff < 0) {
      if (absDays === 0) return '稍后'
      return `${absDays}天后`
    }
    
    if (absDays === 0) {
      return '今天 ' + date.toLocaleTimeString('zh-CN', { 
        hour: '2-digit', 
        minute: '2-digit' 
      })
    }
    
    if (absDays === 1) {
      return '昨天 ' + date.toLocaleTimeString('zh-CN', { 
        hour: '2-digit', 
        minute: '2-digit' 
      })
    }
    
    if (absDays < 7) {
      return `${absDays}天前`
    }
    
    return date.toLocaleDateString('zh-CN', { 
      month: '2-digit', 
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch (error) {
    console.error('日期格式化失败:', dateStr, error)
    return dateStr
  }
}

const getRankText = (rank) => {
  if (rank === undefined || rank === null) return '-'
  const medals = ['🥇 第1名', '🥈 第2名', '🥉 第3名']
  return rank <= 3 ? medals[rank - 1] : `第${rank}名`
}

const getRankBadgeClass = (rank) => {
  if (!rank) return 'bg-gray-50 text-gray-600 dark:bg-gray-800 dark:text-gray-400'
  if (rank === 1) return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
  if (rank === 2) return 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
  if (rank === 3) return 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400'
  return 'bg-gray-50 text-gray-600 dark:bg-gray-800 dark:text-gray-400'
}

const filterBtnClass = (value) => {
  return filter.value === value
    ? 'px-4 py-2 rounded-lg bg-blue-600 text-white font-medium'
    : 'px-4 py-2 rounded-lg bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 text-gray-700 dark:text-gray-300 hover:border-blue-500 transition-colors'
}

onMounted(() => {
  loadHistory()
})
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 py-8">
    <div class="max-w-4xl mx-auto px-4">
      <!-- 标题和筛选 -->
      <div class="mb-6">
        <div class="flex items-center justify-between mb-4">
          <h1 class="text-2xl font-semibold text-gray-900 dark:text-white">游戏历史</h1>
          <button 
            @click="router.push('/')"
            class="text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white transition-colors"
          >
            返回主页
          </button>
        </div>
        
        <div class="flex gap-2">
          <button @click="filter = 'all'" :class="filterBtnClass('all')">全部</button>
          <button @click="filter = 'week'" :class="filterBtnClass('week')">最近7天</button>
          <button @click="filter = 'month'" :class="filterBtnClass('month')">最近30天</button>
        </div>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="text-center py-12">
        <i class="pi pi-spin pi-spinner text-4xl text-gray-400 mb-3"></i>
        <p class="text-gray-500 dark:text-gray-400">加载中</p>
      </div>

      <!-- 历史记录列表 -->
      <div v-else-if="filteredGames.length > 0" class="space-y-3">
        <div 
          v-for="game in filteredGames" 
          :key="game.gameId"
          @click="viewDetail(game.gameId)"
          class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4 
                 hover:border-blue-500 dark:hover:border-blue-600 hover:shadow-md 
                 cursor-pointer transition-all"
        >
          <div class="flex justify-between items-start mb-3">
            <div>
              <span class="text-gray-500 dark:text-gray-400 text-sm">
                房间 #{{ game.roomCode || '-' }}
              </span>
              <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">
                {{ formatDate(game.endTime) }}
              </p>
            </div>
            
            <span 
              :class="getRankBadgeClass(game.myRank)" 
              class="px-3 py-1 rounded-full text-sm font-semibold"
            >
              {{ getRankText(game.myRank) }}
            </span>
          </div>

          <div class="flex gap-4 text-sm text-gray-600 dark:text-gray-400">
            <span>总分: <strong class="text-gray-900 dark:text-white">{{ game.myScore || 0 }}</strong></span>
            <span>题数: {{ game.questionCount || 0 }}</span>
            <span>👥 {{ game.playerCount || 0 }}人</span>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="text-center py-12">
        <div class="text-gray-400 dark:text-gray-500">
          <p class="text-lg mb-2">📝 暂无游戏记录</p>
          <p class="text-sm">快去开始一局游戏吧</p>
        </div>
        <button 
          @click="router.push('/')"
          class="mt-6 px-6 py-2 bg-blue-600 text-white rounded-lg 
                 hover:bg-blue-700 transition-colors"
        >
          开始游戏
        </button>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <Dialog 
      v-model:visible="showDetail" 
      modal 
      :closable="true"
      :dismissableMask="true"
      class="w-full max-w-5xl m-4"
      @hide="closeDetail"
    >
      <template #header>
        <h3 class="text-xl font-semibold text-gray-900 dark:text-white">游戏详情</h3>
      </template>

      <div v-if="detailLoading" class="text-center py-12">
        <i class="pi pi-spin pi-spinner text-4xl text-gray-400 mb-3"></i>
        <p class="text-gray-500 dark:text-gray-400">加载中</p>
      </div>

      <ResultContent 
        v-else-if="selectedGame" 
        :game-history="selectedGame" 
      />

      <div v-else class="text-center py-12 text-gray-400">
        <p>加载失败，请重试</p>
      </div>
    </Dialog>
  </div>
</template>