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
  let privateSubscription = null  // 🔥 私聊订阅

  // 🔥 私聊相关状态
  const selectedRecipients = ref([])  // 选中的收件人 [{id, name}, ...]
  const unreadPrivateCount = ref(0)   // 未读私聊消息数

  // 设置当前聊天室
  const setChatRoom = (code) => {
    roomCode.value = code
  }

  // 显示聊天室
  const showChat = (mobile = false) => {
    visible.value = true
    isMobile.value = mobile
    // 🔥 打开聊天室时清空未读计数
    unreadPrivateCount.value = 0
  }

  // 隐藏聊天室
  const hideChat = () => {
    visible.value = false
  }

  // 切换聊天室显示状态
  const toggleChat = (mobile = false) => {
    visible.value = !visible.value
    isMobile.value = mobile
    // 🔥 如果打开了聊天室，清空未读计数
    if (visible.value) {
      unreadPrivateCount.value = 0
    }
  }

  // 清除聊天室（离开房间时调用）
  const clearChat = () => {
    roomCode.value = null
    visible.value = false
    messages.value = []
    selectedRecipients.value = []
    unreadPrivateCount.value = 0
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

    // 🔥 增加等待时间至10秒，每200ms检查一次
    if (!isConnected()) {
      logger.warn('⚠️ ChatStore: WebSocket未连接，等待连接...')
      let waited = 0
      const maxWait = 10000 // 10秒
      while (!isConnected() && waited < maxWait) {
        await new Promise(resolve => setTimeout(resolve, 200))
        waited += 200
      }

      if (!isConnected()) {
        const error = new Error('WebSocket连接超时（10秒）')
        logger.error('❌ ChatStore: 等待超时（10秒），WebSocket仍未连接')
        throw error
      }

      logger.info(`✅ ChatStore: WebSocket连接成功（等待${waited}ms）`)
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

      // 🔥 订阅私聊频道 - 使用基于playerId的topic路径
      const playerStore = usePlayerStore()
      const privateChannelPath = `/topic/player/${playerStore.playerId}/private`

      privateSubscription = client.subscribe(privateChannelPath, (message) => {
        try {
          const chatMessage = JSON.parse(message.body)
          addMessage(chatMessage)

          // 🔥 如果聊天室未打开且不是自己发的消息，增加未读计数
          if (!visible.value && chatMessage.senderId !== playerStore.playerId) {
            unreadPrivateCount.value++
          }
        } catch (error) {
          logger.error('ChatStore: 解析私聊消息失败:', error)
        }
      })

      // 发送加入消息
      sendJoinMessage()
    } catch (error) {
      logger.error('❌ ChatStore: 订阅聊天频道失败', error)
      throw error
    }
  }

  // 🔥 取消订阅聊天频道
  const unsubscribeFromChat = () => {
    if (chatSubscription) {
      chatSubscription.unsubscribe()
      chatSubscription = null
      logger.info('✅ ChatStore: 取消订阅聊天频道')
    }
    if (privateSubscription) {
      privateSubscription.unsubscribe()
      privateSubscription = null
      logger.info('✅ ChatStore: 取消订阅私聊频道')
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

    // 🔥 如果有选中的收件人，发送私聊
    if (selectedRecipients.value.length > 0) {
      chatMsg.recipientIds = selectedRecipients.value.map(r => r.id)
      chatMsg.isPrivate = true
    }

    sendMessage(`/app/chat/${roomCode.value}`, chatMsg)

    // 🔥 发送后清空收件人（可选：也可以保留选择）
    // clearSelectedRecipients()
  }

  // 🔥 添加消息到列表
  const addMessage = (message) => {
    messages.value.push(message)
  }

  // 🔥 私聊收件人管理
  const addRecipient = (recipient) => {
    // 避免重复添加
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

  // 🔥 清空未读计数
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
    // 🔥 私聊相关
    selectedRecipients,
    unreadPrivateCount,
    addRecipient,
    removeRecipient,
    clearSelectedRecipients,
    clearUnreadPrivate
  }
})
