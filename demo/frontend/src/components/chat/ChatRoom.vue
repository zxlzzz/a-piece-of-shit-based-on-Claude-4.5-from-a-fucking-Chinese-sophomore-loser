<script setup>
import { useChatStore } from '@/stores/chat'
import { computed, nextTick, ref, watch } from 'vue'

const props = defineProps({
  roomCode: {
    type: String,
    required: true
  },
  playerId: {
    type: String,
    required: true
  },
  playerName: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['close'])

const chatStore = useChatStore()
const inputMessage = ref('')
const chatContainer = ref(null)

const messages = computed(() => chatStore.messages)

const messageTypeClass = computed(() => ({
  CHAT: 'chat-message',
  SYSTEM: 'system-message',
  JOIN: 'join-message',
  LEAVE: 'leave-message',
  READY: 'ready-message',
  UNREADY: 'unready-message',
  GAME_START: 'game-start-message',
  GAME_END: 'game-end-message'
}))

const sendChatMessage = () => {
  if (!inputMessage.value.trim()) return

  chatStore.sendChatMessage(inputMessage.value)
  inputMessage.value = ''
}

const isOwnMessage = (message) => {
  return message.senderId === props.playerId
}

watch(messages, () => {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}, { deep: true })

const handleKeyPress = (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendChatMessage()
  }
}
</script>
<template>
  <div class="flex flex-col h-full bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">

    <!-- 标题 -->
    <div class="px-5 py-3 border-b border-gray-200 dark:border-gray-700">
      <div class="flex items-center justify-between">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-white">
          聊天
        </h3>
        <button
          @click="emit('close')"
          class="p-1.5 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
          title="关闭聊天"
        >
          <i class="pi pi-times text-sm text-gray-600 dark:text-gray-400"></i>
        </button>
      </div>
    </div>

    <!-- 消息列表 -->
    <div ref="chatContainer"
         class="flex-1 overflow-y-auto p-4 space-y-3 bg-gray-50 dark:bg-gray-900">
      <TransitionGroup name="message">
        <div v-for="(msg, index) in messages" :key="`msg-${index}`" class="message-item">
          <!-- 聊天消息 -->
          <div v-if="msg.type === 'CHAT'"
               :class="['flex flex-col', isOwnMessage(msg) ? 'items-end' : 'items-start']">
            <div :class="[
              'max-w-[75%] rounded-lg px-3 py-2',
              isOwnMessage(msg)
                ? 'bg-blue-600 text-white'
                : 'bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 border border-gray-200 dark:border-gray-700'
            ]">
              <p class="text-xs font-medium mb-0.5"
                 :class="isOwnMessage(msg) ? 'text-white/80' : 'text-gray-500 dark:text-gray-400'">
                {{ msg.senderName }}
              </p>
              <p class="text-sm">{{ msg.content }}</p>
            </div>
          </div>

          <!-- 系统消息 -->
          <div v-else class="flex justify-center">
            <div class="px-3 py-1 rounded-md text-xs text-gray-600 dark:text-gray-400 bg-gray-100 dark:bg-gray-800">
              {{ msg.content }}
            </div>
          </div>
        </div>
      </TransitionGroup>
    </div>

    <!-- 输入框 -->
    <div class="p-3 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
      <div class="flex gap-2">
        <input
          v-model="inputMessage"
          type="text"
          placeholder="输入消息"
          @keypress="handleKeyPress"
          class="chat-input flex-1 px-3 py-2 text-sm rounded-lg
                 bg-gray-100 dark:bg-gray-700
                 border border-transparent
                 text-gray-900 dark:text-gray-100
                 placeholder-gray-500 dark:placeholder-gray-400
                 focus:border-blue-500 focus:outline-none"
        />
        <button
          @click="sendChatMessage"
          :disabled="!inputMessage.trim()"
          class="px-4 py-2 rounded-lg text-sm font-medium
                 bg-blue-600 hover:bg-blue-700 active:scale-95
                 text-white transition-all
                 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          发送
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 消息弹入动画 */
.message-item {
  margin-bottom: 0.75rem;
}

.message-enter-active {
  animation: message-slide-in 0.3s ease-out;
}

@keyframes message-slide-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 关闭按钮hover效果 */
.pi-times {
  transition: transform 0.2s ease;
}

.pi-times:hover {
  transform: rotate(90deg);
}
</style>