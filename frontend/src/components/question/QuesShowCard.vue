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
      <p class="text-sm sm:text-base text-gray-800 dark:text-gray-200 leading-relaxed mb-4 sm:mb-6">
        {{ text }}
      </p>

      <!-- 🔥 选择题：显示选项 -->
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

      <!-- 🔥 竞价题：显示范围和步长 -->
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

    <!-- 底部装饰线（hover 效果） -->
    <div class="mt-3 sm:mt-4 pt-3 sm:pt-4 border-t border-gray-100 dark:border-gray-700
                opacity-0 group-hover:opacity-100 transition-opacity">
      <span class="text-xs text-gray-400 dark:text-gray-500">
        <span class="hidden sm:inline">点击查看详情</span>
        <span class="sm:hidden">查看详情</span>
      </span>
    </div>
  </div>
</template>

<script setup>
defineProps({
  text: String,
  type: {
    type: String,
    default: 'choice' // choice 或 bid
  },
  choice: {
    type: [Array, String],
    default: () => []
  },
  people: [Number, String],
  min: Number,
  max: Number,
  step: Number
})
</script>