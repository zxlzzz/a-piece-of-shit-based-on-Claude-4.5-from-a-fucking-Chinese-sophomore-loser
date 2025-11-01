<script setup>
import { usePlayerStore } from '@/stores/player'
import { computed, ref } from 'vue'

const props = defineProps({
  gameHistory: {
    type: Object,
    required: true
  }
})

const isPassed = computed(() => {
  return currentPlayerRank.value?.passed ?? true  // 默认通关
})

const playerStore = usePlayerStore()
const expandedQuestion = ref(null)

const currentPlayerRank = computed(() => {
  if (!props.gameHistory || !playerStore.playerId) return null
  return props.gameHistory.leaderboard.find(
    p => p.playerId === playerStore.playerId
  )
})

const toggleQuestion = (index) => {
  expandedQuestion.value = expandedQuestion.value === index ? null : index
}

const getMyScore = (questionDetail) => {
   if (!playerStore.playerId) return null
  const mySubmission = questionDetail.playerSubmissions.find(
    s => s.playerId === playerStore.playerId
  )
  
  return mySubmission?.finalScore || 0
}

const getMyChoice = (questionDetail) => {
  if (!playerStore.playerId) return '-'
  const mySubmission = questionDetail.playerSubmissions.find(
    s => s.playerId === playerStore.playerId
  )
  return mySubmission?.choice || '-'
}
</script>

