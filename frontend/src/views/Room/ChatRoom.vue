<script setup>
import { getStompClient, isConnected, sendMessage } from '@/websocket/ws'
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
// 注意：不再直接导入 SockJS，通过 ws.js 使用

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

const messages = ref([])
const inputMessage = ref('')
const chatContainer = ref(null)
let chatSubscription = null

// 消息类型样式映射
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

// 订阅聊天频道
const subscribeChatChannel = async () => {
  // 🔥 检查并等待连接
  if (!isConnected()) {
    console.warn('⚠️ ChatRoom: WebSocket 未连接，等待连接...')
    
    // 等待最多 3 秒
    let waited = 0
    while (!isConnected() && waited < 3000) {
      await new Promise(resolve => setTimeout(resolve, 200))
      waited += 200
    }
    
    if (!isConnected()) {
      console.error('❌ ChatRoom: 等待超时，WebSocket 仍未连接')
      return
    }
  }
  
  const client = getStompClient()
  
  // 订阅房间聊天频道
  chatSubscription = client.subscribe(`/topic/room/${props.roomCode}/chat`, (message) => {
    try {
      const chatMessage = JSON.parse(message.body)
      addMessage(chatMessage)
    } catch (error) {
      console.error('解析聊天消息失败:', error)
    }
  })

  console.log('✅ 已订阅聊天频道:', props.roomCode)

  // 发送加入消息
  sendJoinMessage()
}

// 发送加入消息
const sendJoinMessage = () => {
  const joinMsg = {
    type: 'JOIN',
    senderId: props.playerId,
    senderName: props.playerName,
    roomCode: props.roomCode
  }
  sendMessage(`/app/room/${props.roomCode}/join`, joinMsg)
}

// 发送聊天消息
const sendChatMessage = () => {
  if (!inputMessage.value.trim()) return

  const chatMsg = {
    type: 'CHAT',
    senderId: props.playerId,
    senderName: props.playerName,
    content: inputMessage.value,
    roomCode: props.roomCode
  }

  sendMessage(`/app/chat/${props.roomCode}`, chatMsg)
  inputMessage.value = ''
}

// 发送准备消息（供外部调用）
const sendReadyMessage = (isReady) => {
  const readyMsg = {
    type: isReady ? 'READY' : 'UNREADY',
    senderId: props.playerId,
    senderName: props.playerName,
    roomCode: props.roomCode
  }
  sendMessage(`/app/room/${props.roomCode}/ready`, readyMsg)
}

// 添加消息到列表
const addMessage = (message) => {
  messages.value.push(message)
  // 滚动到底部
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

// 格式化时间
const formatTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 判断是否是自己的消息
const isOwnMessage = (message) => {
  return message.senderId === props.playerId
}

// 取消订阅
const unsubscribe = () => {
  if (chatSubscription) {
    chatSubscription.unsubscribe()
    chatSubscription = null
    console.log('已取消聊天订阅')
  }
}

// 暴露方法给父组件调用
defineExpose({
  sendReadyMessage
})

onMounted(() => {
  setTimeout(() => {
    subscribeChatChannel()
  }, 500)
})

onUnmounted(() => {
  unsubscribe()
})

// 按 Enter 发送消息
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
      <h3 class="text-sm font-semibold text-gray-900 dark:text-white">
        聊天
      </h3>
    </div>

    <!-- 消息列表 -->
    <div ref="chatContainer" 
         class="flex-1 overflow-y-auto p-4 space-y-3 bg-gray-50 dark:bg-gray-900">
      <div v-for="(msg, index) in messages" :key="index">
        <!-- 聊天消息 -->
        <div v-if="msg.type === 'CHAT'" 
             :class="['flex', isOwnMessage(msg) ? 'justify-end' : 'justify-start']">
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
                 bg-blue-600 hover:bg-blue-700
                 text-white transition-colors
                 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          发送
        </button>
      </div>
    </div>
  </div>
</template>