import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export const usePlayerStore = defineStore('player', () => {
  const playerId = ref(localStorage.getItem('playerId') || null)
  const playerName = ref(localStorage.getItem('playerName') || null)
  const currentRoomCode = ref(null)
  const currentRoom = ref(null) // 🔥 新增：完整房间数据
  
  const isLoggedIn = computed(() => !!playerId.value)
  
  function setPlayer(id, name) {
    playerId.value = String(id)
    playerName.value = name
    localStorage.setItem('playerId', id)
    localStorage.setItem('playerName', name)
  }
  
  function clearPlayer() {
    playerId.value = null
    playerName.value = null
    currentRoomCode.value = null
    currentRoom.value = null // 🔥 新增
    localStorage.removeItem('playerId')
    localStorage.removeItem('playerName')
    localStorage.removeItem('currentRoom') // 🔥 新增
  }
  
  function setRoom(roomData) {
    currentRoomCode.value = roomData.roomCode
    currentRoom.value = roomData
    
    // 🔥 添加保存时间戳
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
  
  // 🔥 新增：恢复房间数据
  function loadRoom() {
    try {
      const saved = localStorage.getItem('currentRoom')
      if (saved) {
        const roomData = JSON.parse(saved)
        currentRoomCode.value = roomData.roomCode
        currentRoom.value = roomData
        return roomData
      }
    } catch (error) {
      console.error('恢复房间数据失败:', error)
      clearRoom()
    }
    return null
  }
  
  return {
    playerId,
    playerName,
    currentRoomCode,
    currentRoom,
    isLoggedIn,
    setPlayer,
    clearPlayer,
    setRoom,
    clearRoom,
    loadRoom
  }
})