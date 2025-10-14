<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  maxQuestions: {
    type: Number,
    default: 10
  },
  currentSettings: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['submit', 'cancel'])

// 🔥 折叠状态
const showAdvanced = ref(false)

// 🔥 表单数据（从 props 初始化）
const formData = ref({
  questionCount: props.currentSettings?.questionCount || 10,
  rankingMode: props.currentSettings?.rankingMode || 'standard',
  targetScore: props.currentSettings?.targetScore || null,
  winConditions: {
    minScorePerPlayer: props.currentSettings?.winConditions?.minScorePerPlayer || null,
    minTotalScore: props.currentSettings?.winConditions?.minTotalScore || null,
    minAvgScore: props.currentSettings?.winConditions?.minAvgScore || null
  }
})

// 🔥 计算属性：是否有通关条件
const hasWinConditions = computed(() => {
  const wc = formData.value.winConditions
  return wc.minScorePerPlayer || wc.minTotalScore || wc.minAvgScore
})

const handleSubmit = () => {
  // 校验题目数量
  if (formData.value.questionCount < 1) {
    return
  }
  
  // 🔥 校验：closest_to_target 必须填目标分
  if (formData.value.rankingMode === 'closest_to_target' && !formData.value.targetScore) {
    return
  }
  
  emit('submit', formData.value)
}

const handleCancel = () => {
  emit('cancel')
}
</script>

