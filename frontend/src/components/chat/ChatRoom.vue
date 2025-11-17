<script setup>
import { useChatStore } from '@/stores/chat'
import { usePlayerStore } from '@/stores/player'
import { computed, nextTick, ref, watch, onUnmounted } from 'vue'

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
const playerStore = usePlayerStore()
const inputMessage = ref('')
const chatContainer = ref(null)
const showPlayerList = ref(false)  // 🔥 控制玩家列表显示

// 从chatStore获取消息
const messages = computed(() => chatStore.messages)

// 🔥 获取玩家列表（排除自己）
const otherPlayers = computed(() => {
  if (!playerStore.currentRoom || !playerStore.currentRoom.players) return []
  return playerStore.currentRoom.players.filter(p => p.playerId !== props.playerId)
})

// 🔥 选中的收件人
const selectedRecipients = computed(() => chatStore.selectedRecipients)

// 🔥 是否启用私聊功能
const privateChatEnabled = computed(() => playerStore.currentRoom?.privateChatEnabled ?? true)

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

// 发送聊天消息
const sendChatMessage = () => {
  if (!inputMessage.value.trim()) return

  chatStore.sendChatMessage(inputMessage.value)
  inputMessage.value = ''
}

// 判断是否是自己的消息
const isOwnMessage = (message) => {
  return message.senderId === props.playerId
}

// 监听消息变化，自动滚动到底部
watch(messages, () => {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}, { deep: true })

// 按 Enter 发送消息
const handleKeyPress = (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendChatMessage()
  }
}

// 🔥 双击玩家头像选择收件人
const selectPlayer = (player) => {
  chatStore.addRecipient({
    id: player.playerId,
    name: player.playerName
  })
  showPlayerList.value = false  // 选择后关闭玩家列表
}

// 🔥 移除收件人
const removeRecipient = (recipientId) => {
  chatStore.removeRecipient(recipientId)
}

// 🔥 点击外部关闭玩家列表
const closePlayerListOnClickOutside = (event) => {
  // 如果点击的不是玩家列表按钮或下拉菜单内部，关闭列表
  const playerListButton = event.target.closest('.player-list-trigger')
  const playerListMenu = event.target.closest('.player-list-menu')

  if (!playerListButton && !playerListMenu) {
    showPlayerList.value = false
  }
}

// 监听点击事件
watch(showPlayerList, (newVal) => {
  if (newVal) {
    document.addEventListener('click', closePlayerListOnClickOutside)
  } else {
    document.removeEventListener('click', closePlayerListOnClickOutside)
  }
})

// 组件卸载时清理事件
onUnmounted(() => {
  document.removeEventListener('click', closePlayerListOnClickOutside)
})

