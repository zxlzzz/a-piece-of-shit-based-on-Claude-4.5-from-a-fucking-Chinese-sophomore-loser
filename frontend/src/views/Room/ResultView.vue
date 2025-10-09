<script setup>
import { getGameHistory } from '@/api';
import { usePlayerStore } from '@/stores/player';
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import ResultContent from '../ResultContent.vue'; // 导入复用组件
import ChatRoom from './ChatRoom.vue';

const route = useRoute()
const roomCode = ref(route.params.roomId)
const gameHistory = ref(null)
const playerStore = usePlayerStore()
const loading = ref(true)
const showChat = ref(false)

onMounted(async () => {
  try {
    const response = await getGameHistory(roomCode.value)
    gameHistory.value = response.data
  } catch (error) {
    console.error('获取游戏历史失败:', error)
  } finally {
    loading.value = false
  }
})

const toggleChat = () => {
  showChat.value = !showChat.value
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-6">
    <div class="max-w-7xl mx-auto">
      <div class="grid gap-6" :class="showChat ? 'lg:grid-cols-[1fr_400px]' : 'lg:grid-cols-1'">
        
        <!-- 主内容区 -->
        <div class="space-y-6">
          <!-- 顶部栏 -->
          <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
            <div class="flex items-center justify-between flex-wrap gap-4">
              <h1 class="text-2xl font-semibold text-gray-900 dark:text-white">游戏结果</h1>
              <button 
                @click="toggleChat"
                class="px-4 py-2 bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300
                       border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-700
                       rounded-lg text-sm font-medium transition-colors"
              >
                <i :class="showChat ? 'pi pi-times' : 'pi pi-comment'"></i>
              </button>
            </div>
          </div>
          
          <!-- 加载状态 -->
          <div v-if="loading" 
               class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-12 text-center">
            <i class="pi pi-spin pi-spinner text-4xl text-gray-400 mb-3"></i>
            <p class="text-gray-600 dark:text-gray-400">加载中</p>
          </div>
          
          <!-- 复用内容组件 -->
          <ResultContent v-else-if="gameHistory" :game-history="gameHistory" />
        </div>

        <!-- 聊天区域 -->
        <transition name="slide">
          <div v-if="showChat">
            <ChatRoom
              v-if="showChat && roomCode"
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
.slide-enter-active, .slide-leave-active {
  transition: all 0.3s;
}
.slide-enter-from, .slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
</style>