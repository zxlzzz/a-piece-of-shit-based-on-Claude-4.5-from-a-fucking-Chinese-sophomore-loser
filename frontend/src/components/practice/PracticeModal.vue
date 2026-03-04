<script setup>
import { ref, computed, watch } from 'vue'
import { startPractice, submitPractice } from '@/api'
import { usePlayerStore } from '@/stores/player'
import ChooseBar from '@/components/game/ChooseBar.vue'
import NumberBar from '@/components/game/NumberBar.vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'

const props = defineProps({
  visible: {
    type: Boolean,
    required: true
  },
  questionId: {
    type: Number,
    default: null  // null表示随机题目
  },
  playerCount: {
    type: Number,
    required: true
  }
})

const emit = defineEmits(['update:visible', 'close'])

const playerStore = usePlayerStore()

// 会话状态
const loading = ref(false)
const session = ref(null)
const choice = ref(null)
const result = ref(null)
const error = ref(null)

// 当前步骤：'question' 或 'result'
const step = ref('question')

// 计算属性
const question = computed(() => session.value?.question)
const canSubmit = computed(() => choice.value !== null && choice.value !== undefined && choice.value !== '')

// 监听visible变化，重置状态
watch(() => props.visible, (newVal) => {
  if (newVal) {
    resetState()
    loadPracticeQuestion()
  }
})

// 重置状态
const resetState = () => {
  session.value = null
  choice.value = null
  result.value = null
  error.value = null
  step.value = 'question'
}

// 加载练习题目
const loadPracticeQuestion = async () => {
  loading.value = true
  error.value = null

  try {
    const response = await startPractice(props.questionId, props.playerCount)
    session.value = response.data
    step.value = 'question'
  } catch (err) {
    error.value = err.response?.data?.message || '加载题目失败'
    console.error('加载练习题目失败:', err)
  } finally {
    loading.value = false
  }
}

// 提交答案
const handleSubmit = async () => {
  if (!canSubmit.value) return

  loading.value = true
  error.value = null

  try {
    const playerId = playerStore.id || null
    const response = await submitPractice(session.value.sessionId, String(choice.value), playerId)
    result.value = response.data
    step.value = 'result'
  } catch (err) {
    error.value = err.response?.data?.message || '提交失败'
    console.error('提交练习答案失败:', err)
  } finally {
    loading.value = false
  }
}

// 关闭弹窗
const handleClose = () => {
  emit('update:visible', false)
  emit('close')
}

// 再来一题
const handleRetry = () => {
  resetState()
  loadPracticeQuestion()
}

// 获取选项文本
const getOptionText = (optionKey) => {
  if (!question.value?.options) return optionKey
  const option = question.value.options.find(opt => opt.key === optionKey || opt.value === optionKey)
  return option ? `${option.key}. ${option.text}` : optionKey
}

// botChoices 是 { bot1: "A", bot2: "3", ... } 格式的对象
const botEntries = computed(() => {
  if (!result.value?.botChoices) return []
  return Object.entries(result.value.botChoices).map(([botId, choice], idx) => ({
    botId,
    label: `Bot ${idx + 1}`,
    choice,
    score: result.value.allScores?.[botId] ?? 0
  }))
})
</script>

