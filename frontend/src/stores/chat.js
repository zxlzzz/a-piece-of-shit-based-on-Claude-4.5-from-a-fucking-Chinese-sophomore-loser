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

  // 🔥 聊天消息和订阅
  const messages = ref([])
  let chatSubscription = null
  let privateSubscription = null  // 🔥 私聊订阅

  // 🔥 私聊相关状态
  const selectedRecipients = ref([])  // 选中的收件人 [{id, name}, ...]
  const unreadPrivateCount = ref(0)   // 未读私聊消息数

  // 🔥 重连恢复回调（用于 WebSocket 断线重连后自动恢复订阅）
  // 🔥 修复：只在没有活跃订阅时才恢复，避免重复订阅
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

    // 🔥 优化：使用事件驱动等待连接，避免轮询
    if (!isConnected()) {
      try {
        await waitForConnection()
      } catch (error) {
        logger.error('❌ ChatStore: 等待连接超时', error)
        throw error
      }
    }

    try {
      const client = getStompClient()
      const playerStore = usePlayerStore()

      // 🔥 修复P1-5：使用常量确保路径一致性
      // 订阅房间聊天频道
      chatSubscription = client.subscribe(WS_TOPIC_ROOM_CHAT(code), (message) => {
        try {
          const chatMessage = JSON.parse(message.body)
          addMessage(chatMessage)
        } catch (error) {
          logger.error('ChatStore: 解析聊天消息失败:', error)
        }
      })

      // 🔥 订阅私聊频道 - 使用常量确保与后端路径一致
      // 验证playerId不包含特殊字符（防止路径注入）
      if (playerStore.playerId && /[^a-zA-Z0-9\-_]/.test(playerStore.playerId)) {
        logger.warn('⚠️ playerId包含特殊字符，可能影响路径匹配:', playerStore.playerId)
      }

      privateSubscription = client.subscribe(WS_TOPIC_PRIVATE_MESSAGE(playerStore.playerId), (message) => {
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

      // 🔥 注册重连回调，确保断线重连后能恢复聊天订阅
      registerSubscriptionCallback(restoreChatSubscriptions)
      logger.info('✅ ChatStore: 已注册重连回调')

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

    // 🔥 注销重连回调，避免内存泄漏
    unregisterSubscriptionCallback(restoreChatSubscriptions)
    logger.info('✅ ChatStore: 已注销重连回调')
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
