<template>
  <div class="space-y-4 sm:space-y-6">
    <!-- 异步模式：已提交后显示等待视图，替代题目页面 -->
    <transition name="slide-up">
      <div
        v-if="isAsyncMode && hasSubmitted"
        class="bg-white dark:bg-gray-800 rounded-lg sm:rounded-xl
               border border-gray-200 dark:border-gray-700 p-8 sm:p-12 text-center"
      >
        <div class="mb-4">
          <div class="inline-flex items-center justify-center w-16 h-16 rounded-full
                      bg-green-100 dark:bg-green-900/30 mb-4">
            <i class="pi pi-check text-3xl text-green-600 dark:text-green-400"></i>
          </div>
          <h3 class="text-base sm:text-lg font-semibold text-gray-900 dark:text-white mb-1">
            已提交，等待其他玩家
          </h3>
          <p class="text-sm text-gray-500 dark:text-gray-400">
            所有人提交后将自动公布结果
          </p>
        </div>

        <!-- 提交进度 -->
        <div class="mt-6">
          <div class="flex items-center justify-center gap-2 text-sm text-gray-600 dark:text-gray-400 mb-3">
            <i class="pi pi-users"></i>
            <span>{{ submittedCount }}/{{ totalPlayers }} 已提交</span>
          </div>

          <!-- 玩家提交状态列表 -->
          <div v-if="room?.players" class="flex flex-wrap gap-2 justify-center max-w-sm mx-auto">
            <div
              v-for="player in activePlayers"
              :key="player.playerId"
              class="flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border transition-all"
              :class="hasPlayerSubmitted(player.playerId)
                ? 'bg-green-50 border-green-200 text-green-700 dark:bg-green-900/20 dark:border-green-800 dark:text-green-400'
                : 'bg-gray-50 border-gray-200 text-gray-500 dark:bg-gray-700/50 dark:border-gray-600 dark:text-gray-400'"
            >
              <i class="pi text-xs"
                 :class="hasPlayerSubmitted(player.playerId) ? 'pi-check-circle' : 'pi-clock'"></i>
              {{ player.name }}
            </div>
          </div>
        </div>
      </div>
    </transition>

    <!-- 题目卡片（同步模式始终显示；异步模式未提交时显示） -->
    <div v-if="!isAsyncMode || !hasSubmitted" class="flex justify-center">
      <QuestionCard
        v-if="question"
        :key="room?.questionStartTime"
        :question="question"
        :disabled="hasSubmitted"
        @choose="$emit('choose', $event)"
        class="w-full"
      />

      <!-- 等待下一题 -->
      <div v-else
           class="bg-white dark:bg-gray-800 rounded-lg sm:rounded-xl
                  border border-gray-200 dark:border-gray-700
                  p-8 sm:p-12 text-center w-full">
        <i class="pi pi-spin pi-spinner text-3xl sm:text-4xl text-gray-400 mb-3"></i>
        <p class="text-sm sm:text-base text-gray-600 dark:text-gray-400">等待下一题</p>
      </div>
    </div>

    <!-- 同步模式：已提交底部提示 -->
    <transition name="fade">
      <div v-if="!isAsyncMode && hasSubmitted"
           class="fixed bottom-4 sm:bottom-6 left-1/2 -translate-x-1/2 z-50
                  bg-green-600 text-white px-4 sm:px-5 py-2 sm:py-2.5
                  rounded-lg text-xs sm:text-sm font-medium shadow-lg">
        已提交
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import QuestionCard from './QuestionCard.vue'

const props = defineProps({
  question: Object,
  hasSubmitted: Boolean,
  room: Object
})

defineEmits(['choose'])

const isAsyncMode = computed(() => props.room?.gameMode === 'ASYNC')

const activePlayers = computed(() =>
  props.room?.players?.filter(p => !p.spectator) ?? []
)

const submittedCount = computed(() =>
  props.room?.submittedPlayerIds?.length ?? 0
)

const totalPlayers = computed(() => activePlayers.value.length)

const hasPlayerSubmitted = (playerId) =>
  props.room?.submittedPlayerIds?.includes(playerId) ?? false
</script>

<style scoped>
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

.slide-up-enter-active, .slide-up-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}
.slide-up-enter-from, .slide-up-leave-to {
  opacity: 0;
  transform: translateY(12px);
}
</style>