<template>
  <Dialog
    :visible="visible"
    @update:visible="handleClose"
    modal
    :closable="true"
    :dismissableMask="true"
    class="practice-dialog"
    :style="{
      width: '95vw',
      maxWidth: '900px',
      backgroundColor: 'white',
      borderRadius: '1rem'
    }"
    :pt="{
      root: { style: 'background: white; border-radius: 1rem; padding: 1.5rem;' },
      header: { style: 'background: white; padding: 1rem 0;' },
      content: { style: 'background: white; padding: 1.5rem 0;' },
      footer: { style: 'background: white; padding: 1rem 0;' }
    }"
  >
    <template #header>
      <div class="flex items-center gap-3">
        <i class="pi pi-book text-2xl text-blue-600 dark:text-blue-400"></i>
        <h2 class="text-xl font-bold text-gray-800 dark:text-white">
          {{ step === 'question' ? '练习模式' : '练习结果' }}
        </h2>
      </div>
    </template>

    <!-- 加载状态 -->
    <div v-if="loading" class="flex flex-col items-center justify-center py-12">
      <i class="pi pi-spin pi-spinner text-4xl text-blue-600 dark:text-blue-400 mb-4"></i>
      <p class="text-gray-600 dark:text-gray-400">{{ step === 'question' ? '加载中...' : '提交中...' }}</p>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="flex flex-col items-center justify-center py-12">
      <i class="pi pi-exclamation-circle text-4xl text-red-500 mb-4"></i>
      <p class="text-gray-600 dark:text-gray-400 mb-4">{{ error }}</p>
      <Button label="重试" icon="pi pi-refresh" @click="loadPracticeQuestion" />
    </div>

    <!-- 题目显示 -->
    <div v-else-if="step === 'question' && question" class="py-4">
      <!-- 人数提示 -->
      <div class="flex items-center gap-2 mb-6">
        <span class="inline-flex items-center gap-2 px-3 py-1.5
                     bg-blue-50 dark:bg-blue-900/30
                     text-blue-700 dark:text-blue-300
                     rounded-full text-sm font-medium">
          <i class="pi pi-users text-xs"></i>
          {{ playerCount }} 人模式
        </span>
      </div>

      <!-- 题目内容 -->
      <div class="mb-6">
        <h3 class="text-lg font-semibold text-gray-800 dark:text-white leading-relaxed">
          {{ question.text }}
        </h3>
      </div>

      <!-- 计分规则 -->
      <div v-if="question.calculateRule" class="mb-6">
        <div class="bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20
                    rounded-lg p-4 border-l-4 border-purple-500">
          <div class="flex items-center gap-2 mb-2">
            <i class="pi pi-calculator text-purple-600 dark:text-purple-400"></i>
            <span class="font-semibold text-purple-700 dark:text-purple-300 text-sm">计分规则</span>
          </div>
          <div class="text-gray-700 dark:text-gray-300 text-sm leading-relaxed whitespace-pre-line">
            {{ question.calculateRule }}
          </div>
        </div>
      </div>

      <!-- 选项区域 -->
      <div class="mb-6">
        <ChooseBar
          v-if="question.type === 'CHOICE'"
          :options="question.options"
          v-model="choice"
          :disabled="false"
        />
        <NumberBar
          v-else-if="question.type === 'BID'"
          :maxval="question.max"
          :minval="question.min"
          :step="question.step || 1"
          v-model="choice"
          :disabled="false"
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
    </div>

    <!-- 结果显示 -->
    <div v-else-if="step === 'result' && result" class="py-4">
      <!-- 题目回顾 -->
      <div class="mb-6">
        <h3 class="text-lg font-semibold text-gray-800 dark:text-white mb-3">
          {{ result.question.text }}
        </h3>

        <!-- 计分规则 -->
        <div v-if="result.question.calculateRule" class="bg-gray-50 dark:bg-gray-800 rounded-lg p-3 mb-3">
          <div class="flex items-center gap-2 mb-1">
            <i class="pi pi-calculator text-gray-600 dark:text-gray-400 text-sm"></i>
            <span class="font-medium text-gray-700 dark:text-gray-300 text-sm">计分规则</span>
          </div>
          <p class="text-sm text-gray-600 dark:text-gray-400 leading-relaxed whitespace-pre-line">
            {{ result.question.calculateRule }}
          </p>
        </div>

        <!-- 选项列表（仅选择题） -->
        <div v-if="result.question.type === 'CHOICE' && result.question.options" class="space-y-2">
          <div v-for="option in result.question.options" :key="option.key"
               class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
            <span class="font-medium">{{ option.key }}.</span>
            <span>{{ option.text }}</span>
          </div>
        </div>
      </div>

      <!-- 所有选手选择和得分 -->
      <div class="grid gap-4 mb-6" :class="botEntries.length === 1 ? 'grid-cols-2' : 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3'">
        <!-- 玩家 -->
        <div class="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-4">
          <div class="flex items-center gap-2 mb-3">
            <i class="pi pi-user text-blue-600 dark:text-blue-400"></i>
            <span class="font-semibold text-gray-800 dark:text-white">你</span>
          </div>
          <div class="space-y-2">
            <div>
              <p class="text-xs text-gray-600 dark:text-gray-400">选择</p>
              <p class="text-lg font-bold text-gray-800 dark:text-white">
                {{ result.question.type === 'CHOICE' ? getOptionText(result.playerChoice) : result.playerChoice }}
              </p>
            </div>
            <div>
              <p class="text-xs text-gray-600 dark:text-gray-400">得分</p>
              <p class="text-2xl font-bold text-blue-600 dark:text-blue-400">
                {{ result.playerScore }}
              </p>
            </div>
          </div>
        </div>

        <!-- 每个 Bot -->
        <div
          v-for="bot in botEntries"
          :key="bot.botId"
          class="bg-purple-50 dark:bg-purple-900/20 rounded-lg p-4"
        >
          <div class="flex items-center gap-2 mb-3">
            <i class="pi pi-desktop text-purple-600 dark:text-purple-400"></i>
            <span class="font-semibold text-gray-800 dark:text-white">{{ bot.label }}</span>
          </div>
          <div class="space-y-2">
            <div>
              <p class="text-xs text-gray-600 dark:text-gray-400">选择</p>
              <p class="text-lg font-bold text-gray-800 dark:text-white">
                {{ result.question.type === 'CHOICE' ? getOptionText(bot.choice) : bot.choice }}
              </p>
            </div>
            <div>
              <p class="text-xs text-gray-600 dark:text-gray-400">得分</p>
              <p class="text-2xl font-bold text-purple-600 dark:text-purple-400">
                {{ bot.score }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- 提示信息 -->
      <div class="bg-gray-50 dark:bg-gray-800 rounded-lg p-4 mb-6">
        <p class="text-sm text-gray-600 dark:text-gray-400 text-center">
          <i class="pi pi-info-circle mr-2"></i>
          Bot的选择基于真实玩家的统计数据生成
        </p>
      </div>
    </div>

    <template #footer>
      <div class="flex gap-3">
        <Button
          v-if="step === 'question'"
          label="提交答案"
          icon="pi pi-check"
          :disabled="!canSubmit || loading"
          @click="handleSubmit"
          severity="primary"
          class="flex-1"
        />
        <template v-else>
          <Button
            label="再来一题"
            icon="pi pi-refresh"
            @click="handleRetry"
            severity="secondary"
            outlined
          />
          <Button
            label="关闭"
            icon="pi pi-times"
            @click="handleClose"
            severity="secondary"
          />
        </template>
      </div>
    </template>
  </Dialog>
</template>

<style scoped>
/* 强制白色背景，覆盖PrimeVue默认样式 */
:deep(.p-dialog) {
  border-radius: 1rem !important;
  background: white !important;
  background-color: white !important;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04) !important;
}

/* 暗色模式 */
:deep(.dark .p-dialog) {
  background: rgb(31, 41, 55) !important;
  background-color: rgb(31, 41, 55) !important;
}

:deep(.p-dialog-header) {
  border-top-left-radius: 1rem !important;
  border-top-right-radius: 1rem !important;
  background: white !important;
  background-color: white !important;
}

:deep(.dark .p-dialog-header) {
  background: rgb(31, 41, 55) !important;
  background-color: rgb(31, 41, 55) !important;
}

:deep(.p-dialog-content) {
  padding: 0 1.5rem !important;
  background: white !important;
  background-color: white !important;
}

:deep(.dark .p-dialog-content) {
  background: rgb(31, 41, 55) !important;
  background-color: rgb(31, 41, 55) !important;
}

:deep(.p-dialog-footer) {
  border-bottom-left-radius: 1rem !important;
  border-bottom-right-radius: 1rem !important;
  background: white !important;
  background-color: white !important;
}

:deep(.dark .p-dialog-footer) {
  background: rgb(31, 41, 55) !important;
  background-color: rgb(31, 41, 55) !important;
}
</style>
