import { defineStore } from 'pinia'
import { ref } from 'vue'
import { logger } from '@/utils/logger'
import { getStompClient, isConnected, sendMessage, registerSubscriptionCallback, unregisterSubscriptionCallback, waitForConnection } from '@/websocket/ws'
import { usePlayerStore } from './player'
import { WS_TOPIC_PRIVATE_MESSAGE, WS_TOPIC_ROOM_CHAT } from '@/config/constants'

export const useChatStore = defineStore('chat', () => {
  const roomCode = ref(null)
  const visible = ref(false)
  const isMobile = ref(false)

  const messages = ref([])
  let chatSubscription = null
  let privateSubscription = null  //  私聊订阅

  const selectedRecipients = ref([])  // 选中的收件人 [{id, name}, ...]
  const unreadPrivateCount = ref(0)   // 未读私聊消息数

  // 只在没有活跃订阅时才恢复，避免重复订阅
  const restoreChatSubscriptions = () => {
    if (roomCode.value && !chatSubscription) {
      logger.info('🔄 ChatStore: WebSocket重连，恢复聊天订阅', roomCode.value)
      subscribeToChat(roomCode.value).catch(err => {
        logger.error('ChatStore: 恢复聊天订阅失败:', err)
      })
    } else if (chatSubscription) {
      logger.debug('ChatStore: 订阅已存在，跳过重连恢复')
    }
  }

  const setChatRoom = (code) => {
    roomCode.value = code
  }

  const showChat = (mobile = false) => {
    visible.value = true
    isMobile.value = mobile
    unreadPrivateCount.value = 0
  }

  const hideChat = () => {
    visible.value = false
  }

  const toggleChat = (mobile = false) => {
    visible.value = !visible.value
    isMobile.value = mobile
    
    if (visible.value) {
      unreadPrivateCount.value = 0
    }
  }

  const clearChat = () => {
    roomCode.value = null
    visible.value = false
    messages.value = []
    selectedRecipients.value = []
    unreadPrivateCount.value = 0
  }

  const subscribeToChat = async (code) => {
    
    if (chatSubscription && roomCode.value === code) {
      logger.debug(' ChatStore: 已订阅房间', code)
      return
    }

    
    if (chatSubscription && roomCode.value !== code) {
      logger.debug('🔄 ChatStore: 切换房间，取消旧订阅')
      unsubscribeFromChat()
    }

    roomCode.value = code

    if (!isConnected()) {
      try {
        await waitForConnection()
      } catch (error) {
        logger.error(' ChatStore: 等待连接超时', error)
        throw error
      }
    }

    try {
      const client = getStompClient()
      const playerStore = usePlayerStore()

      // 使用常量确保路径一致性
      chatSubscription = client.subscribe(WS_TOPIC_ROOM_CHAT(code), (message) => {
        try {
          const chatMessage = JSON.parse(message.body)
          addMessage(chatMessage)
        } catch (error) {
          logger.error('ChatStore: 解析聊天消息失败:', error)
        }
      })

      
      privateSubscription = client.subscribe(WS_TOPIC_PRIVATE_MESSAGE, (message) => {
        try {
          const chatMessage = JSON.parse(message.body)
          addMessage(chatMessage)

          
          if (!visible.value && chatMessage.senderId !== playerStore.playerId) {
            unreadPrivateCount.value++
          }
        } catch (error) {
          logger.error('ChatStore: 解析私聊消息失败:', error)
        }
      })

      registerSubscriptionCallback(restoreChatSubscriptions)
      logger.info(' ChatStore: 已注册重连回调')
    } catch (error) {
      logger.error(' ChatStore: 订阅聊天频道失败', error)
      throw error
    }
  }

  const unsubscribeFromChat = () => {
    if (chatSubscription) {
      chatSubscription.unsubscribe()
      chatSubscription = null
      logger.info(' ChatStore: 取消订阅聊天频道')
    }
    if (privateSubscription) {
      privateSubscription.unsubscribe()
      privateSubscription = null
      logger.info(' ChatStore: 取消订阅私聊频道')
    }

    unregisterSubscriptionCallback(restoreChatSubscriptions)
    logger.info(' ChatStore: 已注销重连回调')
  }

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

    
    if (selectedRecipients.value.length > 0) {
      chatMsg.recipientIds = selectedRecipients.value.map(r => r.id)
      chatMsg.isPrivate = true
    }

    sendMessage(`/app/chat/${roomCode.value}`, chatMsg)

    // clearSelectedRecipients()
  }

  const addMessage = (message) => {
    messages.value.push(message)
  }

  const addRecipient = (recipient) => {
    
    if (!selectedRecipients.value.find(r => r.id === recipient.id)) {
      selectedRecipients.value.push(recipient)
    }
  }

  const removeRecipient = (recipientId) => {
    selectedRecipients.value = selectedRecipients.value.filter(r => r.id !== recipientId)
  }

  const clearSelectedRecipients = () => {
    selectedRecipients.value = []
  }

  const clearUnreadPrivate = () => {
    unreadPrivateCount.value = 0
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
    sendChatMessage,
    selectedRecipients,
    unreadPrivateCount,
    addRecipient,
    removeRecipient,
    clearSelectedRecipients,
    clearUnreadPrivate
  }
})
