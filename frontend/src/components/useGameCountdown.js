import { ref } from 'vue'

export function useGameCountdown(handleAutoSubmit) {
  const questionStartTime = ref(null)
  const timeLimit = ref(30)
  const countdown = ref(30)
  const countdownTimer = ref(null)

  const resetCountdown = () => {
    clearCountdown()
    startCountdown()
  }

  const startCountdown = () => {
    updateCountdown()
    countdownTimer.value = setInterval(() => {
      updateCountdown()
    }, 100)
  }

  const updateCountdown = () => {
    if (!questionStartTime.value) {
      countdown.value = timeLimit.value
      return
    }
    
    const now = new Date()
    const elapsed = Math.floor((now - questionStartTime.value) / 1000)
    const remaining = Math.max(0, timeLimit.value - elapsed)
    
    countdown.value = remaining
    
    if (remaining === 0) {
      clearCountdown()
      handleAutoSubmit()
    }
  }

  const clearCountdown = () => {
    if (countdownTimer.value) {
      clearInterval(countdownTimer.value)
      countdownTimer.value = null
    }
  }

  return {
    questionStartTime,
    timeLimit,
    countdown,
    resetCountdown,
    clearCountdown
  }
}