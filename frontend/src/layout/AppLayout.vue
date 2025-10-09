<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()
const mobileMenuOpen = ref(false)

// 菜单项配置
const menuItems = [
  { label: '主界面', icon: 'pi pi-home', to: '/' },
  { label: '查找房间', icon: 'pi pi-search', to: '/find' },
  { label: '登录', icon: 'pi pi-user', to: '/login' },
  { label: '联系', icon: 'pi pi-phone', to: '/call' },
  { label: '历史', icon: 'pi pi-history', to: '/history' },
  { label: '题库', icon: 'pi pi-book', to: '/table' }
]

const navigateTo = (path) => {
  router.push(path)
  mobileMenuOpen.value = false
}

const isActive = (path) => {
  return route.path === path
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900">
    
    <!-- Topbar -->
    <header class="sticky top-0 z-40 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between h-16">
          
          <!-- Logo -->
          <div class="flex items-center gap-3">
            <h1 class="text-xl font-semibold text-gray-900 dark:text-white">
              答题游戏
            </h1>
          </div>

          <!-- 桌面端导航 -->
          <nav class="hidden md:flex items-center gap-1">
            <button
              v-for="item in menuItems"
              :key="item.to"
              @click="navigateTo(item.to)"
              class="px-3 py-2 rounded-lg text-sm font-medium transition-colors"
              :class="isActive(item.to)
                ? 'bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700/50 hover:text-gray-900 dark:hover:text-white'"
            >
              <i :class="item.icon" class="mr-1.5"></i>
              {{ item.label }}
            </button>
          </nav>

          <!-- 移动端菜单按钮 -->
          <button
            @click="mobileMenuOpen = !mobileMenuOpen"
            class="md:hidden p-2 rounded-lg text-gray-600 dark:text-gray-400 
                   hover:bg-gray-100 dark:hover:bg-gray-700"
          >
            <i :class="mobileMenuOpen ? 'pi pi-times' : 'pi pi-bars'"></i>
          </button>
        </div>
      </div>

      <!-- 移动端菜单 -->
      <transition name="dropdown">
        <div v-if="mobileMenuOpen" 
             class="md:hidden border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
          <div class="px-4 py-3 space-y-1">
            <button
              v-for="item in menuItems"
              :key="item.to"
              @click="navigateTo(item.to)"
              class="w-full text-left px-3 py-2 rounded-lg text-sm font-medium transition-colors"
              :class="isActive(item.to)
                ? 'bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700/50'"
            >
              <i :class="item.icon" class="mr-2"></i>
              {{ item.label }}
            </button>
          </div>
        </div>
      </transition>
    </header>

    <!-- 主内容区 -->
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <router-view></router-view>
    </main>
  </div>
  
  <Toast />
</template>

<style scoped>
.dropdown-enter-active, .dropdown-leave-active {
  transition: all 0.2s ease;
}
.dropdown-enter-from, .dropdown-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>