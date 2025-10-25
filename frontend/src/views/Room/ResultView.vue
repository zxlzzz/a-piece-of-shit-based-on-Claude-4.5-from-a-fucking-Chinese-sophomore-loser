<script setup>
import { getGameHistory } from '@/api';
import { usePlayerStore } from '@/stores/player';
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import ResultContent from '../ResultContent.vue'; // 导入复用组件
import ChatRoom from './ChatRoom.vue';
import { useBreakpoints } from '@vueuse/core';

const route = useRoute()
const roomCode = ref(route.params.roomId)
const gameHistory = ref(null)
const playerStore = usePlayerStore()
const loading = ref(true)

// 🔥 响应式布局 - PC端默认显示聊天，移动端默认隐藏
const breakpoints = useBreakpoints({
  mobile: 0,
  tablet: 768,
  desktop: 1024,
})
const isMobile = breakpoints.smaller('tablet')
const showChat = ref(!isMobile.value)

// 🔥 未读消息计数
const unreadCount = ref(0)

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
  // 🔥 打开聊天室时清空未读计数
  if (showChat.value) {
    unreadCount.value = 0
  }
}

const handleNewMessage = () => {
  // 🔥 只在聊天室关闭时增加未读计数
  if (!showChat.value) {
    unreadCount.value++
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-3 sm:p-6">
    <div class="max-w-7xl mx-auto">
      <div class="grid gap-6" :class="showChat ? 'lg:grid-cols-[1fr_400px]' : 'lg:grid-cols-1'">

        <!-- 主内容区 -->
        <div class="space-y-6">
          <!-- 顶部栏 -->
          <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
            <div class="flex items-center justify-between flex-wrap gap-4">
              <h1 class="text-2xl font-semibold text-gray-900 dark:text-white">游戏结果</h1>
              <!-- 🔥 PC端切换按钮 -->
              <button
                v-if="!isMobile"
                @click="toggleChat"
                class="px-4 py-2 bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300
                       border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-700
                       rounded-lg text-sm font-medium transition-colors relative"
              >
                <i :class="showChat ? 'pi pi-times' : 'pi pi-comment'"></i>
                <!-- 🔥 PC端红点提示 -->
                <span v-if="unreadCount > 0 && !showChat"
                      class="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white text-xs
                             rounded-full flex items-center justify-center font-bold">
                  {{ unreadCount > 9 ? '9+' : unreadCount }}
                </span>
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
              @newMessage="handleNewMessage"
            />
          </div>
        </transition>
      </div>

      <!-- 🔥 移动端浮动聊天按钮 -->
      <button
        v-if="isMobile"
        @click="toggleChat"
        class="fixed bottom-6 right-6 z-50 w-14 h-14 bg-blue-600 hover:bg-blue-700
               text-white rounded-full shadow-lg flex items-center justify-center
               transition-colors relative"
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