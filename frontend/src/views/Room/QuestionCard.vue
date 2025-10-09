<script setup>
import { ref, watch } from 'vue'
import ChooseBar from './ChooseBar.vue'
import NumberBar from './NumberBar.vue'

const props = defineProps({
  question: {
    type: Object,
    required: true
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['choose'])

const choice = ref(null)

watch(() => props.question?.id, () => {
  choice.value = null
})

const onSubmit = () => {
  if (!choice.value && choice.value !== 0) {
    return
  }
  emit('choose', choice.value)
}
</script>

<template>
  <div class="w-full max-w-2xl bg-white dark:bg-gray-800 rounded-2xl shadow-xl p-8 
              border-2 border-gray-100 dark:border-gray-700">
    
    <!-- 题目类型标签 -->
    <div class="flex items-center gap-2 mb-6">
      <span class="px-3 py-1 bg-purple-100 dark:bg-purple-900/30 
                   text-purple-700 dark:text-purple-300 rounded-full text-sm font-medium">
        {{ question.type === 'choice' ? '选择题' : '数字题' }}
      </span>
    </div>

    <!-- 题目内容 -->
    <div class="mb-8">
      <h2 class="text-xl font-semibold text-gray-800 dark:text-white leading-relaxed">
        {{ question.text }}
      </h2>
    </div>

    <!-- 选项区域 -->
    <div class="mb-8">
      <ChooseBar
        v-if="question.type === 'choice'"
        :key="question.id"
        :options="question.options"
        v-model="choice"
        :disabled="disabled"
      />
      <NumberBar
        v-if="question.type === 'bid'"
        :key="question.id"
        :maxval="question.max"
        :minval="question.min"
        v-model="choice"
        :disabled="disabled"
      />
    </div>

    <!-- 当前选择提示 -->
    <div class="mb-6 p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
      <p class="text-sm text-gray-600 dark:text-gray-400">
        当前选择：
        <span class="font-semibold text-gray-800 dark:text-white">
          {{ choice !== null && choice !== undefined ? choice : '未选择' }}
        </span>
      </p>
    </div>

    <!-- 提交按钮 -->
    <button 
      @click="onSubmit"
      :disabled="disabled || choice === null"
      class="w-full py-4 rounded-xl font-bold text-lg transition-all
             bg-gradient-to-r from-blue-500 to-purple-600
             hover:from-blue-600 hover:to-purple-700
             text-white shadow-lg hover:shadow-xl
             disabled:opacity-50 disabled:cursor-not-allowed
             disabled:hover:from-blue-500 disabled:hover:to-purple-600
             flex items-center justify-center gap-2"
    >
      <i class="pi pi-check"></i>
      {{ disabled ? '已提交' : '提交答案' }}
    </button>
  </div>
</template>