<template>
  <div class="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
    <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 
                w-full max-w-md shadow-2xl max-h-[90vh] overflow-y-auto">
      
      <!-- 头部 -->
      <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 sticky top-0 bg-white dark:bg-gray-800 z-10">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-white">
          游戏设置
        </h2>
      </div>

      <!-- 表单内容 -->
      <div class="px-6 py-4 space-y-5">
        
        <!-- 题目数量 -->
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            题目数量
          </label>
          <input 
            v-model.number="formData.questionCount"
            type="number"
            min="1"
            :max="maxQuestions"
            class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600
                   bg-white dark:bg-gray-700
                   text-gray-900 dark:text-white
                   focus:ring-2 focus:ring-blue-500 focus:border-transparent
                   transition-colors"
          />
          <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
            可选范围：1-{{ maxQuestions }}
          </p>
        </div>

        <!-- ========================================= -->
        <!-- 🔥 高级规则区域（可折叠） -->
        <!-- ========================================= -->
        <div class="border-t border-gray-200 dark:border-gray-700 pt-5">
          
          <!-- 折叠按钮 -->
          <button
            type="button"
            @click="showAdvanced = !showAdvanced"
            class="flex items-center justify-between w-full text-left group"
          >
            <span class="text-sm font-medium text-gray-700 dark:text-gray-300 flex items-center gap-2">
              <i class="pi pi-sliders-h text-blue-600"></i>
              高级规则
              <span v-if="formData.rankingMode !== 'standard' || hasWinConditions" 
                    class="text-xs px-2 py-0.5 bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400 rounded-full">
                已配置
              </span>
            </span>
            <i class="pi text-gray-400 transition-transform"
               :class="showAdvanced ? 'pi-chevron-up' : 'pi-chevron-down'">
            </i>
          </button>

          <!-- 高级规则内容 -->
          <div v-show="showAdvanced" class="mt-4 space-y-5 pl-1">
            
            <!-- ========================================= -->
            <!-- 1️⃣ 排名模式 -->
            <!-- ========================================= -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
                排名模式
              </label>
              <div class="space-y-2">
                
                <!-- 标准排名 -->
                <label class="flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-all"
                       :class="formData.rankingMode === 'standard'
                         ? 'bg-blue-50 border-blue-200 dark:bg-blue-900/10 dark:border-blue-800'
                         : 'bg-gray-50 border-gray-200 dark:bg-gray-700/50 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500'">
                  <input 
                    type="radio"
                    v-model="formData.rankingMode"
                    value="standard"
                    class="mt-0.5 w-4 h-4 text-blue-600 focus:ring-blue-500"
                  />
                  <div class="flex-1">
                    <span class="block text-sm font-medium text-gray-900 dark:text-white">
                      标准排名
                    </span>
                    <span class="text-xs text-gray-500 dark:text-gray-400 mt-0.5 block">
                      分数高者胜出
                    </span>
                  </div>
                </label>

                <!-- 接近平均分 -->
                <label class="flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-all"
                       :class="formData.rankingMode === 'closest_to_avg'
                         ? 'bg-blue-50 border-blue-200 dark:bg-blue-900/10 dark:border-blue-800'
                         : 'bg-gray-50 border-gray-200 dark:bg-gray-700/50 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500'">
                  <input 
                    type="radio"
                    v-model="formData.rankingMode"
                    value="closest_to_avg"
                    class="mt-0.5 w-4 h-4 text-blue-600 focus:ring-blue-500"
                  />
                  <div class="flex-1">
                    <span class="block text-sm font-medium text-gray-900 dark:text-white">
                      接近平均分
                    </span>
                    <span class="text-xs text-gray-500 dark:text-gray-400 mt-0.5 block">
                      与平均分偏差最小者胜出
                    </span>
                  </div>
                </label>

                <!-- 接近目标分 -->
                <label class="flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-all"
                       :class="formData.rankingMode === 'closest_to_target'
                         ? 'bg-blue-50 border-blue-200 dark:bg-blue-900/10 dark:border-blue-800'
                         : 'bg-gray-50 border-gray-200 dark:bg-gray-700/50 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500'">
                  <input 
                    type="radio"
                    v-model="formData.rankingMode"
                    value="closest_to_target"
                    class="mt-0.5 w-4 h-4 text-blue-600 focus:ring-blue-500"
                  />
                  <div class="flex-1">
                    <span class="block text-sm font-medium text-gray-900 dark:text-white">
                      接近目标分
                    </span>
                    <span class="text-xs text-gray-500 dark:text-gray-400 mt-0.5 block">
                      距离目标分数最近者胜出
                    </span>
                  </div>
                </label>
              </div>

              <!-- 🔥 目标分输入框（条件显示） -->
              <div v-if="formData.rankingMode === 'closest_to_target'" 
                   class="mt-3 pl-7">
                <input 
                  v-model.number="formData.targetScore"
                  type="number"
                  placeholder="输入目标分数（例如：100）"
                  class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600
                         bg-white dark:bg-gray-700 text-gray-900 dark:text-white text-sm
                         focus:ring-2 focus:ring-blue-500 focus:border-transparent
                         placeholder:text-gray-400 dark:placeholder:text-gray-500"
                />
                <p v-if="!formData.targetScore" 
                   class="mt-1.5 text-xs text-red-600 dark:text-red-400 flex items-center gap-1">
                  <i class="pi pi-exclamation-circle"></i>
                  请设置目标分数
                </p>
              </div>
            </div>

            <!-- ========================================= -->
            <!-- 2️⃣ 通关条件 -->
            <!-- ========================================= -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
                通关条件
                <span class="text-xs font-normal text-gray-500 dark:text-gray-400 ml-2">
                  （可选，不填则无限制）
                </span>
              </label>
              
              <div class="space-y-3">
                
                <!-- 所有人最低分 -->
                <div class="flex items-center gap-3">
                  <div class="flex-1">
                    <input 
                      v-model.number="formData.winConditions.minScorePerPlayer"
                      type="number"
                      placeholder="例如：80"
                      class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600
                             bg-white dark:bg-gray-700 text-gray-900 dark:text-white text-sm
                             focus:ring-2 focus:ring-blue-500 focus:border-transparent
                             placeholder:text-gray-400 dark:placeholder:text-gray-500"
                    />
                  </div>
                  <div class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400 whitespace-nowrap">
                    <i class="pi pi-users text-green-500"></i>
                    <span>所有人都 ≥</span>
                  </div>
                </div>

                <!-- 团队总分 -->
                <div class="flex items-center gap-3">
                  <div class="flex-1">
                    <input 
                      v-model.number="formData.winConditions.minTotalScore"
                      type="number"
                      placeholder="例如：500"
                      class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600
                             bg-white dark:bg-gray-700 text-gray-900 dark:text-white text-sm
                             focus:ring-2 focus:ring-blue-500 focus:border-transparent
                             placeholder:text-gray-400 dark:placeholder:text-gray-500"
                    />
                  </div>
                  <div class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400 whitespace-nowrap">
                    <i class="pi pi-flag text-purple-500"></i>
                    <span>总分 ≥</span>
                  </div>
                </div>

                <!-- 平均分 -->
                <div class="flex items-center gap-3">
                  <div class="flex-1">
                    <input 
                      v-model.number="formData.winConditions.minAvgScore"
                      type="number"
                      placeholder="例如：60"
                      class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600
                             bg-white dark:bg-gray-700 text-gray-900 dark:text-white text-sm
                             focus:ring-2 focus:ring-blue-500 focus:border-transparent
                             placeholder:text-gray-400 dark:placeholder:text-gray-500"
                    />
                  </div>
                  <div class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400 whitespace-nowrap">
                    <i class="pi pi-chart-bar text-orange-500"></i>
                    <span>平均 ≥</span>
                  </div>
                </div>
              </div>

              <!-- 提示信息 -->
              <div class="mt-3 p-3 bg-blue-50 dark:bg-blue-900/10 rounded-lg border border-blue-100 dark:border-blue-800">
                <p class="text-xs text-blue-700 dark:text-blue-400 flex items-start gap-2">
                  <i class="pi pi-info-circle mt-0.5"></i>
                  <span>留空表示无限制，填写后未达成条件将判定为挑战失败</span>
                </p>
              </div>
            </div>

          </div>
        </div>
        <!-- 高级规则区域结束 -->

      </div>

      <!-- 底部按钮 -->
      <div class="px-6 py-4 border-t border-gray-200 dark:border-gray-700 
                  flex justify-end gap-3 sticky bottom-0 bg-white dark:bg-gray-800">
        <button
          @click="handleCancel"
          class="px-4 py-2 rounded-lg text-sm font-medium
                 bg-white dark:bg-gray-700
                 text-gray-700 dark:text-gray-300
                 border border-gray-300 dark:border-gray-600
                 hover:bg-gray-50 dark:hover:bg-gray-600
                 transition-colors"
        >
          取消
        </button>
        
        <button
          @click="handleSubmit"
          :disabled="formData.rankingMode === 'closest_to_target' && !formData.targetScore"
          class="px-4 py-2 rounded-lg text-sm font-medium
                 bg-blue-600 text-white
                 hover:bg-blue-700
                 disabled:opacity-50 disabled:cursor-not-allowed
                 transition-colors"
        >
          确认
        </button>
      </div>
    </div>
  </div>
</template>