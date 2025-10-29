<script setup>
import { logger } from '@/utils/logger'
import axios from "axios";
import { useToast } from 'primevue/usetoast'
import { onMounted, ref, watch } from 'vue'

const toast = useToast()
const questions = ref([])
const loading = ref(false)
const showForm = ref(false)
const editingQuestion = ref(null)

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
    logger.error('❌ Request Error:', error);
    return Promise.reject(error);
  }
);

// ============ 响应拦截器 ============
api.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    logger.error('❌ API Error:', error.response?.data || error.message);
    
    // 🔥 处理 401 未授权错误
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('playerId');
      localStorage.removeItem('playerName');
      window.location.href = '/login';
      return Promise.reject(error);
    }
    
    // 🔥 检查是否需要静默处理（配置中设置了 silentError: true）
    const silentError = error.config?.silentError;
    
    // 🔥 过滤不需要全局提示的错误
    const shouldShowToast = !silentError && !isIgnorableError(error);
    
    // 只有需要提示的错误才触发全局事件
    if (shouldShowToast) {
      window.dispatchEvent(new CustomEvent('api-error', {
        detail: {
          message: error.response?.data?.message || error.message || '请求失败',
          status: error.response?.status,
          url: error.config?.url
        }
      }));
    }
    
    return Promise.reject(error);
  }
);

// 🔥 判断是否是可忽略的错误（不需要弹窗提示）
function isIgnorableError(error) {
  const status = error.response?.status;
  const message = error.response?.data?.message || '';
  const url = error.config?.url || '';
  
  // 房间不存在（404）- 静默处理
  if (status === 404 && url.includes('/rooms/')) {
    return true;
  }
  
  // 房间已结束/不存在等业务错误 - 静默处理
  if (message.includes('房间不存在') ||
      message.includes('房间已结束') ||
      message.includes('房间已过期')) {
    return true;
  }
  
  // 重复提交等正常业务逻辑 - 静默处理
  if (message.includes('已经提交') ||
      message.includes('已提交')) {
    return true;
  }
  
  return false;
}

// 表单数据
const form = ref({
  type: 'CHOICE',
  text: '',
  strategyId: '',
  minPlayers: 2,
  maxPlayers: 2,
  defaultCHOICE: '',
  
  // CHOICE 专用
  options: [
    { key: 'A', text: '' },
    { key: 'B', text: '' }
  ],
  
  // BID 专用
  min: 0,
  max: 100,
  step: 1,
  
  // 序列配置（复选框控制）
  isSequence: false,
  sequenceGroupId: '',
  sequenceOrder: 1,
  totalSequenceCount: 1,
  prerequisiteQuestionIds: '',
  
  // 重复配置（复选框控制）
  isRepeatable: false,
  repeatTimes: 1,
  repeatInterval: null,
  repeatGroupId: ''
})

// 监听类型切换，清空对应字段
watch(() => form.value.type, (newType) => {
  if (newType === 'CHOICE') {
    form.value.min = null
    form.value.max = null
    form.value.step = null
  } else if (newType === 'BID') {
    form.value.options = []
  }
})

// 监听序列复选框
watch(() => form.value.isSequence, (isChecked) => {
  if (!isChecked) {
    form.value.sequenceGroupId = ''
    form.value.sequenceOrder = 1
    form.value.totalSequenceCount = 1
    form.value.prerequisiteQuestionIds = ''
  }
})

// 监听重复复选框
watch(() => form.value.isRepeatable, (isChecked) => {
  if (!isChecked) {
    form.value.repeatTimes = 1
    form.value.repeatInterval = null
    form.value.repeatGroupId = ''
  }
})