// 🔥 判断消息是否显示收件人（私聊消息）
const getRecipientNames = (message) => {
  if (!message.isPrivate || !message.recipientIds || message.recipientIds.length === 0) {
    return null
  }

  // 🔥 从 playerStore.currentRoom 获取玩家列表
  const room = playerStore.currentRoom

  // 🔥 如果无法获取房间信息，直接返回ID（降级处理）
  if (!room || !room.players || room.players.length === 0) {
    console.warn('ChatRoom: 无法获取房间玩家列表，使用ID显示', {
      hasRoom: !!room,
      hasPlayers: !!(room?.players),
      playerCount: room?.players?.length || 0,
      recipientIds: message.recipientIds
    })
    return message.recipientIds.join(', ')
  }

  // 获取收件人名字列表
  const names = message.recipientIds
    .map(id => {
      const player = room.players.find(p => p.playerId === id)
      if (!player) {
        console.warn('ChatRoom: 找不到玩家', { id, availablePlayers: room.players.map(p => ({ id: p.playerId, name: p.playerName })) })
        return id // 找不到就用ID
      }
      return player.playerName
    })
    .join(', ')

  return names || '未知收件人'
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
        <div class="flex items-center gap-2">
          <!-- 🔥 玩家列表按钮 - 只在启用私聊时显示 -->
          <div v-if="privateChatEnabled" class="relative">
            <button
              @click.stop="showPlayerList = !showPlayerList"
              class="player-list-trigger p-1.5 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
              title="选择私聊对象"
            >
              <i class="pi pi-users text-sm text-gray-600 dark:text-gray-400"></i>
            </button>

            <!-- 玩家列表下拉菜单 -->
            <transition name="fade">
              <div v-if="showPlayerList"
                   class="player-list-menu absolute top-full right-0 mt-2 w-56 bg-white dark:bg-gray-800
                          rounded-lg shadow-lg border border-gray-200 dark:border-gray-700 z-50
                          max-h-64 overflow-y-auto">
                <div class="p-2">
                  <div v-if="otherPlayers.length === 0"
                       class="text-xs text-gray-500 dark:text-gray-400 text-center py-2">
                    没有其他玩家
                  </div>
                  <button
                    v-for="player in otherPlayers"
                    :key="player.playerId"
                    @click="selectPlayer(player)"
                    class="w-full flex items-center gap-2 px-3 py-2 rounded-lg
                           hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors text-left"
                  >
                    <div class="w-6 h-6 rounded-full bg-gradient-to-br from-blue-400 to-purple-500
                                flex items-center justify-center text-white text-xs font-bold">
                      {{ player.playerName?.charAt(0)?.toUpperCase() || '?' }}
                    </div>
                    <span class="text-sm text-gray-900 dark:text-white truncate">
                      {{ player.playerName }}
                    </span>
                  </button>
                </div>
              </div>
            </transition>
          </div>

          <button
            @click="emit('close')"
            class="p-1.5 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
            title="关闭聊天"
          >
            <i class="pi pi-times text-sm text-gray-600 dark:text-gray-400"></i>
          </button>
        </div>
      </div>

      <!-- 🔥 收件人chips - 只在启用私聊时显示 -->
      <transition name="fade">
        <div v-if="privateChatEnabled && selectedRecipients.length > 0"
             class="mt-3 flex flex-wrap gap-2">
          <div v-for="recipient in selectedRecipients"
               :key="recipient.id"
               class="inline-flex items-center gap-1 px-2 py-1 bg-purple-100 dark:bg-purple-900/30
                      text-purple-700 dark:text-purple-300 rounded-md text-xs">
            <i class="pi pi-user text-xs"></i>
            <span>{{ recipient.name }}</span>
            <button
              @click="removeRecipient(recipient.id)"
              class="ml-1 hover:bg-purple-200 dark:hover:bg-purple-800/50 rounded-full p-0.5"
            >
              <i class="pi pi-times text-xs"></i>
            </button>
          </div>
          <button
            @click="chatStore.clearSelectedRecipients()"
            class="inline-flex items-center gap-1 px-2 py-1 bg-gray-100 dark:bg-gray-700
                   text-gray-600 dark:text-gray-400 rounded-md text-xs hover:bg-gray-200
                   dark:hover:bg-gray-600 transition-colors"
            title="清空收件人"
          >
            <i class="pi pi-trash text-xs"></i>
            <span>清空</span>
          </button>
        </div>
      </transition>
    </div>

    <!-- 消息列表 -->
    <div ref="chatContainer"
         class="flex-1 overflow-y-auto p-4 space-y-3 bg-gray-50 dark:bg-gray-900">
      <TransitionGroup name="message">
        <div v-for="(msg, index) in messages" :key="`msg-${index}`" class="message-item">
          <!-- 聊天消息 -->
          <div v-if="msg.type === 'CHAT'"
               :class="['flex flex-col', isOwnMessage(msg) ? 'items-end' : 'items-start']">
            <!-- 🔥 私聊标识 -->
            <div v-if="msg.isPrivate" class="text-xs text-purple-600 dark:text-purple-400 mb-1 flex items-center gap-1">
              <i class="pi pi-lock text-xs"></i>
              <span>私聊给: {{ getRecipientNames(msg) }}</span>
            </div>

            <div :class="[
              'max-w-[75%] rounded-lg px-3 py-2',
              isOwnMessage(msg)
                ? (msg.isPrivate ? 'bg-purple-600 text-white' : 'bg-blue-600 text-white')
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