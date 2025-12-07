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
    const currentIndex = room.value?.currentIndex

    try {
      const response = await submitAnswer(roomCode.value, playerStore.playerId, choice.toString())

      const returnedIndex = response.data?.currentIndex
      if (returnedIndex === currentIndex) {
        const submissionKey = getSubmissionKey()
        localStorage.setItem(submissionKey, 'true')
      } else {
        hasSubmitted.value = false
        logger.info('题目已推进，重置提交状态', { currentIndex, returnedIndex })
      }

      toast.add({
        severity: 'success',
        summary: '提交成功',
        detail: '已提交答案',
        life: 2000
      })
    } catch (error) {
      logger.error('提交失败:', error)
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
    if (hasSubmitted.value) {
      return
    }

    if (!question.value || !question.value.id) {
      logger.error('题目不存在，无法自动提交')
      return
    }

    hasSubmitted.value = true
    const currentIndex = room.value?.currentIndex

    let defaultChoice
    if (question.value.type === 'CHOICE') {
      defaultChoice = question.value.options?.[0]?.key || 'A'
    } else if (question.value.type === 'BID') {
      defaultChoice = question.value.min || 0
    }

    try {
      const response = await submitAnswer(roomCode.value, playerStore.playerId, defaultChoice.toString(), true)

      const returnedIndex = response.data?.currentIndex
      if (returnedIndex === currentIndex) {
        const submissionKey = getSubmissionKey()
        localStorage.setItem(submissionKey, 'true')
      } else {
        hasSubmitted.value = false
        logger.info('自动提交时题目已推进，重置提交状态', { currentIndex, returnedIndex })
      }

      toast.add({
        severity: 'info',
        summary: '自动提交',
        detail: '时间到，已自动提交默认答案',
        life: 3000
      })
    } catch (error) {
      logger.error('自动提交失败:', error)
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

  const verifySubmissionState = (submittedPlayerIds) => {
    if (!room.value || room.value.currentIndex === undefined || room.value.currentIndex < 0) {
      return
    }

    if (!window._gameViewLoadTime) {
      window._gameViewLoadTime = Date.now()
    }
    const timeSinceLoad = Date.now() - window._gameViewLoadTime
    if (timeSinceLoad < 2000) {
      logger.debug('页面刚加载，跳过提交状态验证')
      return
    }

    const submissionKey = getSubmissionKey()
    const localStorageSaysSubmitted = localStorage.getItem(submissionKey) === 'true'
    const backendSaysSubmitted = submittedPlayerIds && submittedPlayerIds.includes(playerStore.playerId)

    if (localStorageSaysSubmitted && !backendSaysSubmitted) {
      logger.warn('提交状态不一致，清除本地状态', {
        submissionKey,
        currentIndex: room.value?.currentIndex,
        submittedPlayerIds
      })
      localStorage.removeItem(submissionKey)
      hasSubmitted.value = false
    }
    else if (!localStorageSaysSubmitted && backendSaysSubmitted) {
      logger.info('同步后端提交状态', {
        submissionKey,
        currentIndex: room.value?.currentIndex
      })
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
    verifySubmissionState
  }
}
