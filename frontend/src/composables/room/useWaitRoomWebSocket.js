import { ref } from 'vue'
import { connect, disconnect, isConnected, subscribeRoom, unsubscribeAll } from '@/websocket/ws'
import { getRoomStatus } from '@/api'

export function useWaitRoomWebSocket(roomCode, playerStore, router, toast) {
  const wsConnected = ref(false)
  const subscriptions = ref([])
  const loading = ref(false)

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
    console.error('🔥 WaitRoom 收到 WebSocket 错误:', event.detail)
    wsConnected.value = false
  }

  const handleReconnecting = (event) => {
    console.log('🔄 WebSocket 重连中...', event.detail)
    wsConnected.value = false

    toast.add({
      severity: 'warn',
      summary: '连接中断',
      detail: `正在尝试重连... (${event.detail.attempts}/5)`,
      life: 3000
    })
  }

  const handleMaxReconnectFailed = () => {
    console.error('❌ WebSocket 重连失败，已达到最大次数')
    wsConnected.value = false

    toast.add({
      severity: 'error',
      summary: '连接失败',
      detail: '连接已断开，请刷新页面',
      life: 0
    })

    setTimeout(() => {
      if (confirm('连接已断开，是否重新连接？')) {
        window.location.reload()
      } else {
        router.push('/find')
      }
    }, 2000)
  }

  const setupRoomSubscription = (room, onRoomUpdate) => {
    console.log('📡 WaitRoom: 开始订阅房间:', roomCode.value)

    if (subscriptions.value.length > 0) {
      console.log('🧹 清理旧订阅')
      unsubscribeAll(subscriptions.value)
      subscriptions.value = []
    }

    try {
      const subs = subscribeRoom(
        roomCode.value,
        (roomUpdate) => {
          console.log("📥 房间更新:", roomUpdate)
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
          console.error('🔥 房间错误:', error)
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
        console.log(`✅ WaitRoom: 订阅成功 (${subs.length} 个订阅)`)
      } else {
        console.error('❌ WaitRoom: 订阅返回空数组')
        throw new Error('订阅返回空数组')
      }
    } catch (err) {
      console.error('❌ WaitRoom: 订阅异常:', err)
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
      console.log('🔄 刷新房间状态...')
      const response = await getRoomStatus(roomCode.value)
      room.value = response.data
      playerStore.setRoom(response.data)

      console.log('✅ 房间状态已刷新:', room.value)

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
      console.error('刷新房间状态失败:', error)
    }
  }

  const connectWebSocket = async (room, onRoomUpdate) => {
    console.log('🔌 WaitRoom: 开始连接流程')

    wsConnected.value = isConnected()
    console.log('🔌 当前连接状态:', wsConnected.value)

    if (wsConnected.value) {
      console.log('✅ WaitRoom: WebSocket 显示已连接，验证连接状态...')

      try {
        setupRoomSubscription(room, onRoomUpdate)
        return
      } catch (err) {
        console.error('❌ 订阅失败，可能连接已断开，尝试重连', err)
        disconnect(true)
        wsConnected.value = false
      }
    }

    console.warn('⚠️ WaitRoom: WebSocket 未连接，开始连接...')

    try {
      loading.value = true

      await connect(playerStore.playerId)

      console.log('✅ WaitRoom: WebSocket 连接成功')

      wsConnected.value = true

      await new Promise(resolve => setTimeout(resolve, 100))

    } catch (err) {
      console.error('❌ WaitRoom: WebSocket 连接失败', err)

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

  const cleanup = () => {
    window.removeEventListener('room-deleted', handleRoomDeleted)
    window.removeEventListener('websocket-error', handleWebSocketError)
    window.removeEventListener('websocket-reconnecting', handleReconnecting)
    window.removeEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)

    if (subscriptions.value.length > 0) {
      unsubscribeAll(subscriptions.value)
      subscriptions.value = []
    }
  }

  const init = () => {
    window.addEventListener('room-deleted', handleRoomDeleted)
    window.addEventListener('websocket-error', handleWebSocketError)
    window.addEventListener('websocket-reconnecting', handleReconnecting)
    window.addEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)
  }

  return {
    wsConnected,
    loading,
    connectWebSocket,
    cleanup,
    init
  }
}
