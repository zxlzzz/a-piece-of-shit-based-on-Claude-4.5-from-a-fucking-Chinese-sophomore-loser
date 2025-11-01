<template>
  <div class="space-y-4 sm:space-y-6">
    <!-- 题目卡片 -->
    <div class="flex justify-center">
      <QuestionCard
        v-if="question"
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

    <!-- 已提交提示 -->
    <transition name="fade">
      <div v-if="hasSubmitted" 
           class="fixed bottom-4 sm:bottom-6 left-1/2 -translate-x-1/2 z-50
                  bg-green-600 text-white px-4 sm:px-5 py-2 sm:py-2.5 
                  rounded-lg text-xs sm:text-sm font-medium shadow-lg">
        已提交
      </div>
    </transition>
  </div>
</template>

<script setup>
import QuestionCard from './QuestionCard.vue'

defineProps({
  question: Object,
  hasSubmitted: Boolean
})

defineEmits(['choose'])
</script>

<style scoped>
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>

<style scoped>
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>