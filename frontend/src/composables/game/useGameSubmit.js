import { logger } from '@/utils/logger'
import { ref } from 'vue'
import { sendSubmit } from '@/websocket/ws'

export function useGameSubmit(roomCode, playerStore, toast, question, room) {
  const hasSubmitted = ref(false)

  const getSubmissionKey = () => {
    if (!room.value || room.value.currentIndex === undefined) {
      return `submission_${roomCode.value}_unknown`
    }
    return `submission_${roomCode.value}_${room.value.currentIndex}`
  }

  const handleChoose = (choice) => {
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

    hasSubmitted.value = true
    const submissionKey = getSubmissionKey()
    localStorage.setItem(submissionKey, 'true')

    try {
      sendSubmit({
        roomCode: roomCode.value,
        playerId: playerStore.playerId,
        choice: choice.toString()
      })

      toast.add({
        severity: 'success',
        summary: '提交成功',
        detail: '已提交答案',
        life: 2000
      })
    } catch (error) {
      logger.error('❌ 提交失败:', error)
      hasSubmitted.value = false
      localStorage.removeItem(submissionKey)

      toast.add({
        severity: 'error',
        summary: '提交失败',
        detail: '网络错误，请重试',
        life: 3000
      })
    }
  }

  const handleAutoSubmit = () => {
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

    hasSubmitted.value = true

    let defaultChoice
    if (question.value.type === 'CHOICE') {
      defaultChoice = question.value.options?.[0]?.key || 'A'
    } else if (question.value.type === 'BID') {
      defaultChoice = question.value.min || 0
    }

    const submissionKey = getSubmissionKey()
    localStorage.setItem(submissionKey, 'true')

    try {
      sendSubmit({
        roomCode: roomCode.value,
        playerId: playerStore.playerId,
        choice: defaultChoice.toString(),
        force: true
      })

      toast.add({
        severity: 'info',
        summary: '自动提交',
        detail: '时间到，已自动提交默认答案',
        life: 3000
      })
    } catch (error) {
      logger.error('❌ 自动提交失败:', error)
      hasSubmitted.value = false
      localStorage.removeItem(submissionKey)
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

    const submissionKey = getSubmissionKey()
    const localStorageSaysSubmitted = localStorage.getItem(submissionKey) === 'true'
    const backendSaysSubmitted = submittedPlayerIds && submittedPlayerIds.includes(playerStore.playerId)

    // 🔥 检测不一致：localStorage说已提交，但后端没有记录
    if (localStorageSaysSubmitted && !backendSaysSubmitted) {
      logger.warn('⚠️ 提交状态不一致：localStorage说已提交但后端无记录，清除本地状态')
      localStorage.removeItem(submissionKey)
      hasSubmitted.value = false

      toast.add({
        severity: 'warn',
        summary: '提交状态已更新',
        detail: '检测到提交未成功，请重新提交',
        life: 3000
      })
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
