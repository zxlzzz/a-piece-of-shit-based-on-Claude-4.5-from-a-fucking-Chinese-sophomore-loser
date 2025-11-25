<script setup>
const props = defineProps({
  isRoomOwner: Boolean,
  isSpectator: Boolean,
  currentPlayerReady: Boolean,
  isAllReady: Boolean,
  loading: Boolean,
  wsConnected: Boolean
})

const emit = defineEmits(['ready', 'start', 'leave', 'showCustom'])
</script>

<template>
  <div class="flex flex-col sm:flex-row gap-2 sm:gap-3">
    <button
      @click="emit('leave')"
      class="w-full sm:w-auto px-4 sm:px-5 py-2.5 rounded-lg text-sm font-medium
             bg-white dark:bg-gray-800
             text-gray-700 dark:text-gray-300
             border border-gray-300 dark:border-gray-600
             hover:bg-gray-50 dark:hover:bg-gray-700
             transition-colors"
    >
      离开
    </button>

    <!-- 自定义按钮（仅房主可见） -->
    <button
      v-if="isRoomOwner"
      @click="emit('showCustom')"
      :disabled="loading || !wsConnected"
      class="w-full sm:w-auto px-4 sm:px-5 py-2.5 rounded-lg text-sm font-medium
            bg-white dark:bg-gray-800
            text-gray-700 dark:text-gray-300
            border border-gray-300 dark:border-gray-600
            hover:bg-gray-50 dark:hover:bg-gray-700
            transition-colors
            disabled:opacity-50 disabled:cursor-not-allowed"
    >
      <i class="pi pi-cog mr-1"></i>
      自定义
    </button>

    <!-- 准备按钮（非观战者） -->
    <button
      v-if="!isSpectator"
      @click="emit('ready')"
      :disabled="currentPlayerReady || loading || !wsConnected"
      class="w-full sm:w-auto px-4 sm:px-5 py-2.5 rounded-lg text-sm font-medium
             transition-colors
             disabled:opacity-50 disabled:cursor-not-allowed"
      :class="currentPlayerReady
        ? 'bg-green-100 text-green-700 border border-green-200 dark:bg-green-900/20 dark:text-green-400 dark:border-green-800'
        : 'bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-600 dark:hover:bg-blue-700'"
    >
      {{ currentPlayerReady ? '已准备' : '准备' }}
    </button>

    <!-- 观战者显示观战中标识 -->
    <div v-else class="w-full sm:w-auto px-4 sm:px-5 py-2.5 rounded-lg text-sm font-medium
                       bg-purple-50 text-purple-700 border border-purple-200
                       dark:bg-purple-900/20 dark:text-purple-400 dark:border-purple-800
                       text-center">
      <i class="pi pi-eye mr-1"></i>
      观战中
    </div>

    <button
      v-if="isRoomOwner"
      @click="emit('start')"
      :disabled="!isAllReady || !wsConnected"
      class="w-full sm:w-auto px-4 sm:px-5 py-2.5 rounded-lg text-sm font-medium
             bg-blue-600 hover:bg-blue-700
             text-white transition-colors
             disabled:opacity-50 disabled:cursor-not-allowed"
    >
      开始游戏
    </button>
  </div>
</template>
