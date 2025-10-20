<script setup>
import { ref } from 'vue'

const props = defineProps({
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['create'])

const questionCount = ref(10)
const maxPlayers = ref(4)

const handleCreate = () => {
  emit('create', {
    maxPlayers: maxPlayers.value,
    questionCount: questionCount.value
  })
}
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6
              border border-gray-100 dark:border-gray-700">
    
    <!-- 标题 -->
    <div class="flex items-center gap-3 mb-6">
      <div class="w-10 h-10 rounded-lg bg-blue-100 dark:bg-blue-900/30 
                  flex items-center justify-center">
        <i class="pi pi-plus text-blue-600 dark:text-blue-400 text-lg"></i>
      </div>
      <h2 class="text-xl font-bold text-gray-800 dark:text-white">
        创建房间
      </h2>
    </div>

    <!-- 表单 -->
    <div class="space-y-4">
      <!-- 题目数量 -->
      <div>
        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          题目数量
        </label>
        <div class="relative">
          <input
            type="number"
            v-model="questionCount"
            class="w-full px-4 py-2.5 
                   bg-gray-50 dark:bg-gray-700
                   border border-gray-300 dark:border-gray-600
                   text-gray-800 dark:text-white
                   rounded-lg
                   focus:ring-2 focus:ring-blue-500 focus:border-transparent
                   transition-all"
            min="1"
            max="20"
            placeholder="1-20"
          />
          <span class="absolute right-3 top-1/2 -translate-y-1/2 
                       text-sm text-gray-400 dark:text-gray-500">
            题
          </span>
        </div>
        <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">
          推荐 5-15 题
        </p>
      </div>

      <!-- 最大玩家数 -->
      <div>
        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          最大玩家数
        </label>
        <div class="relative">
          <input
            type="number"
            v-model="maxPlayers"
            class="w-full px-4 py-2.5
                   bg-gray-50 dark:bg-gray-700
                   border border-gray-300 dark:border-gray-600
                   text-gray-800 dark:text-white
                   rounded-lg
                   focus:ring-2 focus:ring-blue-500 focus:border-transparent
                   transition-all"
            min="2"
            max="10"
            placeholder="2-10"
          />
          <span class="absolute right-3 top-1/2 -translate-y-1/2 
                       text-sm text-gray-400 dark:text-gray-500">
            <i class="pi pi-users"></i>
          </span>
        </div>
        <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">
          推荐 2-6 人
        </p>
      </div>
    </div>
    
    <!-- 创建按钮 -->
    <button
      class="w-full mt-6 px-4 py-3 rounded-lg font-medium
             bg-gradient-to-r from-blue-500 to-blue-600
             hover:from-blue-600 hover:to-blue-700
             text-white transition-all
             disabled:opacity-50 disabled:cursor-not-allowed
             disabled:hover:from-blue-500 disabled:hover:to-blue-600
             flex items-center justify-center gap-2
             shadow-sm hover:shadow-md"
      @click="handleCreate"
      :disabled="loading"
    >
      <i v-if="!loading" class="pi pi-check"></i>
      <i v-else class="pi pi-spin pi-spinner"></i>
      {{ loading ? '创建中...' : '创建房间' }}
    </button>
  </div>
</template>