import { logger } from '@/utils/logger'
import { usePlayerStore } from '@/stores/player'
import { useChatStore } from '@/stores/chat'
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('@/layout/AppLayout.vue'),
      children: [
        {
          path: '/',
          name: 'dashboard',
          component: () => import('@/views/Main.vue')
        },
        {
          path: '/table',
          name: 'table',
          component: () => import('@/components/question/Question.vue')
        },
        {
          path: '/find',
          name: 'find',
          component: () => import('@/views/room/RoomView.vue')
          // 🔥 删除 meta: { requiresAuth: true }
        },
        {
          path: '/call',
          name: 'call',
          component: () => import('@/views/Call.vue')
        },
        {
          path: '/history',
          name: 'history',
          component: () => import('@/views/HistoryView.vue'),
          meta: { requiresAuth: true }  // ✅ 保留
        },
        {
          path: '/result/:roomId',
          name: 'result',
          component: () => import('@/views/room/ResultView.vue'),
          meta: { requiresAuth: true }  // ✅ 保留
        },
        {
          path: '/game/:roomId',
          name: 'game',
          component: () => import('@/views/room/GameView.vue'),
          meta: { requiresAuth: true }  // ✅ 保留
        },
        {
          path: '/wait/:roomId',
          name: 'wait',
          component: () => import('@/views/room/WaitRoom.vue'),
          props: true,
          meta: { requiresAuth: true }  // ✅ 保留
        },
        {
          path: '/login',
          name: 'login',
          component: () => import('@/views/Login.vue')
        }
      ]
    },
    {
      path: '/admin/questions',
      name: 'admin-questions',
      component: () => import('@/views/admin/AdminQuestions.vue')
    },
    {
      path: '/admin/test',
      name: 'admin-test',
      component: () => import('@/views/admin/AdminTest.vue')
    }
  ]
})

router.beforeEach(async (to, from, next) => {
  const playerStore = usePlayerStore()
  const chatStore = useChatStore()

  // 🔥 管理聊天订阅和WebSocket连接
  const roomPages = ['wait', 'game', 'result']
  const fromRoom = roomPages.includes(from.name)
  const toRoom = roomPages.includes(to.name)

  // 🔥 离开房间页面去其他页面时，断开聊天订阅和WebSocket
  if (fromRoom && !toRoom) {
    try {
      // 取消聊天订阅
      chatStore.unsubscribeFromChat()
      chatStore.clearChat()

      // 断开WebSocket
      const { disconnect, isConnected } = await import('@/websocket/ws')
      if (isConnected()) {
        disconnect()
      }
      logger.info('✅ 路由守卫: 离开房间页面，已断开聊天订阅和WebSocket')
    } catch (error) {
      logger.error('断开WebSocket失败:', error)
    }
  }

  // 🔥 进入房间页面时，订阅聊天（如果还没订阅）
  if (toRoom && to.params.roomId) {
    // 延迟订阅，等待WebSocket连接建立
    setTimeout(() => {
      chatStore.subscribeToChat(to.params.roomId)
      logger.info('✅ 路由守卫: 进入房间页面，订阅聊天', to.params.roomId)
    }, 600)
  }

  // 1. 检查是否需要登录
  if (to.meta.requiresAuth && !playerStore.isLoggedIn) {
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }

  // 2. 检查房间权限（wait/game/result）
  if (to.name === 'wait' || to.name === 'game' || to.name === 'result') {
    const roomId = to.params.roomId
    const currentRoom = playerStore.currentRoom


    // 🔥 改进：先尝试从 store 获取，如果没有再从 localStorage 加载
    if (!currentRoom) {
      const loaded = playerStore.loadRoom()

      if (!loaded) {

        // 🔥 新增：尝试从服务器获取房间状态（静默失败）
        try {
          const { getRoomStatus } = await import('@/api')
          const response = await getRoomStatus(roomId, true)  // 🔥 silentError=true

          if (response.data) {
            playerStore.setRoom(response.data)

            // 🔥 检查result页面：只有finished的游戏才能访问
            if (to.name === 'result' && !response.data.finished) {
              next({ name: response.data.started ? 'game' : 'wait', params: { roomId }, replace: true })
              return
            }

            next()
            return
          }
        } catch (error) {
          // 🔥 静默处理，清理本地数据，跳转到查找房间页
          playerStore.clearRoom()
          // 🔥 新增：添加错误信息到路由query，让find页面显示
          next({
            name: 'find',
            replace: true,
            query: { error: 'room_not_found' }
          })
          return
        }
      }

      if (loaded && loaded.roomCode !== roomId) {
        playerStore.clearRoom()
        next({ name: 'find', replace: true })
        return
      }

      if (loaded) {
        // 🔥 检查result页面：只有finished的游戏才能访问
        if (to.name === 'result' && !loaded.finished) {
          next({ name: loaded.started ? 'game' : 'wait', params: { roomId }, replace: true })
          return
        }
      }
    } else if (currentRoom.roomCode !== roomId) {
      playerStore.clearRoom()
      next({ name: 'find', replace: true })
      return
    } else {
      // 🔥 检查result页面：只有finished的游戏才能访问
      if (to.name === 'result' && !currentRoom.finished) {
        next({ name: currentRoom.started ? 'game' : 'wait', params: { roomId }, replace: true })
        return
      }
    }
  }

  next()
})

export default router