// 加载题目列表
const loadQuestions = async () => {
  loading.value = true
  try {
    const response = await api.get('/admin/questions')
    questions.value = response.data.content || response.data
    toast.add({
      severity: 'success',
      summary: '加载成功',
      detail: `共 ${questions.value.length} 道题目`,
      life: 2000
    })
  } catch (error) {
    logger.error('加载失败', error)
    toast.add({
      severity: 'error',
      summary: '加载失败',
      detail: error.message,
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

// 打开新建表单
const openCreateForm = () => {
  editingQuestion.value = null
  form.value = {
    type: 'CHOICE',
    text: '',
    strategyId: '',
    minPlayers: 2,
    maxPlayers: 2,
    defaultCHOICE: '',
    options: [
      { key: 'A', text: '' },
      { key: 'B', text: '' }
    ],
    min: 0,
    max: 100,
    step: 1,
    isSequence: false,
    sequenceGroupId: '',
    sequenceOrder: 1,
    totalSequenceCount: 1,
    prerequisiteQuestionIds: '',
    isRepeatable: false,
    repeatTimes: 1,
    repeatInterval: null,
    repeatGroupId: ''
  }
  showForm.value = true
}

// 打开编辑表单
const openEditForm = (question) => {
  editingQuestion.value = question
  
  // 🔥 解析 options（支持多种格式）
  let parsedOptions = []
  
  // 情况1：后端直接返回 options 数组
  if (Array.isArray(question.options) && question.options.length > 0) {
    parsedOptions = question.options
  }
  // 情况2：后端返回 optionsJson 字符串
  else if (question.optionsJson) {
    try {
      parsedOptions = JSON.parse(question.optionsJson)
    } catch (e) {
      logger.error('❌ 解析 optionsJson 失败:', e)
      parsedOptions = []
    }
  }
  // 情况3：检查是否有序列化的字符串字段
  else if (typeof question.options === 'string') {
    try {
      parsedOptions = JSON.parse(question.options)
    } catch (e) {
      logger.error('❌ 解析字符串 options 失败:', e)
      parsedOptions = []
    }
  }
  
  // 情况4：CHOICE 类型但没有 options，给个默认值
  if (parsedOptions.length === 0 && question.type === 'CHOICE') {
    parsedOptions = [{ key: 'A', text: '' }]
  }
  
  // 🔥 填充表单
  form.value = {
    // 基础信息
    type: question.type || 'CHOICE',
    text: question.text || '',
    strategyId: question.strategyId || '',
    minPlayers: question.minPlayers ?? 2,
    maxPlayers: question.maxPlayers ?? 2,
    defaultCHOICE: question.defaultCHOICE || '',
    
    // CHOICE 专用
    options: parsedOptions,
    
    // BID 专用
    min: question.min ?? 0,
    max: question.max ?? 100,
    step: question.step ?? 1,
    
    // 序列配置
    isSequence: !!(question.sequenceGroupId),
    sequenceGroupId: question.sequenceGroupId || '',
    sequenceOrder: question.sequenceOrder ?? 1,
    totalSequenceCount: question.totalSequenceCount ?? 1,
    prerequisiteQuestionIds: question.prerequisiteQuestionIds || '',
    
    // 重复配置
    isRepeatable: !!question.isRepeatable,
    repeatTimes: question.repeatTimes ?? 1,
    repeatInterval: question.repeatInterval ?? null,
    repeatGroupId: question.repeatGroupId || ''
  }
  
  
  showForm.value = true
}

// 添加选项
const addOption = () => {
  const nextKey = String.fromCharCode(65 + form.value.options.length)
  form.value.options.push({ key: nextKey, text: '' })
}

// 删除选项
const removeOption = (index) => {
  if (form.value.options.length > 1) {
    form.value.options.splice(index, 1)
  }
}

// 提交表单
const submitForm = async () => {
  // 验证必填项
  if (!form.value.text || !form.value.strategyId) {
    toast.add({
      severity: 'warn',
      summary: '请填写必填项',
      detail: '题目文本和策略ID不能为空',
      life: 3000
    })
    return
  }
  
  // 🔥 验证 CHOICE 类型的选项
  if (form.value.type === 'CHOICE') {
    if (!form.value.options || form.value.options.length === 0) {
      toast.add({
        severity: 'warn',
        summary: '请添加选项',
        detail: '选择题至少需要一个选项',
        life: 3000
      })
      return
    }
    
    // 🔥 过滤掉空选项
    form.value.options = form.value.options.filter(opt => opt.key && opt.text)
    
    if (form.value.options.length === 0) {
      toast.add({
        severity: 'warn',
        summary: '选项内容不能为空',
        detail: '请填写选项的 key 和 text',
        life: 3000
      })
      return
    }
  }
  
  // 🔥 验证 BID 类型的范围
  if (form.value.type === 'BID') {
    if (form.value.min == null || form.value.max == null) {
      toast.add({
        severity: 'warn',
        summary: '请设置范围',
        detail: '竞价题需要设置最小值和最大值',
        life: 3000
      })
      return
    }
  }
  
  // 构建提交数据
  const payload = {
    type: form.value.type,
    text: form.value.text,
    strategyId: form.value.strategyId,
    minPlayers: form.value.minPlayers,
    maxPlayers: form.value.maxPlayers,
    defaultCHOICE: form.value.defaultCHOICE,
    
    // CHOICE 专用
    options: form.value.type === 'CHOICE' && form.value.options.length > 0 
      ? form.value.options 
      : null,
    
    // BID 专用
    min: form.value.type === 'BID' ? form.value.min : null,
    max: form.value.type === 'BID' ? form.value.max : null,
    step: form.value.type === 'BID' ? form.value.step : null,
    
    // 序列配置
    sequenceGroupId: form.value.isSequence ? form.value.sequenceGroupId : null,
    sequenceOrder: form.value.isSequence ? form.value.sequenceOrder : null,
    totalSequenceCount: form.value.isSequence ? form.value.totalSequenceCount : null,
    prerequisiteQuestionIds: form.value.isSequence ? form.value.prerequisiteQuestionIds : null,
    
    // 重复配置
    isRepeatable: form.value.isRepeatable,
    repeatTimes: form.value.isRepeatable ? form.value.repeatTimes : null,
    repeatInterval: form.value.isRepeatable ? form.value.repeatInterval : null,
    repeatGroupId: form.value.isRepeatable ? form.value.repeatGroupId : null
  }

  loading.value = true
  try {
    if (editingQuestion.value) {
      await api.put(`/admin/questions/${editingQuestion.value.id}`, payload)
    } else {
      await api.post('/admin/questions', payload)
    }
    
    toast.add({
      severity: 'success',
      summary: editingQuestion.value ? '更新成功' : '创建成功',
      life: 2000
    })
    
    showForm.value = false
    loadQuestions()
  } catch (error) {
    logger.error('操作失败', error)
    toast.add({
      severity: 'error',
      summary: '操作失败',
      detail: error.response?.data?.message || error.message,
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

// 删除题目
const deleteQuestion = async (id) => {
  if (!confirm('确定删除这道题目吗？')) return
  
  loading.value = true
  try {
    await api.delete(`/admin/questions/${id}`)
    
    toast.add({
      severity: 'success',
      summary: '删除成功',
      life: 2000
    })
    loadQuestions()
  } catch (error) {
    logger.error('删除失败', error)
    toast.add({
      severity: 'error',
      summary: '删除失败',
      detail: error.response?.data?.message || error.message,
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

// 导出JSON
const exportQuestions = async () => {
  loading.value = true
  try {
    const response = await api.get('/admin/questions/export')
    const data = response.data
    
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `questions_${Date.now()}.json`
    a.click()
    URL.revokeObjectURL(url)
    
    toast.add({
      severity: 'success',
      summary: '导出成功',
      detail: `已导出 ${data.length} 道题目`,
      life: 2000
    })
  } catch (error) {
    logger.error('导出失败', error)
    toast.add({
      severity: 'error',
      summary: '导出失败',
      detail: error.response?.data?.message || error.message,
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

// 清空所有题目
const clearAll = async () => {
  if (!confirm('确定清空所有题目吗？此操作不可恢复！')) return
  
  loading.value = true
  try {
    await api.delete('/admin/questions/all')
    
    toast.add({
      severity: 'success',
      summary: '已清空',
      life: 2000
    })
    loadQuestions()
  } catch (error) {
    logger.error('清空失败', error)
    toast.add({
      severity: 'error',
      summary: '清空失败',
      detail: error.response?.data?.message || error.message,
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadQuestions()
})
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-6">
    <div class="max-w-7xl mx-auto">
      
      <!-- 头部 -->
      <div class="mb-6">
        <h1 class="text-3xl font-bold text-gray-900 dark:text-white mb-2">题库管理</h1>
        <p class="text-gray-600 dark:text-gray-400">管理所有答题题目</p>
      </div>

      <!-- 操作栏 -->
      <div class="flex gap-3 mb-6">
        <button
          @click="openCreateForm"
          class="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium"
        >
          <i class="pi pi-plus mr-2"></i>新建题目
        </button>
        
        <button
          @click="loadQuestions"
          :disabled="loading"
          class="px-4 py-2 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 
                 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700"
        >
          <i class="pi pi-refresh mr-2" :class="{ 'pi-spin': loading }"></i>刷新
        </button>
        
        <button
          @click="exportQuestions"
          :disabled="loading"
          class="px-4 py-2 bg-green-600 hover:bg-green-700 text-white rounded-lg"
        >
          <i class="pi pi-download mr-2"></i>导出JSON
        </button>
        
        <button
          @click="clearAll"
          :disabled="loading"
          class="ml-auto px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg"
        >
          <i class="pi pi-trash mr-2"></i>清空所有
        </button>
      </div>

      <!-- 题目列表 -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700">
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50 dark:bg-gray-700 border-b border-gray-200 dark:border-gray-600">
              <tr>
                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700 dark:text-gray-300">ID</th>
                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700 dark:text-gray-300">类型</th>
                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700 dark:text-gray-300">题目</th>
                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700 dark:text-gray-300">策略ID</th>
                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700 dark:text-gray-300">人数</th>
                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700 dark:text-gray-300">标签</th>
                <th class="px-4 py-3 text-right text-sm font-semibold text-gray-700 dark:text-gray-300">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
              <tr v-for="q in questions" :key="q.id" class="hover:bg-gray-50 dark:hover:bg-gray-700/50">
                <td class="px-4 py-3 text-sm text-gray-900 dark:text-white">{{ q.id }}</td>
                <td class="px-4 py-3 text-sm">
                  <span class="px-2 py-1 rounded text-xs font-medium"
                        :class="q.type === 'CHOICE' 
                          ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' 
                          : 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400'">
                    {{ q.type }}
                  </span>
                </td>
                <td class="px-4 py-3 text-sm text-gray-900 dark:text-white max-w-md truncate">
                  {{ q.text }}
                </td>
                <td class="px-4 py-3 text-sm text-gray-600 dark:text-gray-400">{{ q.strategyId }}</td>
                <td class="px-4 py-3 text-sm text-gray-600 dark:text-gray-400">
                  {{ q.minPlayers }}-{{ q.maxPlayers }}
                </td>
                <td class="px-4 py-3 text-sm">
                  <div class="flex gap-1">
                    <span v-if="q.isRepeatable" 
                          class="px-2 py-0.5 text-xs rounded bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400"
                          :title="`重复${q.repeatTimes}次`">
                      🔁 x{{ q.repeatTimes }}
                    </span>
                    <span v-if="q.sequenceGroupId" 
                          class="px-2 py-0.5 text-xs rounded bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400"
                          :title="`序列 ${q.sequenceOrder}/${q.totalSequenceCount}`">
                      📋 {{ q.sequenceOrder }}/{{ q.totalSequenceCount }}
                    </span>
                  </div>
                </td>
                <td class="px-4 py-3 text-right">
                  <button
                    @click="openEditForm(q)"
                    class="text-blue-600 hover:text-blue-700 dark:text-blue-400 mr-3"
                  >
                    <i class="pi pi-pencil"></i>
                  </button>
                  <button
                    @click="deleteQuestion(q.id)"
                    class="text-red-600 hover:text-red-700 dark:text-red-400"
                  >
                    <i class="pi pi-trash"></i>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        
        <div v-if="questions.length === 0" class="text-center py-12 text-gray-500 dark:text-gray-400">
          暂无题目，点击"新建题目"开始添加
        </div>
      </div>

      <!-- 🔥 新建/编辑表单（重构后） -->
      <div v-if="showForm" 
           class="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50"
           @click.self="showForm = false">
        <div class="bg-white dark:bg-gray-800 rounded-lg shadow-xl max-w-3xl w-full max-h-[90vh] overflow-y-auto p-6">
          
          <h2 class="text-2xl font-bold text-gray-900 dark:text-white mb-6">
            {{ editingQuestion ? '编辑题目' : '新建题目' }}
          </h2>

          <div class="space-y-6">
            
            <!-- 🔥 题目类型切换（按钮） -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                题目类型 *
              </label>
              <div class="flex gap-2 p-1 bg-gray-100 dark:bg-gray-700 rounded-lg w-fit">
                <button
                  type="button"
                  @click="form.type = 'CHOICE'"
                  class="px-6 py-2 rounded-md font-medium transition-all"
                  :class="form.type === 'CHOICE'
                    ? 'bg-white dark:bg-gray-600 text-blue-600 dark:text-blue-400 shadow-sm'
                    : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white'"
                >
                  <i></i>选择题
                </button>
                <button
                  type="button"
                  @click="form.type = 'BID'"
                  class="px-6 py-2 rounded-md font-medium transition-all"
                  :class="form.type === 'BID'
                    ? 'bg-white dark:bg-gray-600 text-purple-600 dark:text-purple-400 shadow-sm'
                    : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white'"
                >
                  <i></i>竞价题
                </button>
              </div>
            </div>

            <!-- 🔥 通用信息 -->
            <div class="border border-gray-200 dark:border-gray-700 rounded-lg p-4">
              <h3 class="font-semibold text-gray-900 dark:text-white mb-4">基础信息</h3>
              
              <div class="space-y-4">
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    题目文本 *
                  </label>
                  <textarea v-model="form.text" 
                            rows="3"
                            placeholder="输入题目描述"
                            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg
                                   bg-white dark:bg-gray-700 text-gray-900 dark:text-white"></textarea>
                </div>

                <div class="grid grid-cols-3 gap-4">
                  <div>
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                      策略ID *
                    </label>
                    <input v-model="form.strategyId" 
                           placeholder="例如: Q001"
                           class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg
                                  bg-white dark:bg-gray-700 text-gray-900 dark:text-white">
                  </div>
                  
                  <div>
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                      最小玩家数
                    </label>
                    <input v-model.number="form.minPlayers" type="number" 
                           class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg
                                  bg-white dark:bg-gray-700 text-gray-900 dark:text-white">
                  </div>
                  
                  <div>
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                      最大玩家数
                    </label>
                    <input v-model.number="form.maxPlayers" type="number"
                           class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg
                                  bg-white dark:bg-gray-700 text-gray-900 dark:text-white">
                  </div>
                </div>

                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    默认选择（可选）
                  </label>
                  <input v-model="form.defaultCHOICE"
                         placeholder="默认答案"
                         class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg
                                bg-white dark:bg-gray-700 text-gray-900 dark:text-white">
                </div>
              </div>
            </div>

            <!-- 🔥 CHOICE 类型的选项 -->
            <div v-if="form.type === 'CHOICE'" 
                 class="border border-blue-200 dark:border-blue-800 rounded-lg p-4 bg-blue-50/50 dark:bg-blue-900/10">
              <h3 class="font-semibold text-blue-700 dark:text-blue-400 mb-4">
                <i class="pi pi-list mr-2"></i>选项设置
              </h3>
              
              <div class="space-y-2">
                <div v-for="(opt, idx) in form.options" :key="idx" class="flex gap-2">
                  <input v-model="opt.key" 
                         placeholder="A"
                         class="w-16 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg
                                bg-white dark:bg-gray-700 text-gray-900 dark:text-white">
                  <input v-model="opt.text" 
                         placeholder="选项文本"
                         class="flex-1 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg
                                bg-white dark:bg-gray-700 text-gray-900 dark:text-white">
                  <button @click="removeOption(idx)"
                          :disabled="form.options.length <= 1"
                          class="px-3 py-2 text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg
                                 disabled:opacity-50 disabled:cursor-not-allowed">
                    <i class="pi pi-trash"></i>
                  </button>
                </div>
              </div>
              
              <button @click="addOption"
                      class="mt-3 px-4 py-2 text-sm text-blue-600 hover:bg-blue-100 dark:hover:bg-blue-900/20 
                             rounded-lg border border-blue-300 dark:border-blue-700">
                <i class="pi pi-plus mr-1"></i>添加选项
              </button>
            </div>

            <!-- 🔥 BID 类型的范围 -->
            <div v-if="form.type === 'BID'" 
                 class="border border-purple-200 dark:border-purple-800 rounded-lg p-4 bg-purple-50/50 dark:bg-purple-900/10">
              <h3 class="font-semibold text-purple-700 dark:text-purple-400 mb-4">
                <i></i>竞价范围
              </h3>
              
              <div class="grid grid-cols-3 gap-4">
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    最小值
                  </label>
                  <input v-model.number="form.min" type="number"
                         class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg
                                bg-white dark:bg-gray-700 text-gray-900 dark:text-white">
                </div>
                
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    最大值
                  </label>
                  <input v-model.number="form.max" type="number"
                         class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg
                                bg-white dark:bg-gray-700 text-gray-900 dark:text-white">
                </div>
                
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    步长
                  </label>
                  <input v-model.number="form.step" type="number"
                         class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg
                                bg-white dark:bg-gray-700 text-gray-900 dark:text-white">
                </div>
              </div>
            </div>

            <!-- 🔥 序列题配置（复选框） -->
            <div class="border border-gray-200 dark:border-gray-700 rounded-lg p-4">
              <div class="flex items-center gap-2 mb-3">
                <input 
                  v-model="form.isSequence" 
                  type="checkbox" 
                  id="isSequence"
                  class="w-4 h-4 text-blue-600 rounded"
                >
                <label for="isSequence" class="font-semibold text-gray-900 dark:text-white cursor-pointer">
                  📋 序列题目（连续多道题）
                </label>
              </div>
              
              <div v-if="form.isSequence" class="mt-3 pl-6 border-l-4 border-blue-500 space-y-3">
                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-sm text-gray-700 dark:text-gray-300 mb-1">
                      序列组ID *
                    </label>
                    <input 
                      v-model="form.sequenceGroupId" 
                      placeholder="例如: seq_auction"
                      class="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg
                             bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                    >
                  </div>
                  
                  <div>
                    <label class="block text-sm text-gray-700 dark:text-gray-300 mb-1">
                      当前顺序 *
                    </label>
                    <input 
                      v-model.number="form.sequenceOrder" 
                      type="number" 
                      placeholder="1, 2, 3..."
                      class="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg
                             bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                    >
                  </div>
                </div>
                
                <div>
                  <label class="block text-sm text-gray-700 dark:text-gray-300 mb-1">
                    总题数 *
                  </label>
                  <input 
                    v-model.number="form.totalSequenceCount" 
                    type="number" 
                    placeholder="例如: 3"
                    class="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg
                           bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                  >
                </div>
                
                <div>
                  <label class="block text-sm text-gray-700 dark:text-gray-300 mb-1">
                    前置题目ID（可选）
                  </label>
                  <input 
                    v-model="form.prerequisiteQuestionIds" 
                    placeholder="多个用逗号分隔，例如: 1,2,3"
                    class="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg
                           bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                  >
                </div>
                
                <p class="text-xs text-gray-500 dark:text-gray-400">
                  💡 序列题示例：拍卖序列3道题，sequenceGroupId相同，顺序为1、2、3
                </p>
              </div>
            </div>

            <!-- 🔥 重复题配置（复选框） -->
            <div class="border border-gray-200 dark:border-gray-700 rounded-lg p-4">
              <div class="flex items-center gap-2 mb-3">
                <input 
                  v-model="form.isRepeatable" 
                  type="checkbox" 
                  id="isRepeatable"
                  class="w-4 h-4 text-orange-600 rounded"
                >
                <label for="isRepeatable" class="font-semibold text-gray-900 dark:text-white cursor-pointer">
                  🔁 重复题目（同一题玩多次）
                </label>
              </div>
              
              <div v-if="form.isRepeatable" class="mt-3 pl-6 border-l-4 border-orange-500 space-y-3">
                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-sm text-gray-700 dark:text-gray-300 mb-1">
                      重复次数 *
                    </label>
                    <input 
                      v-model.number="form.repeatTimes" 
                      type="number" 
                      placeholder="例如: 4"
                      class="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg
                             bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                    >
                  </div>
                  
                  <div>
                    <label class="block text-sm text-gray-700 dark:text-gray-300 mb-1">
                      重复间隔（题目数，可选）
                    </label>
                    <input 
                      v-model.number="form.repeatInterval" 
                      type="number" 
                      placeholder="例如: 2"
                      class="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg
                             bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                    >
                  </div>
                </div>
                
                <div>
                  <label class="block text-sm text-gray-700 dark:text-gray-300 mb-1">
                    重复组ID（可选）
                  </label>
                  <input 
                    v-model="form.repeatGroupId" 
                    placeholder="例如: repeat_planting"
                    class="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg
                           bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                  >
                </div>
                
                <p class="text-xs text-gray-500 dark:text-gray-400">
                  💡 重复题示例：种植题玩4轮，每轮都是同一道题
                </p>
              </div>
            </div>

          </div>

          <!-- 按钮 -->
          <div class="flex gap-3 mt-6 pt-6 border-t border-gray-200 dark:border-gray-700">
            <button @click="submitForm"
                    :disabled="loading"
                    class="flex-1 px-4 py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium
                           disabled:opacity-50 disabled:cursor-not-allowed">
              <i v-if="loading" class="pi pi-spin pi-spinner mr-2"></i>
              {{ editingQuestion ? '更新题目' : '创建题目' }}
            </button>
            <button @click="showForm = false"
                    class="px-6 py-3 bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 
                           rounded-lg font-medium hover:bg-gray-300 dark:hover:bg-gray-600">
              取消
            </button>
          </div>

        </div>
      </div>

    </div>
  </div>
</template>