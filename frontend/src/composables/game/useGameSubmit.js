import { logger } from '@/utils/logger'
import { ref } from 'vue'
import { submitAnswer } from '@/api'

export function useGameSubmit(roomCode, playerStore, toast, question, room) {
  const hasSubmitted = ref(false)

  const getSubmissionKey = () => {
    if (!room.value || room.value.currentIndex === undefined) {
      return `submission_${roomCode.value}_unknown`
    }
    return `submission_${roomCode.value}_${room.value.currentIndex}`
  }

  const handleChoose = async (choice) => {
    // 🔥 观战者不能提交答案
    if (playerStore.isSpectator) {
      toast.add({
        severity: 'warn',
        summary: '观战模式',
        detail: '观战者不能提交答案',
        life: 2000
      })
      return
    }

    if (hasSubmitted.value) {
      toast.add({
        severity: 'warn',
        summary: '提示',
        detail: '您已经提交过答案了',
        life: 2000
      })
      return
    }

    if (!question.value || !question.value.id) {
      toast.add({
        severity: 'error',
        summary: '错误',
        detail: '题目数据异常，无法提交',
        life: 3000
      })
      return
    }

    // 🔥 修复：先禁用提交按钮，防止重复点击
    hasSubmitted.value = true

    // 🔥 保存当前题目index，用于API返回后验证（避免测试房间中题目快速推进导致的状态不一致）
    const currentIndex = room.value?.currentIndex

    try {
      // 🔥 修复：先调用API，成功后再设置localStorage
      // 避免页面刷新时localStorage已设置但API未完成的情况
      const response = await submitAnswer(roomCode.value, playerStore.playerId, choice.toString())

      // 🔥 API成功后，使用API返回的currentIndex判断题目是否已推进
      // 如果API返回的index与提交时不同，说明题目已推进（测试房间中Bot立即触发推进）
      const returnedIndex = response.data?.currentIndex
      if (returnedIndex === currentIndex) {
        const submissionKey = getSubmissionKey()
        localStorage.setItem(submissionKey, 'true')
      } else {
        // 🔥 修复：题目已推进，重置hasSubmitted，因为这已经是新题了
        hasSubmitted.value = false
        logger.info('⚠️ 题目已推进，重置提交状态 (测试房间快速推进)', { currentIndex, returnedIndex })
      }

      // 🔥 房间状态更新会通过WebSocket自动推送

      toast.add({
        severity: 'success',
        summary: '提交成功',
        detail: '已提交答案',
        life: 2000
      })
    } catch (error) {
      logger.error('❌ 提交失败:', error)
      // 🔥 API失败，重置提交状态（不需要清理localStorage，因为还没设置）
      hasSubmitted.value = false

      toast.add({
        severity: 'error',
        summary: '提交失败',
        detail: error.response?.data?.message || '网络错误，请重试',
        life: 3000
      })
    }
  }

  const handleAutoSubmit = async () => {
    // 🔥 观战者不需要自动提交
    if (playerStore.isSpectator) {
      return
    }

    if (hasSubmitted.value) {
      return
    }

    if (!question.value || !question.value.id) {
      logger.error('❌ 题目不存在，无法自动提交')
      return
    }

    // 🔥 修复：先禁用提交，防止用户在自动提交期间手动提交
    hasSubmitted.value = true

    // 🔥 保存当前题目index，用于API返回后验证
    const currentIndex = room.value?.currentIndex

    let defaultChoice
    if (question.value.type === 'CHOICE') {
      defaultChoice = question.value.options?.[0]?.key || 'A'
    } else if (question.value.type === 'BID') {
      defaultChoice = question.value.min || 0
    }

    try {
      // 🔥 修复：先调用API，成功后再设置localStorage
      const response = await submitAnswer(roomCode.value, playerStore.playerId, defaultChoice.toString(), true)

      // 🔥 API成功后，使用API返回的currentIndex判断题目是否已推进
      // timeout触发fillDefaultAnswers后可能立即推进题目
      const returnedIndex = response.data?.currentIndex
      if (returnedIndex === currentIndex) {
        const submissionKey = getSubmissionKey()
        localStorage.setItem(submissionKey, 'true')
      } else {
        // 🔥 修复：题目已推进，重置hasSubmitted
        hasSubmitted.value = false
        logger.info('⚠️ 自动提交时题目已推进，重置提交状态', { currentIndex, returnedIndex })
      }

      // 🔥 房间状态更新会通过WebSocket自动推送

      toast.add({
        severity: 'info',
        summary: '自动提交',
        detail: '时间到，已自动提交默认答案',
        life: 3000
      })
    } catch (error) {
      logger.error('❌ 自动提交失败:', error)
      // 🔥 API失败，重置提交状态
      hasSubmitted.value = false
    }
  }

  const resetSubmitState = () => {
    hasSubmitted.value = false
  }

  const restoreSubmitState = () => {
    const submissionKey = getSubmissionKey()
    const savedSubmission = localStorage.getItem(submissionKey)
    if (savedSubmission === 'true') {
      hasSubmitted.value = true
    }
  }

  const cleanupSubmission = () => {
    const submissionKey = getSubmissionKey()
    localStorage.removeItem(submissionKey)
  }

  /**
   * 🔥 P1-1: 验证提交状态（对比 localStorage 和后端状态）
   * 如果 localStorage 说已提交但后端没有记录，则清除 localStorage 并重置状态
   * @param {Array<string>} submittedPlayerIds - 后端返回的已提交玩家ID列表
   */
  const verifySubmissionState = (submittedPlayerIds) => {
    // 观战者不需要验证
    if (playerStore.isSpectator) {
      return
    }

    // 🔥 修复：确保 room 数据有效，避免在初始化或题目切换过程中误判
    if (!room.value || room.value.currentIndex === undefined || room.value.currentIndex < 0) {
      return
    }

    const submissionKey = getSubmissionKey()
    const localStorageSaysSubmitted = localStorage.getItem(submissionKey) === 'true'
    const backendSaysSubmitted = submittedPlayerIds && submittedPlayerIds.includes(playerStore.playerId)

    // 🔥 检测不一致：localStorage说已提交，但后端没有记录
    if (localStorageSaysSubmitted && !backendSaysSubmitted) {
      logger.warn('⚠️ 提交状态不一致：localStorage说已提交但后端无记录，清除本地状态', {
        submissionKey,
        currentIndex: room.value?.currentIndex,
        submittedPlayerIds
      })
      localStorage.removeItem(submissionKey)
      hasSubmitted.value = false

      // 🔥 修复：不再弹toast，因为这可能是正常的题目推进导致的
      // toast.add({
      //   severity: 'warn',
      //   summary: '提交状态已更新',
      //   detail: '检测到提交未成功，请重新提交',
      //   life: 3000
      // })
    }
    // 🔥 检测不一致：localStorage说未提交，但后端有记录
    else if (!localStorageSaysSubmitted && backendSaysSubmitted) {
      logger.info('✅ 提交状态不一致：后端有记录但localStorage无记录，同步状态')
      localStorage.setItem(submissionKey, 'true')
      hasSubmitted.value = true
    }
  }

  return {
    hasSubmitted,
    handleChoose,
    handleAutoSubmit,
    resetSubmitState,
    restoreSubmitState,
    cleanupSubmission,
    getSubmissionKey,
    verifySubmissionState  // 🔥 P1-1: 新增验证函数
  }
}
