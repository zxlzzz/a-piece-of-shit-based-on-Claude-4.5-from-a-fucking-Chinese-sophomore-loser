<script setup>
import { logger } from '@/utils/logger'
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { usePlayerStore } from '@/stores/player'
import { useBreakpoints } from '@vueuse/core'
import ResultContent from '@/components/result/ResultContent.vue'
import ChatRoom from '@/components/chat/ChatRoom.vue'
import MobileChatDrawer from '@/components/game/MobileChatDrawer.vue'
import SkeletonResult from '@/components/common/SkeletonResult.vue'
import { getGameHistory } from '@/api'

const route = useRoute()
const playerStore = usePlayerStore()

const breakpoints = useBreakpoints({
  mobile: 0,
  tablet: 768,
  desktop: 1024,
})
const isDesktop = breakpoints.greaterOrEqual('desktop')

const roomCode = ref(route.params.roomId)
const gameHistory = ref(null)
const loading = ref(true)
const showChat = ref(false)
const unreadCount = ref(0)
const hasUnreadMessages = computed(() => unreadCount.value > 0)

const toggleChat = () => {
  showChat.value = !showChat.value
  if (showChat.value) {
    unreadCount.value = 0
  }
}

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
    logger.error('加载游戏历史失败:', error)
  } finally {
    loading.value = false
  }
  // 🔥 WebSocket 连接由 App.vue 全局管理，这里不需要建立连接
})
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-3 sm:p-6">
    <!-- 🔥 修复布局：不使用max-w，改用条件宽度 -->
    <div :class="showChat && isDesktop ? 'max-w-[1600px]' : 'max-w-4xl'" class="mx-auto">
      <!-- 🔥 只在桌面端且显示聊天时才分栏 -->
      <div class="grid gap-4 sm:gap-6"
           :class="showChat && isDesktop ? 'lg:grid-cols-[1fr_400px]' : 'grid-cols-1'">
        
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
                <span v-if="hasUnreadMessages && !showChat"
                      class="absolute -top-0.5 -right-0.5
                             w-2 h-2 bg-red-500 rounded-full
                             animate-pulse"></span>
              </button>
            </div>
          </div>
                  
          <!-- 加载状态 -->
          <SkeletonResult v-if="loading" />

          <!-- 错误状态 -->
          <div v-else-if="!gameHistory"
               class="bg-white dark:bg-gray-800 rounded-lg sm:rounded-xl border border-gray-200 dark:border-gray-700 p-8 sm:p-12 text-center">
            <i class="pi pi-exclamation-circle text-3xl sm:text-4xl text-red-500 mb-3"></i>
            <p class="text-sm sm:text-base text-gray-600 dark:text-gray-400 mb-4">无法加载游戏结果</p>
            <button
              @click="$router.push('/find')"
              class="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors text-sm font-medium"
            >
              <i class="pi pi-home mr-2"></i>
              返回首页
            </button>
          </div>

          <ResultContent v-else :gameHistory="gameHistory" />
        </div>

        <!-- 🔥 桌面端聊天 - 只在 1024px+ 且 showChat 为 true 时显示 -->
        <transition name="slide">
          <div v-show="showChat && isDesktop" class="hidden lg:block">
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

    <!-- 🔥 非桌面端聊天抽屉 - 小于 1024px 时使用 -->
    <MobileChatDrawer
      :show="showChat && !isDesktop"
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