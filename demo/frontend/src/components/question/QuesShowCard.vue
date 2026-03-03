<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  id: Number,  
  text: String,
  calculateRule: String,
  type: {
    type: String,
    default: 'choice'
  },
  choice: {
    type: [Array, String],
    default: () => []
  },
  people: [Number, String],
  min: Number,
  max: Number,
  step: Number,
  tags: {
    type: Array,
    default: () => []
  }
})

</script>

<template>
  <div class="break-inside-avoid mb-4 sm:mb-6 group bg-white dark:bg-gray-800 rounded-lg shadow-sm
              hover:shadow-md transition-all duration-300
              border border-gray-100 dark:border-gray-700
              p-4 sm:p-6 flex flex-col">
    
    <!-- 顶部标签区 -->
    <div class="flex items-center gap-2 mb-3 sm:mb-4">
      <!-- 人数标签 -->
      <span class="inline-flex items-center gap-1.5 sm:gap-2 px-2.5 sm:px-3 py-1 sm:py-1.5 
                   bg-blue-50 dark:bg-blue-900/30 
                   text-blue-700 dark:text-blue-300 
                   rounded-full text-xs sm:text-sm font-medium">
        <i class="pi pi-users text-xs"></i>
        {{ people }} 人
      </span>
    </div>

    <!-- 题目内容 -->
    <div class="flex-1">
      <h3 class="text-xs sm:text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">
        题目内容
      </h3>
      <p class="text-sm sm:text-base text-gray-800 dark:text-gray-200 leading-relaxed mb-3 sm:mb-4">
        {{ text }}
      </p>

      <!--  计分规则（如果有） -->
      <div v-if="calculateRule" class="mb-3 sm:mb-4">
        <div class="bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20
                    rounded-lg p-3 sm:p-4 border-l-4 border-purple-500">
          <div class="flex items-center gap-1.5 sm:gap-2 mb-1.5 sm:mb-2">
            <i class="pi pi-calculator text-purple-600 dark:text-purple-400 text-xs sm:text-sm"></i>
            <span class="font-semibold text-purple-700 dark:text-purple-300 text-xs sm:text-sm">计分规则</span>
          </div>
          <div class="text-gray-700 dark:text-gray-300 text-xs sm:text-sm leading-relaxed whitespace-pre-line">
            {{ calculateRule }}
          </div>
        </div>
      </div>

      <!--  标签显示 -->
      <div v-if="tags && tags.length > 0" class="flex flex-wrap gap-1.5 sm:gap-2 mb-4 sm:mb-6">
        <span
          v-for="tag in tags"
          :key="tag.id"
          :style="{ backgroundColor: tag.color + '20', color: tag.color, borderColor: tag.color }"
          class="inline-flex items-center px-2 sm:px-2.5 py-0.5 sm:py-1
                 rounded-full text-xs font-medium border"
        >
          {{ tag.name }}
        </span>
      </div>

      <!--  选择题：显示选项 -->
      <div v-if="type === 'CHOICE' && Array.isArray(choice) && choice.length > 0">
        <h4 class="text-xs sm:text-sm font-medium text-gray-500 dark:text-gray-400 mb-2 sm:mb-3">
          选项
        </h4>
        <ul class="space-y-2">
          <li
            v-for="(opt, i) in choice"
            :key="i"
            class="flex items-start gap-2 text-gray-700 dark:text-gray-300"
          >
            <!-- 选项标签 -->
            <span class="flex-shrink-0 w-5 h-5 sm:w-6 sm:h-6 rounded-full 
                         bg-gray-100 dark:bg-gray-700 
                         text-gray-600 dark:text-gray-400 
                         text-xs font-medium
                         flex items-center justify-center">
              {{ opt?.key || String.fromCharCode(65 + i) }}
            </span>
            
            <!-- 选项文本 -->
            <span class="flex-1 text-xs sm:text-sm">
              <span v-if="opt && typeof opt === 'object'">
                {{ opt.text }}
              </span>
              <span v-else-if="opt">
                {{ opt }}
              </span>
              <span v-else class="text-gray-400 dark:text-gray-500 italic">
                （无内容）
              </span>
            </span>
          </li>
        </ul>
      </div>

      <!--  竞价题：显示范围和步长 -->
      <div v-else-if="type === 'BID'">
        <div class="space-y-2 sm:space-y-3">
          <!-- 范围显示 -->
          <div class="flex items-center gap-2 sm:gap-3 p-2 sm:p-3 bg-gray-50 dark:bg-gray-700/30 rounded-lg">
            <div class="flex-1">
              <div class="text-xs sm:text-sm text-gray-600 dark:text-gray-400 mb-0.5 sm:mb-1">范围</div>
              <div class="text-base sm:text-lg font-semibold text-gray-800 dark:text-white">
                {{ min ?? '?' }} ~ {{ max ?? '?' }}
              </div>
            </div>
          </div>

          <!-- 步长显示 -->
          <div class="flex items-center gap-2 sm:gap-3 p-2 sm:p-3 bg-gray-50 dark:bg-gray-700/30 rounded-lg">
            <div class="flex-1">
              <div class="text-xs sm:text-sm text-gray-600 dark:text-gray-400 mb-0.5 sm:mb-1">步长</div>
              <div class="text-base sm:text-lg font-semibold text-gray-800 dark:text-white">
                {{ step ?? 1 }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 无选项提示（选择题但没有选项） -->
      <div v-else-if="type === 'CHOICE'" class="text-gray-400 dark:text-gray-500 text-xs sm:text-sm italic">
        暂无选项
      </div>
    </div>
  </div>
</template>