<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg sm:rounded-xl 
              border border-gray-200 dark:border-gray-700 p-3 sm:p-5">
    <div class="flex items-center justify-between flex-wrap gap-2 sm:gap-4">
      <!-- 左侧：房间信息 -->
      <div class="flex items-center gap-2 sm:gap-4 flex-wrap">
        <h1 class="text-lg sm:text-xl font-semibold text-gray-900 dark:text-white">
          {{ roomCode }}
        </h1>
        <div class="px-2 sm:px-3 py-0.5 sm:py-1 
                    bg-blue-50 dark:bg-blue-900/20 
                    text-blue-700 dark:text-blue-300 
                    rounded-md text-xs sm:text-sm font-medium">
          {{ currentQuestionIndex }}/{{ totalQuestions }}
        </div>
      </div>
      
      <!-- 右侧：状态和操作 -->
      <div class="flex items-center gap-2 sm:gap-3 flex-wrap">
        <!-- 倒计时 -->
        <div class="px-2 sm:px-3 py-0.5 sm:py-1 rounded-md font-semibold text-xs sm:text-sm"
             :class="countdown <= 10 
               ? 'bg-red-50 text-red-700 dark:bg-red-900/20 dark:text-red-400' 
               : 'bg-green-50 text-green-700 dark:bg-green-900/20 dark:text-green-400'">
          {{ countdown }}s
        </div>
        
        <!-- 提交状态 -->
        <div class="px-2 sm:px-3 py-0.5 sm:py-1 
                    bg-gray-100 dark:bg-gray-700 
                    text-gray-700 dark:text-gray-300 
                    rounded-md text-xs sm:text-sm">
          {{ submittedPlayers }}/{{ totalPlayers }}
        </div>
        
        <!-- 聊天切换按钮 -->
        <button 
          @click="$emit('toggleChat')"
          class="relative p-1.5 sm:p-2 hover:bg-gray-100 dark:hover:bg-gray-700 
                 rounded-lg transition-colors"
        >
          <i :class="showChat ? 'pi pi-times' : 'pi pi-comment'" 
             class="text-sm sm:text-base text-gray-600 dark:text-gray-400"></i>
          
          <!-- 未读消息红点 -->
          <span v-if="hasUnreadMessages && !showChat"
                class="absolute -top-0.5 -right-0.5 
                       w-2 h-2 bg-red-500 rounded-full 
                       animate-pulse"></span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  roomCode: String,
  currentQuestionIndex: Number,
  totalQuestions: Number,
  countdown: Number,
  submittedPlayers: Number,
  totalPlayers: Number,
  showChat: Boolean,
  hasUnreadMessages: Boolean
})

defineEmits(['toggleChat'])
</script>