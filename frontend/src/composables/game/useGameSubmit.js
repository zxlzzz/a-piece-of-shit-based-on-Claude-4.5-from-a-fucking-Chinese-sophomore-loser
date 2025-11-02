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

  return {
    hasSubmitted,
    handleChoose,
    handleAutoSubmit,
    resetSubmitState,
    restoreSubmitState,
    cleanupSubmission,
    getSubmissionKey
  }
}
