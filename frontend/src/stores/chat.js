import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useChatStore = defineStore('chat', () => {
  const roomCode = ref(null)
  const visible = ref(false)
  const isMobile = ref(false)

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
  }

  return {
    roomCode,
    visible,
    isMobile,
    setChatRoom,
    showChat,
    hideChat,
    toggleChat,
    clearChat
  }
})
