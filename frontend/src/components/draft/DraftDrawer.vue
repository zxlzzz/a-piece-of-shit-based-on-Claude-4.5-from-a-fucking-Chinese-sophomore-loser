<script setup>
import { ref, watch, computed } from 'vue'
import CanvasBoard from './CanvasBoard.vue'
import { DRAWER_HEIGHT_VH, DRAWER_MAX_HEIGHT } from '@/config/constants'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible'])

const canvasRef = ref(null)

const drawerStyle = computed(() => ({
  height: `${DRAWER_HEIGHT_VH}vh`,
  maxHeight: `${DRAWER_MAX_HEIGHT}px`
}))

const close = () => {
  emit('update:visible', false)
}

const handleClear = () => {
  if (canvasRef.value) {
    canvasRef.value.clear()
  }
}
</script>

<template>
  <!-- 遮罩层 -->
  <transition name="mask">
    <div
      v-if="visible"
      class="fixed inset-0 bg-black/20 z-[100] backdrop-blur-sm"
      @click="close"
    ></div>
  </transition>

  <!-- 抽屉 -->
  <transition name="drawer">
    <div
      v-if="visible"
      class="fixed bottom-0 left-0 right-0 z-[101] bg-white dark:bg-gray-800 rounded-t-2xl shadow-2xl"
      :style="drawerStyle"
      @click.stop
    >
      <div class="flex flex-col h-full">
        <!-- 顶部栏 -->
        <div class="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700">
          <div class="flex items-center gap-2">
            <i class="pi pi-pencil text-blue-500"></i>
            <h3 class="font-semibold text-gray-900 dark:text-white">草稿本</h3>
          </div>
          <div class="flex items-center gap-2">
            <button
              @click="handleClear"
              class="p-2 rounded-lg text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
              title="清空"
            >
              <i class="pi pi-trash text-sm"></i>
            </button>
            <button
              @click="close"
              class="p-2 rounded-lg text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
              title="关闭"
            >
              <i class="pi pi-times text-sm"></i>
            </button>
          </div>
        </div>

        <!-- 画板区域 -->
        <div class="flex-1 overflow-hidden">
          <CanvasBoard ref="canvasRef" />
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.mask-enter-active, .mask-leave-active {
  transition: opacity 0.3s ease;
}
.mask-enter-from, .mask-leave-to {
  opacity: 0;
}

.drawer-enter-active, .drawer-leave-active {
  transition: transform 0.3s ease;
}
.drawer-enter-from, .drawer-leave-to {
  transform: translateY(100%);
}
</style>
