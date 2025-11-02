<script setup>

const props = defineProps({
  roomCode: String,
  room: Object,
  isRoomOwner: Boolean,
  isAllReady: Boolean,
  onCopyRoomCode: Function
})
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg sm:rounded-xl border border-gray-200 dark:border-gray-700 p-4 sm:p-8">
    <div class="text-center">
      <div class="flex items-center justify-center gap-2 sm:gap-3 mb-2 sm:mb-3">
        <h1 class="text-2xl sm:text-3xl font-semibold text-gray-900 dark:text-white">
          {{ roomCode }}
        </h1>
        <button
          @click="onCopyRoomCode"
          class="p-1.5 sm:p-2 hover:bg-gray-100 dark:hover:bg-gray-700
                 rounded-lg transition-colors"
          title="复制房间码"
        >
          <i class="pi pi-copy text-sm sm:text-base text-gray-500 dark:text-gray-400"></i>
        </button>
      </div>

      <div v-if="room" class="flex items-center justify-center gap-3 sm:gap-4 text-xs sm:text-sm text-gray-600 dark:text-gray-400">
        <span class="flex items-center gap-1.5">
          <i class="pi pi-users"></i>
          {{ room.currentPlayers }}/{{ room.maxPlayers }}
        </span>
        <span class="w-1 h-1 rounded-full bg-gray-300"></span>
        <span class="px-2 py-0.5 rounded-md text-xs font-medium"
              :class="room.status === 'WAITING'
                ? 'bg-yellow-50 text-yellow-700 dark:bg-yellow-900/20 dark:text-yellow-400'
                : 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-400'">
          {{ room.status === 'WAITING' ? '等待中' : room.status }}
        </span>
      </div>
    </div>

    <!-- 游戏信息 -->
    <div v-if="room" class="mt-4 sm:mt-6 pt-4 sm:pt-6 border-t border-gray-200 dark:border-gray-700">
      <div class="grid grid-cols-2 gap-3 sm:gap-4 text-xs sm:text-sm">
        <div class="text-center">
          <p class="text-gray-500 dark:text-gray-400 mb-1">题目数量</p>
          <p class="text-base sm:text-lg font-semibold text-gray-900 dark:text-white">
            {{ room.questionCount || 10 }}
          </p>
        </div>
        <div class="text-center">
          <p class="text-gray-500 dark:text-gray-400 mb-1">准备状态</p>
          <p class="text-base sm:text-lg font-semibold text-gray-900 dark:text-white">
            {{ room.players?.filter(p => !p.spectator && p.ready).length || 0 }}/{{ room.players?.filter(p => !p.spectator).length || 0 }}
          </p>
        </div>
      </div>

      <!-- 排名模式和通关条件 -->
      <div v-if="room?.rankingMode !== 'standard' || room.winConditions"
           class="mt-3 sm:mt-4 pt-3 sm:pt-4 border-t border-gray-200 dark:border-gray-700">
        <div class="text-xs sm:text-sm space-y-2">
          <!-- 排名模式 -->
          <div v-if="room?.rankingMode !== 'standard'"
               class="flex items-center gap-2 text-gray-600 dark:text-gray-400">
            <i class="pi pi-chart-line text-blue-500"></i>
            <span>
              目标：{{
                room.rankingMode === 'closest_to_avg' ? '接近平均分' :
                room.rankingMode === 'closest_to_target' ? `接近 ${room.targetScore} 分` :
                '标准排名'
              }}
            </span>
          </div>
          <!-- 通关条件 -->
          <div v-if="room?.winConditions" class="space-y-1">
            <div v-if="room?.winConditions.minScorePerPlayer"
                 class="flex items-center gap-2 text-gray-600 dark:text-gray-400">
              <i class="pi pi-users text-green-500"></i>
              <span>所有人 ≥ {{ room.winConditions.minScorePerPlayer }} 分</span>
            </div>
            <div v-if="room?.winConditions.minTotalScore"
                 class="flex items-center gap-2 text-gray-600 dark:text-gray-400">
              <i class="pi pi-flag text-purple-500"></i>
              <span>总分 ≥ {{ room.winConditions.minTotalScore }} 分</span>
            </div>
            <div v-if="room?.winConditions.minAvgScore"
                 class="flex items-center gap-2 text-gray-600 dark:text-gray-400">
              <i class="pi pi-chart-bar text-orange-500"></i>
              <span>平均分 ≥ {{ room.winConditions.minAvgScore }} 分</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 提示 -->
    <div class="mt-4 sm:mt-6 space-y-2">
      <p v-if="isRoomOwner" class="text-center text-xs text-gray-500 dark:text-gray-400">
        只有房主可以开始游戏
      </p>
      <p v-if="!isAllReady" class="text-center text-xs text-gray-500 dark:text-gray-400">
        等待所有玩家准备
      </p>
    </div>
  </div>
</template>
