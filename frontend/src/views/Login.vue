<script setup>
import { createPlayer } from '@/api'
import { usePlayerStore } from '@/stores/player'
import { generatePlayerId, validatePlayerName } from '@/utils/player'
import { useToast } from 'primevue/usetoast'
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const playerStore = usePlayerStore()
const router = useRouter()
const toast = useToast()

const isLogin = ref(true) // true = 登录, false = 注册
const name = ref('')
const loading = ref(false)

const switchMode = () => {
  isLogin.value = !isLogin.value
  name.value = '' // 切换时清空输入
}

const handleSubmit = async () => {
  // 验证输入
  const validation = validatePlayerName(name.value)
  if (!validation.valid) {
    toast.add({
      severity: 'error',
      summary: '输入错误',
      detail: validation.message,
      life: 3000
    })
    return
  }
  
  loading.value = true
  try {
    // 🔥 生成唯一 playerId
    const playerId = generatePlayerId()
    console.log('🔍 生成 playerId:', playerId)
    
    // 🔥 传入 playerId 和 name
    const resp = await createPlayer(playerId, name.value)
    const userData = resp.data
    
    console.log('🔍 后端返回数据:', userData)
    
    // 🔥 保存用户信息（使用后端返回的 id 或前端生成的 playerId）
    playerStore.setPlayer(userData.id || playerId, userData.name)
    
    console.log('🔍 保存后 Pinia 状态:', {
      playerId: playerStore.playerId,
      playerName: playerStore.playerName
    })
    
    toast.add({
      severity: 'success',
      summary: isLogin.value ? '登录成功' : '注册成功',
      detail: `欢迎，${userData.name}!`,
      life: 2000
    })
    
    router.push('/find')
    
  } catch (err) {
    console.error('操作失败', err)
  } finally {
    loading.value = false
  }
}

const handleKeyPress = (event) => {
  if (event.key === 'Enter' && name.value.trim()) {
    handleSubmit()
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center px-4">
    <div class="w-full max-w-md">
      <!-- Logo 区域 -->
      <div class="text-center mb-8">
        <h1 class="text-3xl font-bold text-gray-900 dark:text-white mb-2">
          答题游戏
        </h1>
        <p class="text-gray-600 dark:text-gray-400">
          实时对战，智力较量
        </p>
      </div>

      <!-- 主卡片 -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-8">
        
        <!-- 切换按钮 -->
        <div class="flex gap-2 mb-6 p-1 bg-gray-100 dark:bg-gray-700 rounded-lg">
          <button
            @click="switchMode"
            class="flex-1 py-2 rounded-md font-medium transition-all"
            :class="isLogin 
              ? 'bg-white dark:bg-gray-600 text-gray-900 dark:text-white shadow-sm' 
              : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white'"
          >
            登录
          </button>
          <button
            @click="switchMode"
            class="flex-1 py-2 rounded-md font-medium transition-all"
            :class="!isLogin 
              ? 'bg-white dark:bg-gray-600 text-gray-900 dark:text-white shadow-sm' 
              : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white'"
          >
            注册
          </button>
        </div>

        <!-- 表单区域 -->
        <div class="space-y-4">
          <div>
            <label 
              for="name" 
              class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2"
            >
              昵称
            </label>
            <input 
              id="name"
              v-model="name"
              type="text"
              placeholder="请输入昵称"
              class="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg 
                     bg-white dark:bg-gray-700 text-gray-900 dark:text-white
                     focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
                     disabled:opacity-50 disabled:cursor-not-allowed"
              @keypress="handleKeyPress"
              :disabled="loading"
              autofocus
            />
          </div>

          <!-- 提交按钮 -->
          <button
            @click="handleSubmit"
            :disabled="loading || !name.trim()"
            class="w-full py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-lg
                   transition-colors disabled:opacity-50 disabled:cursor-not-allowed
                   flex items-center justify-center gap-2"
          >
            <i v-if="loading" class="pi pi-spin pi-spinner"></i>
            <span v-if="loading">{{ isLogin ? '登录中...' : '注册中...' }}</span>
            <span v-else>{{ isLogin ? '登录' : '注册' }}</span>
          </button>
        </div>

        <!-- 提示信息 -->
        <div class="mt-6 text-center">
          <p class="text-sm text-gray-500 dark:text-gray-400">
            {{ isLogin ? '首次使用？系统将自动创建账号' : '已有账号？可直接登录' }}
          </p>
        </div>
      </div>

      <!-- 底部链接 -->
      <div class="mt-6 text-center">
        <button 
          @click="router.push('/')"
          class="text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white"
        >
          ← 返回首页
        </button>
      </div>
    </div>
  </div>
</template>