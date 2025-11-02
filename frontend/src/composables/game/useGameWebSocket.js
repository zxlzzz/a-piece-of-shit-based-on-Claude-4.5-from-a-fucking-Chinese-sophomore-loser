import { logger } from '@/utils/logger'
import { ref, onMounted, onUnmounted } from 'vue'
import { connect, isConnected, subscribeRoom, unsubscribeAll } from '@/websocket/ws'
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
  getSubmissionKey
) {
  const subscriptions = ref([])
  const wsConnected = ref(false) // 🔥 新增：连接状态

  const handleReconnecting = (event) => {
    wsConnected.value = false // 🔥 更新连接状态
    toast.add({
      severity: 'warn',
      summary: '连接中断',
      detail: `正在尝试重连... (${event.detail.attempts}/5)`,
      life: 3000
    })
  }

  const handleMaxReconnectFailed = () => {
    logger.error('❌ GameView: WebSocket 重连失败')
    
    toast.add({
      severity: 'error',
      summary: '连接失败',
      detail: '连接已断开，请刷新页面',
      life: 0
    })
    
    clearCountdown()
    
    setTimeout(() => {
      if (confirm('连接已断开，是否重新连接？')) {
        window.location.reload()
      } else {
        router.push('/find')
      }
    }, 2000)
  }

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
        toast.add({
          severity: 'info',
          summary: '游戏已结束',
          detail: '正在跳转到结果页面...',
          life: 2000
        })
        setTimeout(() => {
          router.push(`/result/${roomCode.value}`)
        }, 1000)
      } else if (updatedRoom.status === 'WAITING') {
        toast.add({
          severity: 'info',
          summary: '游戏未开始',
          detail: '正在返回等待房间...',
          life: 2000
        })
        setTimeout(() => {
          router.push(`/wait/${roomCode.value}`)
        }, 1000)
      }
      
    } catch (error) {
      logger.error('❌ GameView: 刷新房间状态失败:', error)
    }
  }

  const setupRoomSubscription = () => {
    const subs = subscribeRoom(
      roomCode.value,
      (update) => {
        
        const oldIndex = room.value?.currentIndex
        const newIndex = update.currentIndex
        
        room.value = update
        
        if (newIndex !== undefined && oldIndex !== newIndex) {
          if (oldIndex !== undefined) {
            const oldSubmissionKey = `submission_${roomCode.value}_${oldIndex}`
            localStorage.removeItem(oldSubmissionKey)
          }
          
          clearCountdown()
          
          resetSubmitState()
          question.value = update.currentQuestion
          
          const newSubmissionKey = `submission_${roomCode.value}_${newIndex}`
          const savedSubmission = localStorage.getItem(newSubmissionKey)
          if (savedSubmission === 'true') {
            restoreSubmitState()
          } else {
          }
          
          if (update.questionStartTime) {
            questionStartTime.value = new Date(update.questionStartTime)
            timeLimit.value = update.timeLimit || 30
            resetCountdown()
          }
        } else {
          question.value = update.currentQuestion
        }
        
        playerStore.setRoom(update)

        const isGameFinished = update.finished === true || update.status === 'FINISHED'

        if (isGameFinished) {
          clearCountdown()
          toast.add({
            severity: 'info',
            summary: '游戏结束',
            detail: '正在跳转到结果页面...',
            life: 2000
          })
          setTimeout(() => {
            router.push(`/result/${roomCode.value}`)
          }, 1000)
        }
      },
      (error) => {
        logger.error('🔥 房间错误:', error)
        
        if (error.error?.includes('房间不存在') || error.error?.includes('不存在')) {
          toast.add({
            severity: 'warn',
            summary: '房间已关闭',
            detail: '房间已被删除或游戏已结束',
            life: 3000
          })
          
          playerStore.clearRoom()
          
          setTimeout(() => {
            router.push('/find')
          }, 3000)
        } else {
          toast.add({
            severity: 'error',
            summary: '房间错误',
            detail: error.error || '房间出现错误',
            life: 3000
          })
        }
      }
    )
    
    subscriptions.value = subs
  }

  const connectWebSocket = async () => {
    if (!isConnected()) {

      try {
        await connect(playerStore.playerId)
        wsConnected.value = true // 🔥 连接成功，更新状态
      } catch (err) {
        logger.error('❌ GameView: WebSocket 连接失败', err)
        wsConnected.value = false // 🔥 连接失败，更新状态
        toast.add({
          severity: 'error',
          summary: '连接失败',
          detail: 'WebSocket 连接失败，请刷新页面',
          life: 5000
        })
        return
      }
    } else {
      wsConnected.value = true // 🔥 已连接，更新状态
    }

    setupRoomSubscription()
    await refreshRoomState()
  }

  onMounted(() => {
    window.addEventListener('websocket-reconnecting', handleReconnecting)
    window.addEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)
  })

  onUnmounted(() => {
    if (subscriptions.value.length > 0) {
      unsubscribeAll(subscriptions.value)
    }
    window.removeEventListener('websocket-reconnecting', handleReconnecting)
    window.removeEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)
  })

  return {
    connectWebSocket,
    refreshRoomState,
    wsConnected // 🔥 新增：返回连接状态
  }
}