<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-6">
    <!-- 页面标题 -->
    <div class="max-w-7xl mx-auto mb-8">
      <h1 class="text-3xl font-bold text-gray-800 dark:text-white">题库</h1>
      <p class="text-gray-600 dark:text-gray-400 mt-2">
        共 {{ questions.length }} 道题目
      </p>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="max-w-7xl mx-auto">
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
        <div 
          v-for="i in 6" 
          :key="i"
          class="h-80 bg-white dark:bg-gray-800 rounded-lg shadow-sm animate-pulse"
        ></div>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="max-w-7xl mx-auto text-center py-12">
      <i class="pi pi-exclamation-circle text-6xl text-red-500 mb-4"></i>
      <p class="text-gray-600 dark:text-gray-400">{{ error }}</p>
    </div>

    <!-- 题目列表 -->
    <div v-else class="max-w-7xl mx-auto">
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        <QuesShowCard
          v-for="q in questions"
          :key="q.id"
          :type="q.type"
          :people="q.minPlayers === q.maxPlayers 
            ? (q.minPlayers || '?') 
            : `${q.minPlayers || '?'} ~ ${q.maxPlayers || '?'}`"
          :text="q.text"
          :choice="q.options"
          :min="q.min"
          :max="q.max"
          :step="q.step"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { getAllQuestions } from '@/api'
import { onMounted, ref } from 'vue'
import QuesShowCard from './QuesShowCard.vue'

const questions = ref([])
const loading = ref(true)
const error = ref(null)

onMounted(async () => {
  try {
    const res = await getAllQuestions()
    questions.value = res.data
  } catch (err) {
    console.error("获取题库失败:", err)
    error.value = "加载失败，请刷新重试"
  } finally {
    loading.value = false
  }
})
</script>