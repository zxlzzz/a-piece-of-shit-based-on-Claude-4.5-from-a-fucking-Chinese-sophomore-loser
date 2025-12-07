import { logger } from '@/utils/logger'
import { ref, onUnmounted } from 'vue'
import { isConnected, subscribeRoom, unsubscribeRoom, waitForConnection } from '@/websocket/ws'
import { getRoomStatus } from '@/api'

export function useGameWebSocket(
  roomCode,
  playerStore,
  toast,
  router,
  room,
  question,
  questionStartTime,
  timeLimit,
  resetCountdown,
  clearCountdown,
  resetSubmitState,
  restoreSubmitState,
  getSubmissionKey,
  verifySubmissionState
) {
  const subscriptions = ref([])
  const wsConnected = ref(false)

  const refreshRoomState = async () => {
    try {
      const response = await getRoomStatus(roomCode.value)
      const updatedRoom = response.data

      room.value = updatedRoom
      question.value = updatedRoom.currentQuestion
      playerStore.setRoom(updatedRoom)

      if (updatedRoom.questionStartTime) {
        questionStartTime.value = new Date(updatedRoom.questionStartTime)
        timeLimit.value = updatedRoom.timeLimit || 30
        resetCountdown()
      }

      if (updatedRoom.status === 'FINISHED' || updatedRoom.finished) {
        router.push(`/result/${roomCode.value}`)
      } else if (updatedRoom.status === 'WAITING') {
        router.push(`/wait/${roomCode.value}`)
      }
    } catch (error) {
      logger.error('刷新房间状态失败:', error)
    }
  }

  const setupRoomSubscription = () => {

    const subs = subscribeRoom(
      roomCode.value,
      (update) => {
        const oldIndex = room.value?.currentIndex
        const newIndex = update.currentIndex

        if (newIndex !== oldIndex) {
          clearCountdown()
          resetSubmitState()
        }

        room.value = update
        question.value = update.currentQuestion
        playerStore.setRoom(update)

        if (update.questionStartTime) {
          questionStartTime.value = new Date(update.questionStartTime)
          timeLimit.value = update.timeLimit || 30
          resetCountdown()
        }

        if (update.finished || update.status === 'FINISHED') {
          clearCountdown()
          router.push(`/result/${roomCode.value}`)
        }
      },
      (error) => {
        toast.add({
          severity: 'error',
          summary: '房间错误',
          detail: error.error || '房间出现错误',
          life: 3000
        })
        router.push('/find')
      }
    )

    subscriptions.value = subs
  }

  const connectWebSocket = async () => {
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

    setupRoomSubscription()
    await refreshRoomState()
  }

  onUnmounted(() => {
    unsubscribeRoom(roomCode.value)
  })

  return {
    connectWebSocket,
    refreshRoomState,
    wsConnected
  }
}