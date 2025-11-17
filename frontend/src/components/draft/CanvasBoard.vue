<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { CANVAS_LINE_WIDTHS, CANVAS_ERASER_MULTIPLIER, CANVAS_MAX_HISTORY } from '@/config/constants'

const emit = defineEmits(['update:modelValue'])

const canvas = ref(null)
const ctx = ref(null)
const isDrawing = ref(false)
const tool = ref('pen') // pen, eraser
const lineWidth = ref(CANVAS_LINE_WIDTHS[1]) // 默认中等粗细

const history = ref([])
const historyStep = ref(-1)

onMounted(() => {
  if (canvas.value) {
    // 🔥 先设置 context，再 resize（避免首次加载坐标偏移）
    ctx.value = canvas.value.getContext('2d')
    ctx.value.lineCap = 'round'
    ctx.value.lineJoin = 'round'

    // 设置canvas实际尺寸为显示尺寸
    resizeCanvas()

    // 加载保存的内容
    loadCanvas()

    // 监听窗口大小变化
    window.addEventListener('resize', resizeCanvas)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeCanvas)
})

const resizeCanvas = () => {
  if (!canvas.value) return

  // 🔥 保存当前画布内容
  const imageData = ctx.value ? canvas.value.toDataURL() : null

  const rect = canvas.value.getBoundingClientRect()
  const dpr = window.devicePixelRatio || 1

  // 设置canvas实际尺寸（考虑设备像素比）
  canvas.value.width = rect.width * dpr
  canvas.value.height = rect.height * dpr

  // 缩放绘图上下文以匹配设备像素比
  if (ctx.value) {
    ctx.value.scale(dpr, dpr)
    ctx.value.lineCap = 'round'
    ctx.value.lineJoin = 'round'

    // 🔥 恢复保存的内容（使用逻辑尺寸，避免缩放问题）
    if (imageData) {
      const img = new Image()
      img.onload = () => {
        ctx.value.drawImage(img, 0, 0, rect.width, rect.height)
      }
      img.src = imageData
    }
  }
}

const loadCanvas = () => {
  const saved = sessionStorage.getItem('draftCanvas')
  if (saved && ctx.value) {
    const img = new Image()
    img.onload = () => {
      ctx.value.drawImage(img, 0, 0)
      saveState()
    }
    img.src = saved
  } else {
    saveState()
  }
}

const saveCanvas = () => {
  if (canvas.value) {
    const dataURL = canvas.value.toDataURL()
    sessionStorage.setItem('draftCanvas', dataURL)
  }
}

const saveState = () => {
  if (!canvas.value) return

  historyStep.value++
  history.value = history.value.slice(0, historyStep.value)
  history.value.push(canvas.value.toDataURL())

  if (history.value.length > CANVAS_MAX_HISTORY) {
    history.value.shift()
    historyStep.value--
  }
}

const undo = () => {
  if (historyStep.value > 0) {
    historyStep.value--
    restoreState(history.value[historyStep.value])
  }
}

const redo = () => {
  if (historyStep.value < history.value.length - 1) {
    historyStep.value++
    restoreState(history.value[historyStep.value])
  }
}

const restoreState = (dataURL) => {
  const img = new Image()
  img.onload = () => {
    ctx.value.clearRect(0, 0, canvas.value.width, canvas.value.height)
    ctx.value.drawImage(img, 0, 0)
    saveCanvas()
  }
  img.src = dataURL
}

const clear = () => {
  if (ctx.value && canvas.value) {
    ctx.value.clearRect(0, 0, canvas.value.width, canvas.value.height)
    saveState()
    saveCanvas()
  }
}

const startDrawing = (e) => {
  isDrawing.value = true
  const rect = canvas.value.getBoundingClientRect()
  const x = (e.clientX || e.touches[0].clientX) - rect.left
  const y = (e.clientY || e.touches[0].clientY) - rect.top

  ctx.value.beginPath()
  ctx.value.moveTo(x, y)
}

