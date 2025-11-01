import { logger } from '@/utils/logger'
import { ref } from 'vue'
import { connect, disconnect, isConnected, subscribeRoom, unsubscribeAll, registerSubscriptionCallback, unregisterSubscriptionCallback } from '@/websocket/ws'
import { getRoomStatus } from '@/api'

export function useWaitRoomWebSocket(roomCode, playerStore, router, toast) {
  const wsConnected = ref(false)
  const subscriptions = ref([])
  const loading = ref(false)
  let roomUpdateCallback = null // 🔥 保存room更新回调

  const handleRoomDeleted = () => {
    toast.add({
      severity: 'warn',
      summary: '房间已解散',
      detail: '房主已离开，房间被解散',
      life: 3000
    })
    setTimeout(() => {
      router.push('/find')
    }, 1000)
  }

  const handleWebSocketError = (event) => {
    logger.error('🔥 WaitRoom 收到 WebSocket 错误:', event.detail)
    wsConnected.value = false
  }

  const handleReconnecting = (event) => {
    wsConnected.value = false

    const { attempts, maxAttempts, delay } = event.detail
    const delaySeconds = Math.round(delay / 1000)

    toast.add({
      severity: 'warn',
      summary: '连接中断',
      detail: `正在重连 (${attempts}/${maxAttempts})，${delaySeconds}秒后尝试...`,
      life: Math.min(delay + 1000, 4000)
    })
  }

  // 🔥 重连成功处理
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
    logger.error('❌ WebSocket 重连失败，已达到最大次数')
    wsConnected.value = false

    toast.add({
      severity: 'error',
      summary: '连接失败',
      detail: '连接已断开，请返回房间列表',
      life: 4000
    })

    // 🔥 自动跳转而不是confirm弹窗
    setTimeout(() => {
      router.push('/find')
    }, 3000)
  }

  const setupRoomSubscription = (room, onRoomUpdate) => {

    // 🔥 保存回调用于重连恢复
    roomUpdateCallback = onRoomUpdate

    if (subscriptions.value.length > 0) {
      unsubscribeAll(subscriptions.value)
      subscriptions.value = []
    }

    try {
      const subs = subscribeRoom(
        roomCode.value,
        (roomUpdate) => {
          onRoomUpdate(roomUpdate)

          if (roomUpdate.status === 'PLAYING') {
            toast.add({
              severity: 'info',
              summary: '游戏开始',
              detail: '正在进入游戏...',
              life: 2000
            })
            router.push(`/game/${roomCode.value}`)
          }
        },
        (error) => {
          logger.error('🔥 房间错误:', error)
          toast.add({
            severity: 'error',
            summary: '房间错误',
            detail: error.error || '房间出现错误',
            life: 3000
          })
        }
      )

      if (subs && subs.length > 0) {
        subscriptions.value = subs
      } else {
        logger.error('❌ WaitRoom: 订阅返回空数组')
        throw new Error('订阅返回空数组')
      }
    } catch (err) {
      logger.error('❌ WaitRoom: 订阅异常:', err)
      toast.add({
        severity: 'error',
        summary: '订阅失败',
        detail: '订阅房间时出现异常',
        life: 3000
      })
      throw err
    }
  }

  const refreshRoomState = async (room) => {
    try {
      const response = await getRoomStatus(roomCode.value)
      room.value = response.data
      playerStore.setRoom(response.data)


      if (room.value.status === 'PLAYING') {
        toast.add({
          severity: 'info',
          summary: '游戏进行中',
          detail: '正在进入游戏...',
          life: 2000
        })
        router.push(`/game/${roomCode.value}`)
      }
    } catch (error) {
      logger.error('刷新房间状态失败:', error)
    }
  }

  const connectWebSocket = async (room, onRoomUpdate) => {

    wsConnected.value = isConnected()

    if (wsConnected.value) {

      try {
        setupRoomSubscription(room, onRoomUpdate)
        return
      } catch (err) {
        logger.error('❌ 订阅失败，可能连接已断开，尝试重连', err)
        disconnect(true)
        wsConnected.value = false
      }
    }


    try {
      loading.value = true

      await connect(playerStore.playerId)


      wsConnected.value = true

      await new Promise(resolve => setTimeout(resolve, 100))

    } catch (err) {
      logger.error('❌ WaitRoom: WebSocket 连接失败', err)

      wsConnected.value = false

      toast.add({
        severity: 'error',
        summary: '连接失败',
        detail: err.message === '连接超时'
          ? 'WebSocket 连接超时，请刷新页面'
          : 'WebSocket 连接失败：' + err.message,
        life: 5000
      })

      loading.value = false

      if (confirm('WebSocket 连接失败，是否重试？')) {
        await connectWebSocket(room, onRoomUpdate)
        return
      } else {
        router.push('/find')
        return
      }
    } finally {
      loading.value = false
    }

    setupRoomSubscription(room, onRoomUpdate)
    await refreshRoomState(room)
  }

  // 🔥 订阅恢复回调（重连后自动调用）
  const subscriptionRestoreCallback = () => {
    if (roomUpdateCallback) {
      try {
        setupRoomSubscription({ value: null }, roomUpdateCallback)
      } catch (err) {
        logger.error('恢复订阅失败:', err)
      }
    }
  }

  const cleanup = () => {
    window.removeEventListener('room-deleted', handleRoomDeleted)
    window.removeEventListener('websocket-error', handleWebSocketError)
    window.removeEventListener('websocket-reconnecting', handleReconnecting)
    window.removeEventListener('websocket-reconnected', handleReconnected)
    window.removeEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)

    // 🔥 取消注册订阅恢复回调
    unregisterSubscriptionCallback(subscriptionRestoreCallback)

    if (subscriptions.value.length > 0) {
      unsubscribeAll(subscriptions.value)
      subscriptions.value = []
    }
  }

  const init = () => {
    window.addEventListener('room-deleted', handleRoomDeleted)
    window.addEventListener('websocket-error', handleWebSocketError)
    window.addEventListener('websocket-reconnecting', handleReconnecting)
    window.addEventListener('websocket-reconnected', handleReconnected)
    window.addEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)

    // 🔥 注册订阅恢复回调
    registerSubscriptionCallback(subscriptionRestoreCallback)
  }

  return {
    wsConnected,
    loading,
    connectWebSocket,
    cleanup,
    init
  }
}
