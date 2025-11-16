import { defineStore } from 'pinia'
import { ref } from 'vue'
import { logger } from '@/utils/logger'
import { getStompClient, isConnected, sendMessage } from '@/websocket/ws'
import { usePlayerStore } from './player'

export const useChatStore = defineStore('chat', () => {
  const roomCode = ref(null)
  const visible = ref(false)
  const isMobile = ref(false)

  // 🔥 聊天消息和订阅
  const messages = ref([])
  let chatSubscription = null

  // 设置当前聊天室
  const setChatRoom = (code) => {
    roomCode.value = code
  }

  // 显示聊天室
  const showChat = (mobile = false) => {
    visible.value = true
    isMobile.value = mobile
  }

  // 隐藏聊天室
  const hideChat = () => {
    visible.value = false
  }

  // 切换聊天室显示状态
  const toggleChat = (mobile = false) => {
    visible.value = !visible.value
    isMobile.value = mobile
  }

  // 清除聊天室（离开房间时调用）
  const clearChat = () => {
    roomCode.value = null
    visible.value = false
    messages.value = []
  }

  // 🔥 订阅聊天频道
  const subscribeToChat = async (code) => {
    // 如果已经订阅了相同的房间，不重复订阅
    if (chatSubscription && roomCode.value === code) {
      logger.debug('✅ ChatStore: 已订阅房间', code)
      return
    }

    // 如果订阅了不同的房间，先取消旧订阅
    if (chatSubscription && roomCode.value !== code) {
      logger.debug('🔄 ChatStore: 切换房间，取消旧订阅')
      unsubscribeFromChat()
    }

    // 设置房间码
    roomCode.value = code

    // 🔥 检查并等待WebSocket连接
    if (!isConnected()) {
      logger.warn('⚠️ ChatStore: WebSocket未连接，等待连接...')
      let waited = 0
      while (!isConnected() && waited < 3000) {
        await new Promise(resolve => setTimeout(resolve, 200))
        waited += 200
      }

      if (!isConnected()) {
        logger.error('❌ ChatStore: 等待超时，WebSocket仍未连接')
        return
      }
    }

    try {
      const client = getStompClient()

      // 订阅房间聊天频道
      chatSubscription = client.subscribe(`/topic/room/${code}/chat`, (message) => {
        try {
          const chatMessage = JSON.parse(message.body)
          addMessage(chatMessage)
        } catch (error) {
          logger.error('ChatStore: 解析聊天消息失败:', error)
        }
      })

      logger.info('✅ ChatStore: 订阅聊天频道成功', code)

      // 发送加入消息
      sendJoinMessage()
    } catch (error) {
      logger.error('❌ ChatStore: 订阅聊天频道失败', error)
    }
  }

  // 🔥 取消订阅聊天频道
  const unsubscribeFromChat = () => {
    if (chatSubscription) {
      chatSubscription.unsubscribe()
      chatSubscription = null
      logger.info('✅ ChatStore: 取消订阅聊天频道')
    }
  }

  // 🔥 发送加入消息
  const sendJoinMessage = () => {
    const playerStore = usePlayerStore()
    const joinMsg = {
      type: 'JOIN',
      senderId: playerStore.playerId,
      senderName: playerStore.playerName,
      roomCode: roomCode.value
    }
    sendMessage(`/app/room/${roomCode.value}/join`, joinMsg)
  }

  // 🔥 发送聊天消息
  const sendChatMessage = (content) => {
    if (!content || !content.trim()) return

    const playerStore = usePlayerStore()
    const chatMsg = {
      type: 'CHAT',
      senderId: playerStore.playerId,
      senderName: playerStore.playerName,
      content: content.trim(),
      roomCode: roomCode.value
    }

    sendMessage(`/app/chat/${roomCode.value}`, chatMsg)
  }

  // 🔥 添加消息到列表
  const addMessage = (message) => {
    messages.value.push(message)
  }

  return {
    roomCode,
    visible,
    isMobile,
    messages,
    setChatRoom,
    showChat,
    hideChat,
    toggleChat,
    clearChat,
    subscribeToChat,
    unsubscribeFromChat,
    sendChatMessage
  }
})
