<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
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
  if (choice.value === null || choice.value === undefined || choice.value === '') {
    return
  }
  emit('choose', choice.value)
}

// 🔥 监听全局选择事件（从 GameView 触发）
const handleSelectOption = (e) => {
  if (props.disabled || props.question?.type !== 'choice') return
  
  const selectedKey = e.detail.key
  const option = props.question.options?.find(opt => opt.key === selectedKey)
  if (option) {
    choice.value = selectedKey
  }
}

// 🔥 监听全局提交事件（从 GameView 触发）
const handleSubmitAnswer = () => {
  if (!props.disabled) {
    onSubmit()
  }
}

onMounted(() => {
  window.addEventListener('select-option', handleSelectOption)
  window.addEventListener('submit-answer', handleSubmitAnswer)
})

onUnmounted(() => {
  window.removeEventListener('select-option', handleSelectOption)
  window.removeEventListener('submit-answer', handleSubmitAnswer)
})
</script>

<template>
  <div class="w-full max-w-2xl bg-white dark:bg-gray-800 rounded-2xl shadow-xl p-8
               border-2 border-gray-100 dark:border-gray-700">
         
    <!-- 题目类型标签 -->
    <div class="flex items-center gap-2 mb-6">
      <span class="text-sm text-gray-600 dark:text-gray-400">
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
        :step="question.step || 1"
        v-model="choice"
        :disabled="disabled"
      />
    </div>
     
    <!-- 当前选择提示 -->
    <div class="mb-6 p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
      <p class="text-sm text-gray-600 dark:text-gray-400">
        当前选择：
        <span class="font-semibold text-gray-800 dark:text-white">
          {{ choice !== null && choice !== undefined && choice !== '' ? choice : '未选择' }}
        </span>
      </p>
    </div>
     
    <!-- 提交按钮 -->
    <button 
      @click="onSubmit"
      :disabled="disabled || choice === null || choice === undefined || choice === ''"
      class="w-full py-4 rounded-xl font-bold text-lg transition-all
             bg-blue-600
             hover:bg-blue-700
             text-white shadow-lg hover:shadow-xl
             disabled:opacity-50 disabled:cursor-not-allowed
             disabled:hover:bg-blue-600
             flex items-center justify-center gap-2"
    >
      <i class="pi pi-check"></i>
      {{ disabled ? '已提交' : '提交答案 (Enter)' }}
    </button>
  </div>
</template>