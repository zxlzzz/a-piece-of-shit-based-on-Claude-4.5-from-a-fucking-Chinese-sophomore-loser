<script setup>
import { usePlayerStore } from '@/stores/player'
import { computed, ref } from 'vue'

const props = defineProps({
  gameHistory: {
    type: Object,
    required: true
  }
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
  <div class="space-y-6">
    <!-- 我的成绩 -->
    <div v-if="currentPlayerRank" 
         class="bg-white dark:bg-gray-800 rounded-xl border-2 border-blue-200 dark:border-blue-800 p-8">
      <div class="flex justify-between items-center">
        <div>
          <p class="text-sm text-gray-500 dark:text-gray-400 mb-1">排名</p>
          <div class="flex items-baseline gap-2">
            <span class="text-5xl font-semibold text-gray-900 dark:text-white">
              #{{ currentPlayerRank.rank }}
            </span>
            <span v-if="currentPlayerRank.rank === 1" class="text-2xl">🏆</span>
          </div>
        </div>
        <div class="text-right">
          <p class="text-sm text-gray-500 dark:text-gray-400 mb-1">总分</p>
          <p class="text-5xl font-semibold text-gray-900 dark:text-white">
            {{ currentPlayerRank.totalScore }}
          </p>
        </div>
      </div>
    </div>
    
    <!-- 排行榜 -->
    <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
      <h2 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">排行榜</h2>
      <div class="space-y-2">
        <div
          v-for="player in gameHistory.leaderboard"
          :key="player.playerId"
          class="flex items-center justify-between p-3 rounded-lg"
          :class="player.rank <= 3 ? 'bg-gray-50 dark:bg-gray-700/50' : ''"
        >
          <div class="flex items-center gap-3">
            <span class="text-xl font-semibold w-8 text-center"
                  :class="player.rank === 1 ? 'text-yellow-600' 
                    : player.rank === 2 ? 'text-gray-400' 
                    : player.rank === 3 ? 'text-orange-600'
                    : 'text-gray-600 dark:text-gray-400'">
              {{ player.rank === 1 ? '🥇' : player.rank === 2 ? '🥈' : player.rank === 3 ? '🥉' : player.rank }}
            </span>
            <span class="font-medium text-gray-900 dark:text-white">
              {{ player.playerName }}
            </span>
          </div>
          <span class="text-lg font-semibold text-gray-900 dark:text-white">
            {{ player.totalScore }}
          </span>
        </div>
      </div>
    </div>
    
    <!-- 题目详情 -->
    <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
      <h2 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">题目详情</h2>
      
      <div class="space-y-3">
        <div
          v-for="(detail, index) in gameHistory.questionDetails"
          :key="index"
          class="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden"
        >
          <!-- 题目头部 -->
          <div
            @click="toggleQuestion(index)"
            class="p-4 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="flex justify-between items-center gap-4">
              <div class="flex-1 min-w-0">
                <span class="text-xs font-medium text-blue-600 dark:text-blue-400">
                  第 {{ index + 1 }} 题
                </span>
                <p class="font-medium text-gray-900 dark:text-white mt-1 truncate">
                  {{ detail.questionText }}
                </p>
              </div>
              <div class="flex items-center gap-4 flex-shrink-0">
                <div class="text-center">
                  <p class="text-xs text-gray-500 dark:text-gray-400">选择</p>
                  <p class="font-semibold text-gray-900 dark:text-white">
                    {{ getMyChoice(detail) }}
                  </p>
                </div>
                <div class="text-center">
                  <p class="text-xs text-gray-500 dark:text-gray-400">得分</p>
                  <p class="font-semibold text-green-600 dark:text-green-400">
                    {{ getMyScore(detail) }}
                  </p>
                </div>
                <i class="pi transition-transform text-gray-400"
                   :class="expandedQuestion === index ? 'pi-chevron-up' : 'pi-chevron-down'">
                </i>
              </div>
            </div>
          </div>
          
          <!-- 展开详情 -->
          <div v-if="expandedQuestion === index" 
               class="p-5 bg-gray-50 dark:bg-gray-700/30 border-t border-gray-200 dark:border-gray-700">
            

            <div class="mb-5 pb-4 border-b border-gray-200 dark:border-gray-700">
              <h3 class="text-sm font-semibold text-gray-900 dark:text-white mb-2">题目选项</h3>
              <p class="text-sm text-gray-700 dark:text-gray-300">
                {{ detail.optionText }}
              </p>
            </div>
            <!-- 选项分布 -->
            <div class="mb-5">
              <h3 class="text-sm font-semibold text-gray-900 dark:text-white mb-3">选项分布</h3>
              <div class="grid grid-cols-2 sm:grid-cols-4 gap-2">
                <div
                  v-for="(count, choice) in detail.choiceCounts"
                  :key="choice"
                  class="bg-white dark:bg-gray-800 p-3 rounded-lg text-center border border-gray-200 dark:border-gray-700"
                >
                  <p class="text-2xl font-semibold text-gray-900 dark:text-white">{{ choice }}</p>
                  <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ count }} 人</p>
                </div>
              </div>
            </div>
            
            <!-- 玩家详情 -->
            <div>
              <h3 class="text-sm font-semibold text-gray-900 dark:text-white mb-3">玩家详情</h3>
              <div class="space-y-2">
                <div
                  v-for="submission in detail.playerSubmissions"
                  :key="submission.playerId"
                  class="flex justify-between items-center p-2.5 rounded-lg bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700"
                >
                  <span class="font-medium text-gray-900 dark:text-white text-sm">
                    {{ submission.playerName }}
                  </span>
                  <div class="flex items-center gap-3">
                    <span class="text-sm text-gray-600 dark:text-gray-400">
                      {{ submission.choice }}
                    </span>
                    <span class="font-semibold text-green-600 dark:text-green-400 min-w-[50px] text-right text-sm">
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