import { logger } from '@/utils/logger'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export const usePlayerStore = defineStore('player', () => {
  
  const userId = ref(localStorage.getItem('userId') || null)
  const token = ref(localStorage.getItem('token') || null)
  const playerId = ref(localStorage.getItem('playerId') || null)
  const playerName = ref(localStorage.getItem('playerName') || null)
  const username = ref(localStorage.getItem('username') || null) //  新增用户名
  const currentRoomCode = ref(null)
  const currentRoom = ref(null)

  const isLoggedIn = computed(() => !!token.value && !!playerId.value)

  // 登录时保存完整信息（包括 token 和 username）
  function setPlayer(authData) {
    token.value = authData.token
    playerId.value = authData.playerId  // UUID（用于API调用）
    userId.value = authData.id          
    playerName.value = authData.name
    username.value = authData.username
    
    localStorage.setItem('token', authData.token)
    localStorage.setItem('playerId', authData.playerId)
    localStorage.setItem('userId', authData.id)  //  新增
    localStorage.setItem('playerName', authData.name)
    localStorage.setItem('username', authData.username)
  }

  function clearPlayer() {
    token.value = null
    playerId.value = null
    playerName.value = null
    username.value = null
    currentRoomCode.value = null
    currentRoom.value = null

    localStorage.removeItem('token')
    localStorage.removeItem('playerId')
    localStorage.removeItem('playerName')
    localStorage.removeItem('username')
    localStorage.removeItem('currentRoom')
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

      
      if (roomData._savedAt && (Date.now() - roomData._savedAt > 10 * 60 * 1000)) {
        logger.info('🧹 房间缓存已过期，自动清理')
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
    setPlayer,
    clearPlayer,
    setRoom,
    clearRoom,
    loadRoom
  }
})