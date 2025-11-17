<script setup>
import { ref, computed } from 'vue'
import { submitUserFeedback } from '@/api'

const feedbackType = ref('QUESTION_SUGGESTION')
const content = ref('')
const nickname = ref('')
const contact = ref('')
const submitting = ref(false)
const submitted = ref(false)
const errorMessage = ref('')

const contentLength = computed(() => content.value.length)
const maxLength = 2000

const canSubmit = computed(() => {
  return content.value.trim().length > 0 && !submitting.value && !submitted.value
})

const handleSubmit = async () => {
  if (!canSubmit.value) return

  errorMessage.value = ''
  submitting.value = true

  try {
    await submitUserFeedback({
      type: feedbackType.value,
      content: content.value.trim(),
      nickname: nickname.value.trim() || null,
      contact: contact.value.trim() || null
    })

    submitted.value = true

    // 3秒后重置表单
    setTimeout(() => {
      submitted.value = false
      content.value = ''
      nickname.value = ''
      contact.value = ''
      feedbackType.value = 'QUESTION_SUGGESTION'
    }, 3000)
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-3 sm:p-6">
    <div class="max-w-3xl mx-auto">
      <!-- 页面标题 -->
      <div class="mb-6 sm:mb-8">
        <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-white mb-2">
          联系作者
        </h1>
        <p class="text-sm sm:text-base text-gray-600 dark:text-gray-400">
          欢迎提交题目建议或其他反馈，您的意见对我们很重要
        </p>
      </div>

      <!-- 提交成功提示 -->
      <div v-if="submitted"
           class="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800
                  rounded-lg p-4 sm:p-6 mb-6 flex items-center gap-3">
        <i class="pi pi-check-circle text-2xl text-green-600 dark:text-green-400"></i>
        <div>
          <h3 class="font-semibold text-green-900 dark:text-green-100 mb-1">
            提交成功！
          </h3>
          <p class="text-sm text-green-700 dark:text-green-300">
            感谢您的反馈，我们会认真查看
          </p>
        </div>
      </div>

      <!-- 反馈表单 -->
      <div v-else class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-4 sm:p-6">
        <form @submit.prevent="handleSubmit" class="space-y-4 sm:space-y-6">

          <!-- 反馈类型 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
              反馈类型 <span class="text-red-500">*</span>
            </label>
            <div class="flex flex-col sm:flex-row gap-3 sm:gap-4">
              <label class="flex items-center cursor-pointer group">
                <input
                  type="radio"
                  v-model="feedbackType"
                  value="QUESTION_SUGGESTION"
                  class="w-4 h-4 text-blue-600 border-gray-300 focus:ring-blue-500 cursor-pointer"
                />
                <span class="ml-2 text-sm sm:text-base text-gray-700 dark:text-gray-300 group-hover:text-blue-600 dark:group-hover:text-blue-400">
                  题目建议
                </span>
              </label>
              <label class="flex items-center cursor-pointer group">
                <input
                  type="radio"
                  v-model="feedbackType"
                  value="OTHER"
                  class="w-4 h-4 text-blue-600 border-gray-300 focus:ring-blue-500 cursor-pointer"
                />
                <span class="ml-2 text-sm sm:text-base text-gray-700 dark:text-gray-300 group-hover:text-blue-600 dark:group-hover:text-blue-400">
                  其他
                </span>
              </label>
            </div>
          </div>

          <!-- 反馈内容 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              详细内容 <span class="text-red-500">*</span>
            </label>
            <textarea
              v-model="content"
              :maxlength="maxLength"
              placeholder="请详细描述您的建议或问题..."
              class="w-full px-3 sm:px-4 py-2 sm:py-3 text-sm sm:text-base
                     bg-white dark:bg-gray-900
                     border border-gray-300 dark:border-gray-600
                     rounded-lg resize-none
                     focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
                     text-gray-900 dark:text-gray-100
                     placeholder:text-gray-400 dark:placeholder:text-gray-500"
              rows="8"
            ></textarea>
            <div class="flex justify-between items-center mt-2">
              <span class="text-xs sm:text-sm text-gray-500 dark:text-gray-400">
                {{ contentLength }}/{{ maxLength }}
              </span>
              <span v-if="feedbackType === 'QUESTION_SUGGESTION'" class="text-xs text-gray-500 dark:text-gray-400">
                💡 可以包含题目文本、规则、选项等
              </span>
            </div>
          </div>

          <!-- 昵称（可选） -->
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              昵称（可选）
            </label>
            <input
              type="text"
              v-model="nickname"
              maxlength="100"
              placeholder="您的昵称或称呼"
              class="w-full px-3 sm:px-4 py-2 sm:py-3 text-sm sm:text-base
                     bg-white dark:bg-gray-900
                     border border-gray-300 dark:border-gray-600
                     rounded-lg
                     focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
                     text-gray-900 dark:text-gray-100
                     placeholder:text-gray-400 dark:placeholder:text-gray-500"
            />
          </div>

          <!-- 联系方式（可选） -->
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              联系方式（可选）
            </label>
            <input
              type="text"
              v-model="contact"
              maxlength="200"
              placeholder="邮箱、QQ、微信等（如需回复）"
              class="w-full px-3 sm:px-4 py-2 sm:py-3 text-sm sm:text-base
                     bg-white dark:bg-gray-900
                     border border-gray-300 dark:border-gray-600
                     rounded-lg
                     focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
                     text-gray-900 dark:text-gray-100
                     placeholder:text-gray-400 dark:placeholder:text-gray-500"
            />
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
              如需作者回复，请留下联系方式
            </p>
          </div>

          <!-- 错误提示 -->
          <div v-if="errorMessage"
               class="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800
                      rounded-lg p-3 text-sm text-red-700 dark:text-red-300">
            {{ errorMessage }}
          </div>

          <!-- 提交按钮 -->
          <button
            type="submit"
            :disabled="!canSubmit"
            class="w-full py-3 px-4 text-base font-medium rounded-lg
                   transition-colors duration-150
                   disabled:opacity-50 disabled:cursor-not-allowed"
            :class="canSubmit
              ? 'bg-blue-600 hover:bg-blue-700 text-white'
              : 'bg-gray-300 dark:bg-gray-700 text-gray-500 dark:text-gray-400'"
          >
            <span v-if="submitting">
              <i class="pi pi-spin pi-spinner mr-2"></i>
              提交中...
            </span>
            <span v-else>
              <i class="pi pi-send mr-2"></i>
              提交反馈
            </span>
          </button>
        </form>
      </div>

      <!-- 底部说明 -->
      <div class="mt-6 text-center text-xs sm:text-sm text-gray-500 dark:text-gray-400">
        <p>您的反馈将匿名提交，我们会认真对待每一条建议</p>
      </div>
    </div>
  </div>
</template>

<!--
  TODO: 后续可考虑添加防恶意提交机制
  - 前端：LocalStorage记录提交时间，限制提交频率
  - 后端：IP限流、内容校验等
-->