const draw = (e) => {
  if (!isDrawing.value) return
  e.preventDefault()

  const rect = canvas.value.getBoundingClientRect()
  const x = (e.clientX || e.touches[0].clientX) - rect.left
  const y = (e.clientY || e.touches[0].clientY) - rect.top

  if (tool.value === 'eraser') {
    ctx.value.globalCompositeOperation = 'destination-out'
    ctx.value.lineWidth = lineWidth.value * CANVAS_ERASER_MULTIPLIER
  } else {
    ctx.value.globalCompositeOperation = 'source-over'
    ctx.value.lineWidth = lineWidth.value
  }

  ctx.value.strokeStyle = '#000000'
  ctx.value.lineTo(x, y)
  ctx.value.stroke()
}

const stopDrawing = () => {
  if (isDrawing.value) {
    isDrawing.value = false
    saveState()
    saveCanvas()
  }
}

defineExpose({ clear, undo, redo })
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- 工具栏 -->
    <div class="flex items-center gap-2 p-3 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
      <!-- 工具选择 -->
      <div class="flex gap-1">
        <button
          @click="tool = 'pen'"
          :class="tool === 'pen' ? 'bg-blue-500 text-white' : 'bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300'"
          class="p-2 rounded-lg transition-colors hover:bg-blue-100 dark:hover:bg-gray-600"
          title="画笔"
        >
          <i class="pi pi-pencil"></i>
        </button>
        <button
          @click="tool = 'eraser'"
          :class="tool === 'eraser' ? 'bg-blue-500 text-white' : 'bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300'"
          class="p-2 rounded-lg transition-colors hover:bg-blue-100 dark:hover:bg-gray-600"
          title="橡皮擦"
        >
          <i class="pi pi-eraser"></i>
        </button>
      </div>

      <div class="h-6 w-px bg-gray-300 dark:bg-gray-600"></div>

      <!-- 粗细选择 -->
      <div class="flex gap-1">
        <button
          v-for="width in CANVAS_LINE_WIDTHS"
          :key="width"
          @click="lineWidth = width"
          :class="lineWidth === width ? 'bg-blue-500' : 'bg-white dark:bg-gray-700'"
          class="w-8 h-8 rounded-lg transition-colors hover:bg-blue-100 dark:hover:bg-gray-600 flex items-center justify-center"
          :title="`粗细 ${width}`"
        >
          <div
            :class="lineWidth === width ? 'bg-white' : 'bg-gray-700 dark:bg-gray-300'"
            :style="{ width: width * 2 + 'px', height: width * 2 + 'px' }"
            class="rounded-full"
          ></div>
        </button>
      </div>

      <div class="h-6 w-px bg-gray-300 dark:bg-gray-600"></div>

      <!-- 撤销重做 -->
      <button
        @click="undo"
        :disabled="historyStep <= 0"
        class="p-2 rounded-lg transition-colors disabled:opacity-30 disabled:cursor-not-allowed
               bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300
               hover:bg-blue-100 dark:hover:bg-gray-600"
        title="撤销"
      >
        <i class="pi pi-undo"></i>
      </button>
      <button
        @click="redo"
        :disabled="historyStep >= history.length - 1"
        class="p-2 rounded-lg transition-colors disabled:opacity-30 disabled:cursor-not-allowed
               bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300
               hover:bg-blue-100 dark:hover:bg-gray-600"
        title="重做"
      >
        <i class="pi pi-redo"></i>
      </button>

      <div class="flex-1"></div>

      <!-- 清空 -->
      <button
        @click="clear"
        class="p-2 rounded-lg transition-colors
               bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400
               hover:bg-red-100 dark:hover:bg-red-900/40"
        title="清空画布"
      >
        <i class="pi pi-trash"></i>
      </button>
    </div>

    <!-- 画布 -->
    <div class="flex-1 bg-white dark:bg-gray-900 overflow-hidden">
      <canvas
        ref="canvas"
        width="800"
        height="600"
        class="w-full h-full cursor-crosshair touch-none"
        @mousedown="startDrawing"
        @mousemove="draw"
        @mouseup="stopDrawing"
        @mouseleave="stopDrawing"
        @touchstart="startDrawing"
        @touchmove="draw"
        @touchend="stopDrawing"
      ></canvas>
    </div>
  </div>
</template>

<style scoped>
canvas {
  touch-action: none;
}
</style>
