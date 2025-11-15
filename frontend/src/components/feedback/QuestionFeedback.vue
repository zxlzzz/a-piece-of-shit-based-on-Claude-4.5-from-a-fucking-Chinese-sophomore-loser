<script setup>
import { ref, computed } from 'vue'
import { submitQuestionFeedback } from '@/api'

const props = defineProps({
  questionId: {
    type: Number,
    required: true
  }
})

const isExpanded = ref(false)
const rating = ref(null)
const comment = ref('')
const submitted = ref(false)
const submitting = ref(false)
const errorMessage = ref('')

const stars = [1, 2, 3, 4, 5]
const commentLength = computed(() => comment.value.length)
const maxLength = 500

const canSubmit = computed(() => {
  return (rating.value !== null || comment.value.trim().length > 0) && !submitting.value && !submitted.value
})

const setRating = (value) => {
  if (submitted.value) return
  rating.value = rating.value === value ? null : value
}

const toggleExpand = () => {
  isExpanded.value = !isExpanded.value
}

const handleSubmit = async () => {
  if (!canSubmit.value) return

  errorMessage.value = ''
  submitting.value = true

  try {
    await submitQuestionFeedback(props.questionId, {
      rating: rating.value,
      comment: comment.value.trim() || null
    })
    submitted.value = true

    // 2秒后收起
    setTimeout(() => {
      isExpanded.value = false
    }, 2000)
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="mt-3">
    <!-- 收起状态：只显示按钮 -->
    <button
      v-if="!isExpanded"
      @click="toggleExpand"
      class="w-full py-2 px-3 text-sm
             bg-gray-100 hover:bg-gray-200 dark:bg-gray-700 dark:hover:bg-gray-600
             text-gray-700 dark:text-gray-300
             rounded-lg transition-colors duration-150
             flex items-center justify-center gap-2"
    >
      <i class="pi pi-comment text-xs"></i>
      <span>评价这道题</span>
    </button>

    <!-- 展开状态：完整表单 -->
    <div v-else class="bg-gray-50 dark:bg-gray-700/30 rounded-lg p-3 sm:p-4 border border-gray-200 dark:border-gray-600">
      <!-- 标题和收起按钮 -->
      <div class="flex items-center justify-between mb-3">
        <div class="flex items-center gap-2">
          <i class="pi pi-comment text-gray-500 dark:text-gray-400"></i>
          <h4 class="text-sm font-semibold text-gray-900 dark:text-white">题目反馈</h4>
        </div>
        <button
          @click="toggleExpand"
          class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
        >
          <i class="pi pi-times"></i>
        </button>
      </div>

      <!-- 已提交状态 -->
      <div v-if="submitted" class="flex items-center gap-2 text-green-600 dark:text-green-400">
        <i class="pi pi-check-circle"></i>
        <span class="text-sm font-medium">感谢您的反馈！</span>
      </div>

      <!-- 反馈表单 -->
      <div v-else class="space-y-3">
        <!-- 星级评分 -->
        <div>
          <label class="text-xs text-gray-600 dark:text-gray-400 mb-1.5 block">
            星级评价（可选）
          </label>
          <div class="flex gap-1">
            <button
              v-for="star in stars"
              :key="star"
              @click="setRating(star)"
              type="button"
              class="transition-all duration-150"
              :disabled="submitted"
            >
              <i
                class="text-xl sm:text-2xl"
                :class="star <= (rating || 0)
                  ? 'pi pi-star-fill text-yellow-400'
                  : 'pi pi-star text-gray-300 dark:text-gray-600 hover:text-yellow-400'"
              ></i>
            </button>
          </div>
        </div>

        <!-- 文字评价 -->
        <div>
          <label class="text-xs text-gray-600 dark:text-gray-400 mb-1.5 block">
            文字评价（可选，最多500字）
          </label>
          <textarea
            v-model="comment"
            :maxlength="maxLength"
            :disabled="submitted"
            placeholder="说说您对这道题的看法..."
            class="w-full px-3 py-2 text-sm
                   bg-white dark:bg-gray-800
                   border border-gray-300 dark:border-gray-600
                   rounded-lg resize-none
                   focus:outline-none focus:ring-2 focus:ring-blue-500
                   disabled:bg-gray-100 dark:disabled:bg-gray-700
                   disabled:cursor-not-allowed
                   text-gray-900 dark:text-gray-100
                   placeholder:text-gray-400 dark:placeholder:text-gray-500"
            rows="3"
          ></textarea>
          <div class="flex justify-between items-center mt-1">
            <span class="text-xs text-gray-500 dark:text-gray-400">
              {{ commentLength }}/{{ maxLength }}
            </span>
          </div>
        </div>

        <!-- 错误提示 -->
        <div v-if="errorMessage" class="text-xs text-red-600 dark:text-red-400">
          {{ errorMessage }}
        </div>

        <!-- 提交按钮 -->
        <button
          @click="handleSubmit"
          :disabled="!canSubmit"
          class="w-full py-2 px-4 text-sm font-medium rounded-lg
                 transition-colors duration-150
                 disabled:opacity-50 disabled:cursor-not-allowed"
          :class="canSubmit
            ? 'bg-blue-600 hover:bg-blue-700 text-white'
            : 'bg-gray-300 dark:bg-gray-700 text-gray-500 dark:text-gray-400'"
        >
          <span v-if="submitting">
            <i class="pi pi-spin pi-spinner mr-1"></i>
            提交中...
          </span>
          <span v-else>
            提交反馈
          </span>
        </button>
      </div>
    </div>
  </div>
</template>

<!--
  TODO: 后续需要添加防恶意提交机制
  - 前端：LocalStorage记录已反馈题目，限制重复提交
  - 后端：IP限流、内容校验等
-->
