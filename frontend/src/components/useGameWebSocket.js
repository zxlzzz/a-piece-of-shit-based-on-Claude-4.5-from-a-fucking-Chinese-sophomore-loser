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

  const handleReconnecting = (event) => {
    console.log('🔄 GameView: WebSocket 重连中...', event.detail)
    
    toast.add({
      severity: 'warn',
      summary: '连接中断',
      detail: `正在尝试重连... (${event.detail.attempts}/5)`,
      life: 3000
    })
  }

  const handleMaxReconnectFailed = () => {
    console.error('❌ GameView: WebSocket 重连失败')
    
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
      console.log('🔄 GameView: 刷新房间状态...')
      const response = await getRoomStatus(roomCode.value)
      const updatedRoom = response.data
      
      console.log('✅ GameView: 房间状态已刷新:', updatedRoom)
      
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
      console.error('❌ GameView: 刷新房间状态失败:', error)
    }
  }

  const setupRoomSubscription = () => {
    const subs = subscribeRoom(
      roomCode.value,
      (update) => {
        console.log("房间更新:", update)
        
        const oldIndex = room.value?.currentIndex
        const newIndex = update.currentIndex
        
        room.value = update
        
        if (newIndex !== undefined && oldIndex !== newIndex) {
          if (oldIndex !== undefined) {
            const oldSubmissionKey = `submission_${roomCode.value}_${oldIndex}`
            localStorage.removeItem(oldSubmissionKey)
            console.log('🧹 清除旧题目提交记录:', oldSubmissionKey)
          }
          
          clearCountdown()
          
          resetSubmitState()
          question.value = update.currentQuestion
          
          const newSubmissionKey = `submission_${roomCode.value}_${newIndex}`
          const savedSubmission = localStorage.getItem(newSubmissionKey)
          if (savedSubmission === 'true') {
            restoreSubmitState()
            console.log('✅ 新题目已提交过')
          } else {
            console.log('🆕 新题目未提交，可以作答')
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
          console.log('🎮 游戏结束，准备跳转')
          clearCountdown()
          toast.add({
            severity: 'info',
            summary: '游戏结束',
            detail: '正在跳转到结果页面...',
            life: 2000
          })
          setTimeout(() => {
            console.log('🚀 跳转到结果页:', `/result/${roomCode.value}`)
            router.push(`/result/${roomCode.value}`)
          }, 1000)
        }
      },
      (error) => {
        console.error('🔥 房间错误:', error)
        
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
      console.warn('⚠️ GameView: WebSocket 未连接，尝试连接...')
      
      try {
        await connect(playerStore.playerId)
        console.log('✅ GameView: WebSocket 连接成功')
      } catch (err) {
        console.error('❌ GameView: WebSocket 连接失败', err)
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
    refreshRoomState
  }
}