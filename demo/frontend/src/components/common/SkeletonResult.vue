<template>
  <div class="space-y-4 sm:space-y-6">
    <!-- 游戏信息卡片 -->
    <div class="bg-white dark:bg-gray-800 rounded-lg sm:rounded-xl border border-gray-200 dark:border-gray-700 p-4 sm:p-6 animate-pulse">
      <div class="h-6 w-32 bg-gray-200 dark:bg-gray-700 rounded mb-4"></div>
      <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4">
        <div class="space-y-2">
          <div class="h-3 w-16 bg-gray-200 dark:bg-gray-700 rounded"></div>
          <div class="h-5 w-20 bg-gray-200 dark:bg-gray-700 rounded"></div>
        </div>
        <div class="space-y-2">
          <div class="h-3 w-16 bg-gray-200 dark:bg-gray-700 rounded"></div>
          <div class="h-5 w-24 bg-gray-200 dark:bg-gray-700 rounded"></div>
        </div>
        <div class="space-y-2">
          <div class="h-3 w-16 bg-gray-200 dark:bg-gray-700 rounded"></div>
          <div class="h-5 w-20 bg-gray-200 dark:bg-gray-700 rounded"></div>
        </div>
        <div class="space-y-2">
          <div class="h-3 w-16 bg-gray-200 dark:bg-gray-700 rounded"></div>
          <div class="h-5 w-28 bg-gray-200 dark:bg-gray-700 rounded"></div>
        </div>
      </div>
    </div>

    <!-- 排行榜 -->
    <div class="bg-white dark:bg-gray-800 rounded-lg sm:rounded-xl border border-gray-200 dark:border-gray-700 p-4 sm:p-6 animate-pulse">
      <div class="h-6 w-24 bg-gray-200 dark:bg-gray-700 rounded mb-4"></div>
      <div class="space-y-3">
        <div v-for="i in 3" :key="i" class="flex items-center gap-3 p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
          <div class="w-8 h-8 bg-gray-200 dark:bg-gray-700 rounded-full"></div>
          <div class="flex-1 space-y-2">
            <div class="h-4 w-24 bg-gray-200 dark:bg-gray-700 rounded"></div>
            <div class="h-3 w-16 bg-gray-200 dark:bg-gray-700 rounded"></div>
          </div>
          <div class="h-5 w-12 bg-gray-200 dark:bg-gray-700 rounded"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
</script>
