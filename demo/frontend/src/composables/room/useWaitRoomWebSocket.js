import { logger } from '@/utils/logger'
import { ref } from 'vue'
import { isConnected, subscribeRoom, unsubscribeAll, registerSubscriptionCallback, unregisterSubscriptionCallback, waitForConnection } from '@/websocket/ws'
import { getRoomStatus } from '@/api'

export function useWaitRoomWebSocket(roomCode, playerStore, router, toast) {
  const wsConnected = ref(false)
  const subscriptions = ref([])
  const loading = ref(false)
  let roomUpdateCallback = null //  保存room更新回调
  let isSubscribed = false //  标记是否已订阅
  let isActive = false ��标记页面是否活跃，防止卸载后执行回调

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
    logger.error(' WaitRoom 收到 WebSocket 错误:', event.detail)
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

  //  重连成功处理
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
    logger.error(' WebSocket 重连失败，已达到最大次数')
    wsConnected.value = false

    toast.add({
      severity: 'error',
      summary: '连接失败',
      detail: '连接已断开，请返回房间列表',
      life: 4000
    })

    //  自动跳转而不是confirm弹窗
    setTimeout(() => {
      router.push('/find')
    }, 3000)
  }

  const setupRoomSubscription = (room, onRoomUpdate) => {
    logger.debug('WaitRoom: 设置房间订阅');

    //  保存回调用于重连恢复
    roomUpdateCallback = onRoomUpdate

    //  避免重复订阅
    if (isSubscribed && subscriptions.value.length > 0) {
      logger.debug('已存在订阅，先取消旧订阅');
      unsubscribeAll(subscriptions.value);
      subscriptions.value = [];
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
          logger.error(' 房间错误:', error)
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
        isSubscribed = true
        logger.debug('WaitRoom: 订阅成功，共', subs.length, '个订阅');
      } else {
        logger.error(' WaitRoom: 订阅返回空数组')
        throw new Error('订阅返回空数组')
      }
    } catch (err) {
      logger.error(' WaitRoom: 订阅异常:', err)
      isSubscribed = false
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
    //  简化：不再管理连接，只管理订阅
    // 连接由 App.vue 全局管理

    wsConnected.value = isConnected()

    if (!wsConnected.value) {
      logger.error(' WaitRoom: WebSocket 未连接，等待全局连接建立')
      toast.add({
        severity: 'warn',
        summary: '等待连接',
        detail: '正在建立连接，请稍候...',
        life: 3000
      })

      //  优化：使用事件驱动等待连接，避免轮询（最多3秒）
      try {
        await waitForConnection(3000)
        wsConnected.value = true
      } catch (error) {
        wsConnected.value = false
        logger.error('WaitRoom: 等待连接超时', error)
        toast.add({
          severity: 'error',
          summary: '连接失败',
          detail: 'WebSocket 连接失败，请刷新页面',
          life: 5000
        })
        return
      }
    }

    // 设置订阅
    try {
      loading.value = true
      setupRoomSubscription(room, onRoomUpdate)
      await refreshRoomState(room)
    } catch (err) {
      logger.error(' WaitRoom: 订阅失败', err)
      toast.add({
        severity: 'error',
        summary: '订阅失败',
        detail: '订阅房间失败，请刷新页面',
        life: 5000
      })
    } finally {
      loading.value = false
    }
  }

  //  订阅恢复回调（重连后自动调用）
  ��只在页面活跃时恢复订阅，避免页面卸载后仍执行回调
  const subscriptionRestoreCallback = () => {
    // 只在页面活跃时恢复订阅
    if (!isActive) {
      logger.debug('WaitRoom: 页面已卸载，跳过重连恢复');
      return;
    }

    logger.debug('WaitRoom: 重连后恢复订阅');
    if (roomUpdateCallback) {
      try {
        // setupRoomSubscription不使用room参数，可以安全传递null
        setupRoomSubscription(null, roomUpdateCallback);
        logger.info(' WaitRoom: 重连后订阅恢复成功');
      } catch (err) {
        logger.error('WaitRoom: 恢复订阅失败:', err);
      }
    } else {
      logger.warn('WaitRoom: 没有保存的回调，无法恢复订阅');
    }
  }

  const cleanup = () => {
    logger.debug('WaitRoom: 清理资源');

    ��标记页面不再活跃，防止回调执行
    isActive = false;

    window.removeEventListener('room-deleted', handleRoomDeleted)
    window.removeEventListener('websocket-error', handleWebSocketError)
    window.removeEventListener('websocket-reconnecting', handleReconnecting)
    window.removeEventListener('websocket-reconnected', handleReconnected)
    window.removeEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)

    //  取消注册订阅恢复回调
    unregisterSubscriptionCallback(subscriptionRestoreCallback)

    if (subscriptions.value.length > 0) {
      unsubscribeAll(subscriptions.value)
      subscriptions.value = []
    }

    isSubscribed = false
    roomUpdateCallback = null
  }

  const init = () => {
    ��标记页面活跃
    isActive = true;

    window.addEventListener('room-deleted', handleRoomDeleted)
    window.addEventListener('websocket-error', handleWebSocketError)
    window.addEventListener('websocket-reconnecting', handleReconnecting)
    window.addEventListener('websocket-reconnected', handleReconnected)
    window.addEventListener('websocket-max-reconnect-failed', handleMaxReconnectFailed)

    //  注册订阅恢复回调
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
