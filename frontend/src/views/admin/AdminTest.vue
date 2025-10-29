<script setup>
import { logger } from '@/utils/logger'
import { joinRoom } from '@/api'
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStore } from '@/stores/player'
import { useToast } from 'primevue/usetoast'
import axios from 'axios'

const router = useRouter()
const playerStore = usePlayerStore()
const toast = useToast()

const maxPlayers = ref(3)
const questionCount = ref(5)
const loading = ref(false)

// 🔥 检查登录状态
onMounted(() => {
  if (!playerStore.isLoggedIn) {
    toast.add({
      severity: 'error',
      summary: '未登录',
      detail: '请先登录后再使用测试工具',
      life: 3000
    })
    router.push('/login')
    return
  }
})

/* ================================================
   🔥 axios 实例配置
================================================ */
const api = axios.create({
  baseURL: "/api",
  timeout: 10000,
});

// ============ 请求拦截器（添加 token）============
api.interceptors.request.use(
  (config) => {
    // 🔥 自动添加 token 到请求头
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => {
    logger.error('Request Error:', error);
    return Promise.reject(error);
  }
);

// ============ 响应拦截器 ============
api.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    logger.error('API Error:', error.response?.data || error.message);

    // 🔥 处理 401 未授权错误
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('playerId');
      localStorage.removeItem('playerName');
      window.location.href = '/login';
      return Promise.reject(error);
    }

    return Promise.reject(error);
  }
);

const createTestRoom = async () => {
  // 🔥 再次检查登录状态（防御性编程）
  if (!playerStore.isLoggedIn || !playerStore.playerId || !playerStore.playerName) {
    toast.add({
      severity: 'error',
      summary: '未登录',
      detail: '请先登录后再创建测试房间',
      life: 3000
    })
    router.push('/login')
    return
  }

  if (maxPlayers.value < 2 || maxPlayers.value > 10) {
    toast.add({
      severity: 'error',
      summary: '错误',
      detail: '玩家数量必须在 2-10 之间',
      life: 3000
    })
    return
  }

  if (questionCount.value < 1 || questionCount.value > 20) {
    toast.add({
      severity: 'error',
      summary: '错误',
      detail: '题目数量必须在 1-20 之间',
      life: 3000
    })
    return
  }

  loading.value = true

  try {
    // 1. 创建测试房间
    const createResponse = await api.post('/admin/test/room', null, {
      params: {
        maxPlayers: maxPlayers.value,
        questionCount: questionCount.value
      }
    })

    const { roomCode, botCount } = createResponse.data

    // 2. 🔥 真实玩家加入房间（与普通房间一样）
    const joinResponse = await joinRoom(
      roomCode,
      playerStore.playerId,
      playerStore.playerName,
      false  // 不是观战者
    )

    // 3. 保存房间信息到 store
    playerStore.setRoom(joinResponse.data)
    playerStore.setSpectator(false)

    toast.add({
      severity: 'success',
      summary: '创建成功',
      detail: `测试房间 ${roomCode} 创建成功，已添加 ${botCount} 个虚拟玩家`,
      life: 2000
    })

    // 4. 跳转到等待房间
    setTimeout(() => {
      router.push(`/wait/${roomCode}`)
    }, 500)

  } catch (error) {
    logger.error('创建测试房间失败:', error)
    toast.add({
      severity: 'error',
      summary: '创建失败',
      detail: error.response?.data?.message || '创建测试房间失败',
      life: 3000
    })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-6">
    <div class="max-w-2xl mx-auto">
      <!-- 标题 -->
      <div class="mb-6">
        <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-2">
          测试工具
        </h1>
        <p class="text-gray-600 dark:text-gray-400">
          创建测试房间，自动填充虚拟玩家进行测试
        </p>
      </div>

      <!-- 创建测试房间卡片 -->
      <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6 shadow-sm">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          创建测试房间
        </h2>

        <!-- 表单 -->
        <div class="space-y-4">
          <!-- 玩家数量 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              玩家数量 (包括你)
            </label>
            <InputNumber
              v-model="maxPlayers"
              :min="2"
              :max="10"
              showButtons
              class="w-full"
              :disabled="loading"
            />
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
              将创建 {{ maxPlayers - 1 }} 个虚拟玩家
            </p>
          </div>

          <!-- 题目数量 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              题目数量
            </label>
            <InputNumber
              v-model="questionCount"
              :min="1"
              :max="20"
              showButtons
              class="w-full"
              :disabled="loading"
            />
          </div>

          <!-- 提示信息 -->
          <div class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
            <h3 class="text-sm font-medium text-blue-900 dark:text-blue-100 mb-2">
              💡 测试说明
            </h3>
            <ul class="text-sm text-blue-800 dark:text-blue-200 space-y-1">
              <li>• 虚拟玩家命名为 Bot1, Bot2...</li>
              <li>• Bot 会在你提交答案后立即随机提交</li>
              <li>• Bot 默认已准备，你准备后即可开始</li>
              <li>• 测试数据会保存到历史记录</li>
            </ul>
          </div>

          <!-- 按钮 -->
          <div class="flex gap-3">
            <Button
              label="创建测试房间"
              icon="pi pi-plus"
              @click="createTestRoom"
              :loading="loading"
              :disabled="loading"
              class="flex-1"
            />
            <Button
              label="返回"
              icon="pi pi-arrow-left"
              severity="secondary"
              @click="router.push('/admin/questions')"
              :disabled="loading"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
