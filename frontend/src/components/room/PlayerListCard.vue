<script setup>
import { defineProps } from 'vue'
import { generatePlayerColor } from '@/utils/player'

const props = defineProps({
  room: Object,
  currentPlayerId: String
})
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg sm:rounded-xl border border-gray-200 dark:border-gray-700 p-4 sm:p-6">
    <h2 class="text-base sm:text-lg font-semibold text-gray-900 dark:text-white mb-3 sm:mb-4">
      玩家
    </h2>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 sm:gap-3">
      <div
        v-for="(player, index) in room?.players"
        :key="player.playerId"
        class="p-2.5 sm:p-3 rounded-lg border transition-all relative"
        :class="[
          player.ready
            ? 'bg-green-50 border-green-200 dark:bg-green-900/10 dark:border-green-800'
            : 'bg-gray-50 border-gray-200 dark:bg-gray-700/50 dark:border-gray-600',
          player.playerId === currentPlayerId
            ? 'ring-1 ring-blue-500 dark:ring-blue-600'
            : ''
        ]"
      >
        <!-- 房主标识 -->
        <div v-if="index === 0"
             class="absolute -top-1 -right-1 w-4 h-4 sm:w-5 sm:h-5 bg-yellow-400 rounded-full
                    flex items-center justify-center text-xs">
          👑
        </div>

        <div class="flex items-center gap-2 sm:gap-3">
          <!-- 头像 -->
          <div class="w-8 h-8 sm:w-10 sm:h-10 rounded-full bg-gray-200 dark:bg-gray-600
                      flex items-center justify-center text-gray-700 dark:text-gray-300
                      font-medium text-xs sm:text-sm"
               :style="{ backgroundColor: generatePlayerColor(player.playerId) + '20',
                         color: generatePlayerColor(player.playerId) }">
            {{ player.name.charAt(0).toUpperCase() }}
          </div>

          <!-- 信息 -->
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-1.5 sm:gap-2">
              <p class="font-medium text-gray-900 dark:text-white text-xs sm:text-sm truncate">
                {{ player.name }}
              </p>
              <span v-if="player.playerId === currentPlayerId"
                    class="text-xs px-1 py-0.5 sm:px-1.5 bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400 rounded">
                你
              </span>
            </div>
            <p class="text-xs text-gray-500 dark:text-gray-400">
              {{ player.spectator ? '观战中' : (player.ready ? '已准备' : '等待中') }}
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
