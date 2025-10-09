import AppLayout from '@/layout/AppLayout.vue'
import { usePlayerStore } from '@/stores/player'
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      children: [
        {
          path: '/',
          name: 'dashboard',
          component: () => import('@/views/Main.vue')
        },
        {
          path: '/table',
          name: 'table',
          component: () => import('@/views/Question.vue')
        },
        {
          path: '/find',
          name: 'find',
          component: () => import('@/views/Room/RoomView.vue')
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
          component: () => import('@/views/Room/ResultView.vue'),
          meta: { requiresAuth: true }  // ✅ 保留
        },
        {
          path: '/game/:roomId',
          name: 'game',
          component: () => import('@/views/Room/GameView.vue'),
          meta: { requiresAuth: true }  // ✅ 保留
        },
        {
          path: '/wait/:roomId',
          name: 'wait',
          component: () => import('@/views/Room/WaitRoom.vue'),
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
    }
  ]
})

router.beforeEach((to, from, next) => {
  const playerStore = usePlayerStore()
  
  console.log('🛣️ 路由守卫:', from.name, '→', to.name, '登录状态:', playerStore.isLoggedIn)
  
  // 1. 检查是否需要登录
  if (to.meta.requiresAuth && !playerStore.isLoggedIn) {
    console.warn('❌ 未登录，跳转到登录页')
    alert('请先登录')
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }
  
  // 2. 检查房间权限（wait/game/result）
  if (to.name === 'wait' || to.name === 'game' || to.name === 'result') {
    const roomId = to.params.roomId
    const currentRoom = playerStore.currentRoom
    
    console.log('🏠 检查房间权限:', { roomId, currentRoom: currentRoom?.roomCode })
    
    // 🔥 改进：先尝试从 store 获取，如果没有再从 localStorage 加载
    if (!currentRoom) {
      console.log('📦 从 localStorage 加载房间信息')
      const loaded = playerStore.loadRoom()
      
      if (!loaded) {
        console.error('❌ 没有房间信息')
        alert('房间信息不存在，请重新加入房间')
        next({ name: 'find' })
        return
      }
      
      if (loaded.roomCode !== roomId) {
        console.error('❌ 房间码不匹配:', loaded.roomCode, '≠', roomId)
        alert('房间不匹配')
        next({ name: 'find' })
        return
      }
      
      console.log('✅ 房间信息加载成功:', loaded.roomCode)
    } else if (currentRoom.roomCode !== roomId) {
      console.error('❌ 当前房间与目标房间不匹配')
      alert('房间不匹配')
      next({ name: 'find' })
      return
    }
  }
  
  console.log('✅ 路由守卫通过')
  next()
})

export default router