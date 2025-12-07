import { logger } from '@/utils/logger'
import { ref, onMounted, onUnmounted } from 'vue'
import { isConnected, subscribeRoom, unsubscribeAll, unsubscribeRoom, registerSubscriptionCallback, unregisterSubscriptionCallback, waitForConnection } from '@/websocket/ws'
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
  let isSubscribed = false 

  const handleReconnecting = (event) => {
    wsConnected.value = false //  更新连接状态
    toast.add({
      severity: 'warn',
      summary: '连接中断',
      detail: `正在尝试重连... (${event.detail.attempts}/5)`,
      life: 3000
    })
  }

  const handleReconnected = () => {
    wsConnected.value = true
    toast.add({
      severity: 'success',
      summary: '重连成功',
      detail: '连接已恢复',
      life: 2000
    })
  }

  const handleMaxReconnectFailed = () => {
    logger.error(' GameView: WebSocket 重连失败')

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

      // 重连时恢复提交状态
      if (restoreSubmitState) {
        restoreSubmitState()
      }

      
      if (verifySubmissionState && updatedRoom.submittedPlayerIds) {
        verifySubmissionState(updatedRoom.submittedPlayerIds)
      }

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
      logger.error(' GameView: 刷新房间状态失败:', error)
    }
  }

  const setupRoomSubscription = () => {
    
    if (isSubscribed && subscriptions.value.length > 0) {
      logger.debug('已存在订阅，先取消旧订阅');
      unsubscribeAll(subscriptions.value);
      subscriptions.value = [];
    }

    const subs = subscribeRoom(
      roomCode.value,
      (update) => {

        const oldIndex = room.value?.currentIndex
        const newIndex = update.currentIndex
        const oldQuestionStartTime = room.value?.questionStartTime
        const newQuestionStartTime = update.questionStartTime

        // 检测是否是第一次收到题目数据（游戏刚开始）
        const isFirstLoad = (oldIndex === undefined || oldIndex === -1) && newIndex >= 0
        const indexChanged = newIndex !== undefined && oldIndex !== newIndex
        const questionTimeChanged = newQuestionStartTime && oldQuestionStartTime !== newQuestionStartTime

        // 题目切换、重复题换轮、或首次加载时都需要处理
        if (isFirstLoad || indexChanged || questionTimeChanged) {
          if (indexChanged && newIndex !== undefined) {
            const submissionPrefix = `submission_${roomCode.value}_`
            const keysToRemove = []

            for (let i = 0; i < localStorage.length; i++) {
              const key = localStorage.key(i)
              if (key && key.startsWith(submissionPrefix)) {
                const match = key.match(/submission_[^_]+_(\d+)/)
                if (match) {
                  const keyIndex = parseInt(match[1])
                  if (keyIndex < newIndex) {
                    keysToRemove.push(key)
                  }
                }
              }
            }

            keysToRemove.forEach(key => {
              localStorage.removeItem(key)
              logger.debug('🧹 清理旧题目提交记录:', key)
            })
          }

          clearCountdown()
          resetSubmitState()

          
          room.value = update
          question.value = update.currentQuestion

          const newSubmissionKey = `submission_${roomCode.value}_${newIndex}`
          const savedSubmission = localStorage.getItem(newSubmissionKey)
          if (savedSubmission === 'true') {
            restoreSubmitState()
            logger.info(' WebSocket恢复提交状态:', { newIndex, hasSubmitted: true })
          }

          if (update.questionStartTime) {
            questionStartTime.value = new Date(update.questionStartTime)
            timeLimit.value = update.timeLimit || 30
            resetCountdown()
          }
        } else {
          room.value = update
          question.value = update.currentQuestion
        }

        playerStore.setRoom(update)

        
        if (verifySubmissionState && update.submittedPlayerIds) {
          verifySubmissionState(update.submittedPlayerIds)
        }

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
        logger.error(' 房间错误:', error)
        
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
    isSubscribed = true
  }

  const subscriptionRestoreCallback = () => {
    logger.debug('GameView: 重连后恢复订阅');
    try {
      setupRoomSubscription();
      refreshRoomState();
    } catch (err) {
      logger.error('GameView: 恢复订阅失败:', err);
    }
  }

  const connectWebSocket = async () => {

    wsConnected.value = isConnected()

    if (!wsConnected.value) {
      logger.error(' GameView: WebSocket 未连接，等待全局连接建立')
      toast.add({
        severity: 'warn',
        summary: '等待连接',
        detail: '正在建立连接，请稍候...',
        life: 3000
      })

      try {
        await waitForConnection(3000)
        wsConnected.value = true
      } catch (error) {
        wsConnected.value = false
        logger.error('GameView: 等待连接超时', error)
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
    window.addEventListener('websocket-reconnected', handleReconnected)
    window.addEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)

    registerSubscriptionCallback(subscriptionRestoreCallback)
  })

  onUnmounted(() => {
    // 使用unsubscribeRoom清理订阅，确保从全局Map中移除
    unsubscribeRoom(roomCode.value)

    window.removeEventListener('websocket-reconnecting', handleReconnecting)
    window.removeEventListener('websocket-reconnected', handleReconnected)
    window.removeEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)

    unregisterSubscriptionCallback(subscriptionRestoreCallback)

    isSubscribed = false
  })

  return {
    connectWebSocket,
    refreshRoomState,
    wsConnected 
  }
}