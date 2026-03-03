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
        },
        {
          path: '/history',
          name: 'history',
          component: () => import('@/views/HistoryView.vue')
          
        },
        {
          path: '/result/:roomId',
          name: 'result',
          component: () => import('@/views/room/ResultView.vue'),
          meta: { requiresAuth: true }  //  保留
        },
        {
          path: '/game/:roomId',
          name: 'game',
          component: () => import('@/views/room/GameView.vue'),
          meta: { requiresAuth: true }  //  保留
        },
        {
          path: '/wait/:roomId',
          name: 'wait',
          component: () => import('@/views/room/WaitRoom.vue'),
          props: true,
          meta: { requiresAuth: true }  //  保留
        },
        {
          path: '/login',
          name: 'login',
          component: () => import('@/views/Login.vue')
        }
      ]
    }
  ]
})

router.beforeEach(async (to, from, next) => {
  const playerStore = usePlayerStore()
  const chatStore = useChatStore()

  const roomPages = ['wait', 'game', 'result']
  const fromRoom = roomPages.includes(from.name)
  const toRoom = roomPages.includes(to.name)

  if (fromRoom && !toRoom) {
    try {
      chatStore.unsubscribeFromChat()
      chatStore.clearChat()

      const { disconnect, isConnected } = await import('@/websocket/ws')
      if (isConnected()) {
        disconnect()
      }
      logger.info(' 路由守卫: 离开房间页面，已断开聊天订阅和WebSocket')
    } catch (error) {
      logger.error('断开WebSocket失败:', error)
    }
  }

  if (toRoom && to.params.roomId) {
    chatStore.subscribeToChat(to.params.roomId).then(() => {
      logger.info(' 路由守卫: 进入房间页面，聊天订阅成功', to.params.roomId)
    }).catch((err) => {
      logger.error(' 路由守卫: 聊天订阅失败', err)
    })
  }

  if (to.meta.requiresAuth && !playerStore.isLoggedIn) {
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }

  if (to.name === 'wait' || to.name === 'game' || to.name === 'result') {
    const roomId = to.params.roomId
    const currentRoom = playerStore.currentRoom


    if (!currentRoom) {
      const loaded = playerStore.loadRoom()

      if (!loaded) {

        
        try {
          const { getRoomStatus } = await import('@/api')
          const response = await getRoomStatus(roomId, true)  //  silentError=true

          if (response.data) {
            playerStore.setRoom(response.data)

            
            if (to.name === 'result' && !response.data.finished) {
              next({ name: response.data.started ? 'game' : 'wait', params: { roomId }, replace: true })
              return
            }

            next()
            return
          }
        } catch (error) {
          playerStore.clearRoom()
          
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
      
      if (to.name === 'result' && !currentRoom.finished) {
        next({ name: currentRoom.started ? 'game' : 'wait', params: { roomId }, replace: true })
        return
      }
    }
  }

  next()
})

export default router