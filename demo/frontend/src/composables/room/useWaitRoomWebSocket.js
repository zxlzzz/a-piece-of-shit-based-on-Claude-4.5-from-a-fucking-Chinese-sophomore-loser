import { logger } from '@/utils/logger'
import { ref } from 'vue'
import { isConnected, subscribeRoom, unsubscribeAll, waitForConnection } from '@/websocket/ws'
import { getRoomStatus } from '@/api'

export function useWaitRoomWebSocket(roomCode, playerStore, router, toast) {
  const wsConnected = ref(false)
  const subscriptions = ref([])
  const loading = ref(false)

  const handleRoomDeleted = () => {
    toast.add({
      severity: 'warn',
      summary: '房间已解散',
      detail: '房间已被解散',
      life: 3000
    })
    setTimeout(() => {
      router.push('/find')
    }, 1000)
  }

  const setupRoomSubscription = (room, onRoomUpdate) => {
    if (subscriptions.value.length > 0) {
      unsubscribeAll(subscriptions.value)
      subscriptions.value = []
    }

    const subs = subscribeRoom(
      roomCode.value,
      (roomUpdate) => {
        onRoomUpdate(roomUpdate)

        if (roomUpdate.status === 'PLAYING') {
          router.push(`/game/${roomCode.value}`)
        }
      },
      (error) => {
        toast.add({
          severity: 'error',
          summary: '房间错误',
          detail: error.error || '房间出现错误',
          life: 3000
        })
      }
    )

    subscriptions.value = subs
  }

  const refreshRoomState = async (room) => {
    try {
      const response = await getRoomStatus(roomCode.value)
      room.value = response.data
      playerStore.setRoom(response.data)

      if (room.value.status === 'PLAYING') {
        router.push(`/game/${roomCode.value}`)
      }
    } catch (error) {
      logger.error('刷新房间状态失败:', error)
    }
  }

  const connectWebSocket = async (room, onRoomUpdate) => {
    wsConnected.value = isConnected()

    if (!wsConnected.value) {
      try {
        await waitForConnection(3000)
        wsConnected.value = true
      } catch (error) {
        toast.add({
          severity: 'error',
          summary: '连接失败',
          detail: 'WebSocket 连接失败，请刷新页面',
          life: 5000
        })
        return
      }
    }

    loading.value = true
    setupRoomSubscription(room, onRoomUpdate)
    await refreshRoomState(room)
    loading.value = false
  }

  const cleanup = () => {
    window.removeEventListener('room-deleted', handleRoomDeleted)

    if (subscriptions.value.length > 0) {
      unsubscribeAll(subscriptions.value)
      subscriptions.value = []
    }
  }

  const init = () => {
    window.addEventListener('room-deleted', handleRoomDeleted)
  }

  return {
    wsConnected,
    loading,
    connectWebSocket,
    cleanup,
    init
  }
}
