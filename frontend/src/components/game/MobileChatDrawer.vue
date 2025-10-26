<template>
  <!-- 遮罩层 -->
  <div v-show="show"
       @click="$emit('close')"
       class="fixed inset-0 bg-black/50 z-40 lg:hidden
              transition-opacity duration-300"
       :class="show ? 'opacity-100' : 'opacity-0'"></div>

  <!-- 聊天抽屉 -->
  <div v-show="show"
       class="fixed inset-x-0 bottom-0 z-50 lg:hidden
              bg-white dark:bg-gray-800 
              border-t-2 border-gray-200 dark:border-gray-700
              rounded-t-2xl shadow-2xl
              h-[50vh] flex flex-col
              transition-transform duration-300"
       :class="show ? 'translate-y-0' : 'translate-y-full'">
    <!-- 拖动条 -->
    <div class="flex justify-center py-2 border-b border-gray-200 dark:border-gray-700">
      <div class="w-12 h-1 bg-gray-300 dark:bg-gray-600 rounded-full"></div>
    </div>
    
    <ChatRoom
      v-if="roomCode"
      :roomCode="roomCode"
      :playerId="playerId"
      :playerName="playerName"
      @newMessage="$emit('newMessage', $event)"
      class="flex-1 overflow-hidden"
    />
  </div>
</template>

<script setup>
import ChatRoom from './ChatRoom.vue'

defineProps({
  show: Boolean,
  roomCode: String,
  playerId: String,
  playerName: String
})

defineEmits(['newMessage', 'close'])
</script>