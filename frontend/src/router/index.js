import { logger } from '@/utils/logger'
import { usePlayerStore } from '@/stores/player'
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


  // 🔥 离开房间页面时断开 WebSocket
  const roomPages = ['wait', 'game', 'result']
  const fromRoom = roomPages.includes(from.name)
  const toRoom = roomPages.includes(to.name)

  if (fromRoom && !toRoom) {
    try {
      const { disconnect, isConnected } = await import('@/websocket/ws')
      if (isConnected()) {
        disconnect()
      }
    } catch (error) {
      logger.error('断开WebSocket失败:', error)
    }
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
            next()
            return
          }
        } catch (error) {
          // 🔥 静默处理，清理本地数据，跳转到查找房间页
          playerStore.clearRoom()
          next({ name: 'find', replace: true })
          return
        }
      }

      if (loaded && loaded.roomCode !== roomId) {
        playerStore.clearRoom()
        next({ name: 'find', replace: true })
        return
      }

      if (loaded) {
      }
    } else if (currentRoom.roomCode !== roomId) {
      playerStore.clearRoom()
      next({ name: 'find', replace: true })
      return
    }
  }

  next()
})

export default router