<template>
  <div class="space-y-4 sm:space-y-6">
    <!-- 顶部状态卡片 - 简化版 -->
    <div v-if="currentPlayerRank" 
        class="rounded-lg p-4 sm:p-5 border"
        :class="isPassed 
          ? 'bg-green-50 dark:bg-green-900/10 border-green-200 dark:border-green-800'
          : 'bg-red-50 dark:bg-red-900/10 border-red-200 dark:border-red-800'">
      <div class="flex items-center gap-2 sm:gap-3">
        <i class="text-xl sm:text-2xl" 
          :class="isPassed 
            ? 'pi pi-check-circle text-green-600 dark:text-green-400' 
            : 'pi pi-times-circle text-red-600 dark:text-red-400'">
        </i>
        <div>
          <h1 class="text-lg sm:text-xl font-semibold"
              :class="isPassed 
                ? 'text-green-900 dark:text-green-100' 
                : 'text-red-900 dark:text-red-100'">
            {{ isPassed ? '游戏已结束' : '未达成条件' }}
          </h1>
        </div>
      </div>
    </div>

    <!-- 我的成绩 -->
    <div v-if="currentPlayerRank" 
         class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4 sm:p-6 shadow-sm">
      <div class="flex justify-between items-center">
        <div>
          <p class="text-xs sm:text-sm text-gray-500 dark:text-gray-400 mb-1">排名</p>
          <div class="flex items-baseline gap-2">
            <span class="text-2xl sm:text-3xl font-semibold text-gray-900 dark:text-white">
              #{{ currentPlayerRank.rank }}
            </span>
          </div>
        </div>
        <div class="text-right">
          <p class="text-xs sm:text-sm text-gray-500 dark:text-gray-400 mb-1">总分</p>
          <p class="text-2xl sm:text-3xl font-semibold text-gray-900 dark:text-white">
            {{ currentPlayerRank.totalScore }}
          </p>
        </div>
      </div>
    </div>
    
    <!-- 排行榜 -->
    <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4 sm:p-6 shadow-sm">
      <h2 class="text-base sm:text-lg font-semibold text-gray-900 dark:text-white mb-3 sm:mb-4">排行榜</h2>
      <div class="space-y-2">
        <div
          v-for="player in gameHistory.leaderboard"
          :key="player.playerId"
          class="flex items-center justify-between p-2.5 sm:p-3 rounded-lg"
          :class="player.rank <= 3 ? 'bg-gray-50 dark:bg-gray-700/50' : ''"
        >
          <div class="flex items-center gap-2 sm:gap-3">
            <span class="text-sm sm:text-base font-medium w-6 sm:w-8 text-center text-gray-600 dark:text-gray-400">
              {{ player.rank }}
            </span>
            <span class="font-medium text-sm sm:text-base text-gray-900 dark:text-white truncate">
              {{ player.playerName }}
            </span>
          </div>
          <span class="text-sm sm:text-base font-semibold text-gray-900 dark:text-white">
            {{ player.totalScore }}
          </span>
        </div>
      </div>
    </div>
    
    <!-- 题目详情 -->
    <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4 sm:p-6 shadow-sm">
      <h2 class="text-base sm:text-lg font-semibold text-gray-900 dark:text-white mb-3 sm:mb-4">题目详情</h2>
      
      <div class="space-y-2 sm:space-y-3">
        <div
          v-for="(detail, index) in gameHistory.questionDetails"
          :key="index"
          class="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden"
        >
          <!-- 题目头部 -->
          <div
            @click="toggleQuestion(index)"
            class="p-3 sm:p-4 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="flex justify-between items-center gap-3 sm:gap-4">
              <div class="flex-1 min-w-0">
                <span class="text-xs font-medium text-gray-500 dark:text-gray-400">
                  第 {{ index + 1 }} 题
                </span>
                <p class="font-medium text-sm sm:text-base text-gray-900 dark:text-white mt-1 truncate">
                  {{ detail.questionText }}
                </p>
              </div>
              <div class="flex items-center gap-2 sm:gap-4 flex-shrink-0">
                <div class="text-center">
                  <p class="text-xs text-gray-500 dark:text-gray-400">选择</p>
                  <p class="font-semibold text-sm sm:text-base text-gray-900 dark:text-white">
                    {{ getMyChoice(detail) }}
                  </p>
                </div>
                <div class="text-center">
                  <p class="text-xs text-gray-500 dark:text-gray-400">得分</p>
                  <p class="font-semibold text-sm sm:text-base text-gray-600 dark:text-gray-300">
                    {{ getMyScore(detail) }}
                  </p>
                </div>
                <i class="pi transition-transform text-gray-400 text-sm"
                   :class="expandedQuestion === index ? 'pi-chevron-up' : 'pi-chevron-down'">
                </i>
              </div>
            </div>
          </div>
          
          <!-- 展开详情 -->
          <div v-if="expandedQuestion === index" 
               class="p-4 sm:p-5 bg-gray-50 dark:bg-gray-700/30 border-t border-gray-200 dark:border-gray-700">
            
            <!-- 题目选项 -->
            <div class="mb-4 sm:mb-5 pb-3 sm:pb-4 border-b border-gray-200 dark:border-gray-700">
              <h3 class="text-xs sm:text-sm font-semibold text-gray-900 dark:text-white mb-2">题目选项</h3>
              <p class="text-xs sm:text-sm text-gray-700 dark:text-gray-300">
                {{ detail.optionText }}
              </p>
            </div>

            <!-- 选项分布 -->
            <div class="mb-4 sm:mb-5">
              <h3 class="text-xs sm:text-sm font-semibold text-gray-900 dark:text-white mb-2 sm:mb-3">选项分布</h3>
              <div class="grid grid-cols-2 sm:grid-cols-4 gap-2">
                <div
                  v-for="(count, choice) in detail.choiceCounts"
                  :key="choice"
                  class="bg-white dark:bg-gray-800 p-2.5 sm:p-3 rounded-lg text-center border border-gray-200 dark:border-gray-700"
                >
                  <p class="text-lg sm:text-xl font-semibold text-gray-900 dark:text-white">{{ choice }}</p>
                  <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ count }} 人</p>
                </div>
              </div>
            </div>
            
            <!-- 玩家详情 -->
            <div>
              <h3 class="text-xs sm:text-sm font-semibold text-gray-900 dark:text-white mb-2 sm:mb-3">玩家详情</h3>
              <div class="space-y-2">
                <div
                  v-for="submission in detail.playerSubmissions"
                  :key="submission.playerId"
                  class="flex justify-between items-center p-2 sm:p-2.5 rounded-lg bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700"
                >
                  <span class="font-medium text-gray-900 dark:text-white text-xs sm:text-sm truncate">
                    {{ submission.playerName }}
                  </span>
                  <div class="flex items-center gap-2 sm:gap-3">
                    <span class="text-xs sm:text-sm text-gray-600 dark:text-gray-400">
                      {{ submission.choice }}
                    </span>
                    <span class="font-semibold text-gray-600 dark:text-gray-300 min-w-[40px] sm:min-w-[50px] text-right text-xs sm:text-sm">
                      +{{ submission.finalScore }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>