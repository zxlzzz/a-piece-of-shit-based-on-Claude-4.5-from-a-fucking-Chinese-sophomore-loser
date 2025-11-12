<script setup>
import { ref, onMounted } from 'vue'
import DraftDrawer from './DraftDrawer.vue'
import { FLOATING_BUTTON_SIZE, FLOATING_BUTTON_DEFAULT_OFFSET } from '@/config/constants'

const props = defineProps({
  enabled: {
    type: Boolean,
    default: true
  }
})

const position = ref({ x: 0, y: 0 })
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0 })
const drawerVisible = ref(false)

onMounted(() => {
  // 初始位置：右下角
  const savedPos = localStorage.getItem('draftButtonPos')
  if (savedPos) {
    position.value = JSON.parse(savedPos)
  } else {
    position.value = {
      x: window.innerWidth - FLOATING_BUTTON_DEFAULT_OFFSET,
      y: window.innerHeight - FLOATING_BUTTON_DEFAULT_OFFSET
    }
  }
})

const startDrag = (e) => {
  isDragging.value = true
  const clientX = e.clientX || e.touches[0].clientX
  const clientY = e.clientY || e.touches[0].clientY

  dragStart.value = {
    x: clientX - position.value.x,
    y: clientY - position.value.y
  }

  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
  document.addEventListener('touchmove', onDrag)
  document.addEventListener('touchend', stopDrag)
}

const onDrag = (e) => {
  if (!isDragging.value) return

  e.preventDefault()
  const clientX = e.clientX || e.touches[0].clientX
  const clientY = e.clientY || e.touches[0].clientY

  position.value = {
    x: Math.max(0, Math.min(window.innerWidth - FLOATING_BUTTON_SIZE, clientX - dragStart.value.x)),
    y: Math.max(0, Math.min(window.innerHeight - FLOATING_BUTTON_SIZE, clientY - dragStart.value.y))
  }
}

const stopDrag = () => {
  if (isDragging.value) {
    isDragging.value = false
    localStorage.setItem('draftButtonPos', JSON.stringify(position.value))

    document.removeEventListener('mousemove', onDrag)
    document.removeEventListener('mouseup', stopDrag)
    document.removeEventListener('touchmove', onDrag)
    document.removeEventListener('touchend', stopDrag)
  }
}

const handleClick = () => {
  if (!isDragging.value) {
    drawerVisible.value = true
  }
}
</script>

<template>
  <div v-if="enabled">
    <!-- 悬浮球 -->
    <button
      class="fixed z-50 w-14 h-14 rounded-full shadow-lg
             bg-blue-500 hover:bg-blue-600 active:bg-blue-700
             text-white transition-all duration-200
             flex items-center justify-center
             touch-none select-none"
      :style="{
        left: position.x + 'px',
        top: position.y + 'px',
        cursor: isDragging ? 'grabbing' : 'grab'
      }"
      @mousedown="startDrag"
      @touchstart="startDrag"
      @click="handleClick"
    >
      <i class="pi pi-pencil text-xl"></i>
    </button>

    <!-- 抽屉 -->
    <DraftDrawer v-model:visible="drawerVisible" />
  </div>
</template>

<style scoped>
button {
  -webkit-tap-highlight-color: transparent;
}
</style>
