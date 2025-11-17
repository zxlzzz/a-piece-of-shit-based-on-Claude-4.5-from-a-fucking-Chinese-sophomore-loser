<script setup>
import { logger } from '@/utils/logger'
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { usePlayerStore } from '@/stores/player'
import { useChatStore } from '@/stores/chat'
import { useBreakpoints } from '@vueuse/core'
import ResultContent from '@/components/result/ResultContent.vue'
import SkeletonResult from '@/components/common/SkeletonResult.vue'
import { getGameHistory } from '@/api'

const route = useRoute()
const playerStore = usePlayerStore()
const chatStore = useChatStore()

const breakpoints = useBreakpoints({
  mobile: 0,
  tablet: 768,
  desktop: 1024,
})
const isDesktop = breakpoints.greaterOrEqual('desktop')
const isMobile = breakpoints.smaller('desktop')

const roomCode = ref(route.params.roomId)
const gameHistory = ref(null)
const loading = ref(true)

onMounted(async () => {
  try {
    const response = await getGameHistory(roomCode.value)
    gameHistory.value = response.data
  } catch (error) {
    logger.error('加载游戏历史失败:', error)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-3 sm:p-6 transition-[padding] duration-300 ease-in-out"
       :class="chatStore.visible && isDesktop ? 'pr-[420px]' : ''">
    <!-- 🔥 修复布局：当全局聊天打开时，给右侧留出空间 -->
    <div class="max-w-4xl mx-auto">
      <div class="space-y-4 sm:space-y-6">
        <!-- 顶部栏 -->
        <div class="bg-white dark:bg-gray-800 rounded-lg sm:rounded-xl border border-gray-200 dark:border-gray-700 p-4 sm:p-5">
          <div class="flex items-center justify-between flex-wrap gap-3 sm:gap-4">
            <h1 class="text-lg sm:text-2xl font-semibold text-gray-900 dark:text-white">游戏结果</h1>
            <!-- 🔥 切换全局聊天 -->
            <button
              @click="chatStore.toggleChat(isMobile)"
              class="relative px-3 sm:px-4 py-1.5 sm:py-2 bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300
                     border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-700
                     hover:scale-105 active:scale-95
                     rounded-lg text-sm font-medium transition-all duration-200"
              :title="chatStore.visible ? '关闭聊天' : '打开聊天'"
            >
              <i class="pi transition-transform" :class="chatStore.visible ? 'pi-times text-blue-600 dark:text-blue-400' : 'pi-comment'"></i>
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
    </div>
  </div>
</template>

<style scoped>
/* 样式已移除，聊天室在全局App.vue中管理 */
</style>