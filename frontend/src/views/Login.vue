<script setup>
import { logger } from '@/utils/logger'
import { login, register } from '@/api'
import { usePlayerStore } from '@/stores/player'
import { validateUsername, validatePassword, validatePlayerName } from '@/utils/player'
import { useToast } from 'primevue/usetoast'
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'

const playerStore = usePlayerStore()
const router = useRouter()
const toast = useToast()

const isLogin = ref(true) // true = 登录, false = 注册
const username = ref('')
const password = ref('')
const name = ref('') // 昵称（仅注册时需要）
const loading = ref(false)

const switchMode = () => {
  isLogin.value = !isLogin.value
  username.value = ''
  password.value = ''
  name.value = ''
}

const handleSubmit = async () => {
  // 验证用户名
  const usernameValidation = validateUsername(username.value)
  if (!usernameValidation.valid) {
    toast.add({
      severity: 'error',
      summary: '输入错误',
      detail: usernameValidation.message,
      life: 3000
    })
    return
  }
  
  // 验证密码
  const passwordValidation = validatePassword(password.value)
  if (!passwordValidation.valid) {
    toast.add({
      severity: 'error',
      summary: '输入错误',
      detail: passwordValidation.message,
      life: 3000
    })
    return
  }
  
  // 如果是注册，验证昵称
  if (!isLogin.value) {
    const nameValidation = validatePlayerName(name.value)
    if (!nameValidation.valid) {
      toast.add({
        severity: 'error',
        summary: '输入错误',
        detail: nameValidation.message,
        life: 3000
      })
      return
    }
  }
  
  loading.value = true
  try {
    let resp
    
    if (isLogin.value) {
      // 登录
      resp = await login(username.value.trim().toLowerCase(), password.value)
    } else {
      // 注册
      resp = await register(
        username.value.trim().toLowerCase(),
        password.value,
        name.value.trim()
      )
    }
    
    const authData = resp.data
    
    // 保存用户信息到 store
    playerStore.setPlayer(authData)
    
    toast.add({
      severity: 'success',
      summary: isLogin.value ? '登录成功' : '注册成功',
      detail: `欢迎，${authData.name}!`,
      life: 2000
    })
    
    // 跳转到主页
    setTimeout(() => {
      router.push('/find')
    }, 500)
    
  } catch (err) {
    logger.error('操作失败:', err)
    
    const errorMsg = err.response?.data?.message || 
                     err.response?.data || 
                     (isLogin.value ? '登录失败' : '注册失败')
    
    toast.add({
      severity: 'error',
      summary: '操作失败',
      detail: errorMsg,
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const handleKeyPress = (event) => {
  if (event.key === 'Enter' && canSubmit.value) {
    handleSubmit()
  }
}

// 计算是否可以提交
const canSubmit = computed(() => {
  if (isLogin.value) {
    return username.value.trim() && password.value.trim()
  } else {
    return username.value.trim() && password.value.trim() && name.value.trim()
  }
})
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center px-3 sm:px-4">
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
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6 sm:p-8">
        
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
          <!-- 用户名 -->
          <div>
            <label 
              for="username" 
              class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2"
            >
              用户名
            </label>
            <input 
              id="username"
              v-model="username"
              type="text"
              placeholder="请输入用户名"
              class="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg 
                     bg-white dark:bg-gray-700 text-gray-900 dark:text-white
                     focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
                     disabled:opacity-50 disabled:cursor-not-allowed"
              @keypress="handleKeyPress"
              :disabled="loading"
              autofocus
            />
          </div>

          <!-- 密码 -->
          <div>
            <label 
              for="password" 
              class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2"
            >
              密码
            </label>
            <input 
              id="password"
              v-model="password"
              type="password"
              placeholder="请输入密码（至少6位）"
              class="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg 
                     bg-white dark:bg-gray-700 text-gray-900 dark:text-white
                     focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
                     disabled:opacity-50 disabled:cursor-not-allowed"
              @keypress="handleKeyPress"
              :disabled="loading"
            />
          </div>

          <!-- 昵称（仅注册时显示）-->
          <div v-if="!isLogin">
            <label 
              for="name" 
              class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2"
            >
              游戏昵称
            </label>
            <input 
              id="name"
              v-model="name"
              type="text"
              placeholder="请输入游戏昵称"
              class="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg 
                     bg-white dark:bg-gray-700 text-gray-900 dark:text-white
                     focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
                     disabled:opacity-50 disabled:cursor-not-allowed"
              @keypress="handleKeyPress"
              :disabled="loading"
            />
          </div>

          <!-- 提交按钮 -->
          <button
            @click="handleSubmit"
            :disabled="loading || !canSubmit"
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
            {{ isLogin ? '还没有账号？点击上方注册' : '已有账号？点击上方登录' }}
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