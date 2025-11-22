<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-3 sm:p-6">
    <!-- 页面标题 -->
    <div class="max-w-7xl mx-auto mb-4 sm:mb-8">
      <h1 class="text-xl sm:text-2xl lg:text-3xl font-bold text-gray-800 dark:text-white">题库</h1>
      <p class="text-sm sm:text-base text-gray-600 dark:text-gray-400 mt-1 sm:mt-2">
        共 {{ questions.length }} 道题目
      </p>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="max-w-7xl mx-auto">
      <div class="columns-1 sm:columns-2 lg:columns-3 xl:columns-4 gap-4 sm:gap-6">
        <div 
          v-for="i in 6" 
          :key="i"
          class="break-inside-avoid mb-4 sm:mb-6 h-64 sm:h-80 bg-white dark:bg-gray-800 rounded-lg shadow-sm animate-pulse"
        ></div>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="max-w-7xl mx-auto text-center py-8 sm:py-12">
      <i class="pi pi-exclamation-circle text-4xl sm:text-6xl text-red-500 mb-3 sm:mb-4"></i>
      <p class="text-sm sm:text-base text-gray-600 dark:text-gray-400">{{ error }}</p>
    </div>

    <!-- 🔥 题目列表（瀑布流布局） -->
    <div v-else class="max-w-7xl mx-auto">
      <div class="columns-1 sm:columns-2 lg:columns-3 xl:columns-3 gap-4 sm:gap-6">
        <QuesShowCard
          v-for="q in questions"
          :key="q.id"
          :id="q.id"
          :type="q.type"
          :people="q.minPlayers === q.maxPlayers
            ? (q.minPlayers || '?')
            : `${q.minPlayers || '?'} ~ ${q.maxPlayers || '?'}`"
          :text="q.text"
          :calculate-rule="q.calculateRule"
          :choice="q.options"
          :min="q.min"
          :max="q.max"
          :step="q.step"
          :tags="q.tags"
          @practice="handleOpenPractice"
        />
      </div>
    </div>

    <!-- 🔥 练习模式弹窗 -->
    <PracticeModal
      v-model:visible="showPracticeModal"
      :questionId="selectedQuestionId"
      :playerCount="selectedPlayerCount"
    />
  </div>
</template>

<script setup>
import { logger } from '@/utils/logger'
import { getAllQuestions } from '@/api'
import { onMounted, ref } from 'vue'
import QuesShowCard from './QuesShowCard.vue'
import PracticeModal from '@/components/practice/PracticeModal.vue'

const questions = ref([])
const loading = ref(true)
const error = ref(null)

// 🔥 练习模式相关
const showPracticeModal = ref(false)
const selectedQuestionId = ref(null)
const selectedPlayerCount = ref(2)

const handleOpenPractice = (questionId, playerCount) => {
  selectedQuestionId.value = questionId
  selectedPlayerCount.value = playerCount || 2
  showPracticeModal.value = true
}

onMounted(async () => {
  try {
    const res = await getAllQuestions()
    questions.value = res.data
    logger.debug('题库加载成功:', questions.value)
  } catch (err) {
    logger.error("获取题库失败:", err)
    error.value = "加载失败，请刷新重试"
  } finally {
    loading.value = false
  }
})
</script>