<script setup>
import { computed } from 'vue'

const props = defineProps({
  room: {
    type: Object,
    required: true
  },
  isCurrent: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['enter', 'leave'])

const statusText = computed(() => {
  switch (props.room.status) {
    case 'WAITING': return '等待中'
    case 'PLAYING': return '游戏中'
    case 'FINISHED': return '已结束'
    default: return '未知'
  }
})

const statusClass = computed(() => {
  switch (props.room.status) {
    case 'WAITING': 
      return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
    case 'PLAYING': 
      return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
    case 'FINISHED': 
      return 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-400'
    default: 
      return 'bg-gray-100 text-gray-700'
  }
})
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6
              border-2 transition-all"
       :class="isCurrent 
         ? 'border-blue-500 dark:border-blue-600' 
         : 'border-gray-100 dark:border-gray-700'">
    
    <!-- 当前房间标签 -->
    <div v-if="isCurrent" 
         class="flex items-center gap-2 mb-4 
                text-blue-600 dark:text-blue-400 text-sm font-medium">
      <i class="pi pi-bookmark-fill"></i>
      当前房间
    </div>

    <!-- 房间信息 -->
    <div class="flex justify-between items-start mb-4">
      <div>
        <h3 class="text-2xl font-bold text-gray-800 dark:text-white mb-1">
          {{ room.roomCode }}
        </h3>
        <span :class="['text-xs px-3 py-1 rounded-full font-medium', statusClass]">
          {{ statusText }}
        </span>
      </div>
    </div>
    
    <!-- 玩家数量 -->
    <div class="flex items-center justify-between py-3 px-4 
                bg-gray-50 dark:bg-gray-700/50 rounded-lg mb-4">
      <span class="text-sm text-gray-600 dark:text-gray-400">
        玩家数量
      </span>
      <span class="font-semibold text-gray-800 dark:text-white">
        {{ room.currentPlayers }}/{{ room.maxPlayers }}
      </span>
    </div>
    
    <!-- 玩家列表 -->
    <div v-if="room.players && room.players.length > 0" class="mb-4">
      <p class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">
        玩家列表
      </p>
      <div class="space-y-2">
        <div 
          v-for="player in room.players" 
          :key="player.id"
          class="flex items-center justify-between py-2 px-3
                 bg-gray-50 dark:bg-gray-700/50 rounded-lg"
        >
          <div class="flex items-center gap-2">
            <div class="w-6 h-6 rounded-full bg-gradient-to-br from-purple-500 to-pink-500 
                        flex items-center justify-center text-white text-xs font-bold">
              {{ player.name.charAt(0).toUpperCase() }}
            </div>
            <span class="text-sm text-gray-700 dark:text-gray-300">
              {{ player.name }}
            </span>
          </div>
          <i v-if="player.ready" 
             class="pi pi-check-circle text-green-500 dark:text-green-400"></i>
        </div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="flex gap-2">
      <button
        class="flex-1 px-4 py-2.5 rounded-lg font-medium
               bg-blue-500 hover:bg-blue-600 
               text-white transition-colors
               disabled:opacity-50 disabled:cursor-not-allowed
               flex items-center justify-center gap-2"
        @click="$emit('enter')"
        :disabled="room.status === 'FINISHED'"
      >
        <i class="pi pi-arrow-right"></i>
        进入房间
      </button>
      <button
        class="px-4 py-2.5 rounded-lg font-medium
               bg-gray-100 dark:bg-gray-700
               text-gray-700 dark:text-gray-300
               hover:bg-gray-200 dark:hover:bg-gray-600
               transition-colors
               flex items-center justify-center"
        @click="$emit('leave')"
      >
        <i class="pi pi-times"></i>
      </button>
    </div>
  </div>
</template>