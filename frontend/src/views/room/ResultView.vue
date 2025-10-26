<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { usePlayerStore } from '@/stores/player'
import { useBreakpoints } from '@vueuse/core'
import ResultContent from '@/components/result/ResultContent.vue'
import ChatRoom from '@/components/chat/ChatRoom.vue'
import MobileChatDrawer from '@/components/game/MobileChatDrawer.vue'
import { getGameHistory } from '@/api'

const route = useRoute()
const playerStore = usePlayerStore()

const breakpoints = useBreakpoints({
  mobile: 0,
  tablet: 768,
  desktop: 1024,
})
const isMobile = breakpoints.smaller('tablet')

const roomCode = ref(route.params.roomId)
const gameHistory = ref(null)
const loading = ref(true)
const showChat = ref(!isMobile.value)  // 🔥 移动端默认关闭
const unreadCount = ref(0)  // 🔥 未读消息计数
const hasUnreadMessages = computed(() => unreadCount.value > 0)  // 🔥 是否有未读消息

const toggleChat = () => {
  showChat.value = !showChat.value
  // 🔥 打开聊天时清空未读
  if (showChat.value) {
    unreadCount.value = 0
  }
}

// 🔥 处理新消息
const handleNewMessage = () => {
  if (!showChat.value) {
    unreadCount.value++
  }
}

onMounted(async () => {
  try {
    const response = await getGameHistory(roomCode.value)
    gameHistory.value = response.data
  } catch (error) {
    console.error('加载游戏历史失败:', error)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-3 sm:p-6">
    <div class="max-w-7xl mx-auto">
      <div class="grid gap-4 sm:gap-6" 
           :class="showChat && !isMobile ? 'lg:grid-cols-[1fr_400px]' : 'lg:grid-cols-1'">
                
        <!-- 主内容区 -->
        <div class="space-y-4 sm:space-y-6">
          <!-- 顶部栏 -->
          <div class="bg-white dark:bg-gray-800 rounded-lg sm:rounded-xl border border-gray-200 dark:border-gray-700 p-4 sm:p-5">
            <div class="flex items-center justify-between flex-wrap gap-3 sm:gap-4">
              <h1 class="text-lg sm:text-2xl font-semibold text-gray-900 dark:text-white">游戏结果</h1>
              <button
                @click="toggleChat"
                class="relative px-3 sm:px-4 py-1.5 sm:py-2 bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300
                       border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-700
                       rounded-lg text-sm font-medium transition-colors"
              >
                <i :class="showChat ? 'pi pi-times' : 'pi pi-comment'"></i>
                <!-- 🔥 未读消息红点 -->
                <span v-if="hasUnreadMessages && !showChat"
                      class="absolute -top-0.5 -right-0.5
                             w-2 h-2 bg-red-500 rounded-full
                             animate-pulse"></span>
              </button>
            </div>
          </div>
                  
          <!-- 加载状态 -->
          <div v-if="loading"
               class="bg-white dark:bg-gray-800 rounded-lg sm:rounded-xl border border-gray-200 dark:border-gray-700 p-8 sm:p-12 text-center">
            <i class="pi pi-spin pi-spinner text-3xl sm:text-4xl text-gray-400 mb-3"></i>
            <p class="text-sm sm:text-base text-gray-600 dark:text-gray-400">加载中</p>
          </div>
                  
          <!-- 复用内容组件 -->
          <ResultContent v-else-if="gameHistory" :game-history="gameHistory" />
        </div>
        
        <!-- PC 端聊天 -->
        <transition name="slide">
          <div v-show="showChat && !isMobile" class="hidden lg:block">
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

    <!-- 🔥 移动端聊天抽屉 -->
    <MobileChatDrawer
      :show="showChat && isMobile"
      :roomCode="roomCode"
      :playerId="playerStore.playerId"
      :playerName="playerStore.playerName"
      @newMessage="handleNewMessage"
      @close="toggleChat"
    />
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