<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const status = ref('connected') // connected | reconnecting | disconnected

const handleReconnecting = () => {
  status.value = 'reconnecting'
}

const handleMaxReconnectFailed = () => {
  status.value = 'disconnected'
}

onMounted(() => {
  window.addEventListener('websocket-reconnecting', handleReconnecting)
  window.addEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)
})

onUnmounted(() => {
  window.removeEventListener('websocket-reconnecting', handleReconnecting)
  window.removeEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)
})
</script>

<template>
  <div v-if="status !== 'connected'" 
       class="fixed top-4 right-4 z-50 px-4 py-2 rounded-lg shadow-lg"
       :class="{
         'bg-yellow-500 text-white': status === 'reconnecting',
         'bg-red-500 text-white': status === 'disconnected'
       }">
    <div class="flex items-center gap-2">
      <i v-if="status === 'reconnecting'" class="pi pi-spin pi-spinner"></i>
      <i v-if="status === 'disconnected'" class="pi pi-exclamation-triangle"></i>
      <span>{{ status === 'reconnecting' ? '连接中断，正在重连...' : '连接失败' }}</span>
    </div>
  </div>
</template>