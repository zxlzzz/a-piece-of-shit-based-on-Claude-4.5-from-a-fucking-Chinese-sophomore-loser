import { logger } from '@/utils/logger'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export const usePlayerStore = defineStore('player', () => {
  
  const userId = ref(localStorage.getItem('userId') || null)
  const token = ref(localStorage.getItem('token') || null)
  const playerId = ref(localStorage.getItem('playerId') || null)
  const playerName = ref(localStorage.getItem('playerName') || null)
  const username = ref(localStorage.getItem('username') || null) // 🔥 新增用户名
  const currentRoomCode = ref(null)
  const currentRoom = ref(null)
  const isSpectator = ref(localStorage.getItem('isSpectator') === 'true') // 🔥 观战模式

  const isLoggedIn = computed(() => !!token.value && !!playerId.value)
  
  // 🔥 修改：登录时保存完整信息（包括 token 和 username）
  function setPlayer(authData) {
    token.value = authData.token
    playerId.value = authData.playerId  // UUID（用于API调用）
    userId.value = authData.id          // 🔥 新增：自增ID（用于显示）
    playerName.value = authData.name
    username.value = authData.username
    
    localStorage.setItem('token', authData.token)
    localStorage.setItem('playerId', authData.playerId)
    localStorage.setItem('userId', authData.id)  // 🔥 新增
    localStorage.setItem('playerName', authData.name)
    localStorage.setItem('username', authData.username)
  }
  
  function setSpectator(value) {
    isSpectator.value = value
    localStorage.setItem('isSpectator', value ? 'true' : 'false')
  }

  function clearPlayer() {
    token.value = null
    playerId.value = null
    playerName.value = null
    username.value = null
    currentRoomCode.value = null
    currentRoom.value = null
    isSpectator.value = false

    localStorage.removeItem('token')
    localStorage.removeItem('playerId')
    localStorage.removeItem('playerName')
    localStorage.removeItem('username')
    localStorage.removeItem('currentRoom')
    localStorage.removeItem('isSpectator')
  }
  
  function setRoom(roomData) {
    currentRoomCode.value = roomData.roomCode
    currentRoom.value = roomData
    
    const roomWithTimestamp = {
      ...roomData,
      _savedAt: Date.now()
    }
    
    localStorage.setItem('currentRoom', JSON.stringify(roomWithTimestamp))
  }
  
  function clearRoom() {
    currentRoomCode.value = null
    currentRoom.value = null
    localStorage.removeItem('currentRoom')
  }
  
  function loadRoom() {
  try {
    const saved = localStorage.getItem('currentRoom')
    if (saved) {
      const roomData = JSON.parse(saved)
      
      // 🔥 检查房间是否过期（例如2小时）
      const TWO_HOURS = 2 * 60 * 60 * 1000
      if (roomData._savedAt && (Date.now() - roomData._savedAt > TWO_HOURS)) {
        clearRoom()
        return null
      }
      
      currentRoomCode.value = roomData.roomCode
      currentRoom.value = roomData
      return roomData
    }
  } catch (error) {
    logger.error('恢复房间数据失败:', error)
    clearRoom()
  }
  return null
}
  
  return {
    token,
    playerId,
    playerName,
    username,
    currentRoomCode,
    currentRoom,
    isLoggedIn,
    userId,
    isSpectator,
    setPlayer,
    setSpectator,
    clearPlayer,
    setRoom,
    clearRoom,
    loadRoom
  }
})