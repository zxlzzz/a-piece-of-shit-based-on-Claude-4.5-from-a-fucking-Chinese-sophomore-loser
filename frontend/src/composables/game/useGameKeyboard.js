import { onMounted, onUnmounted } from 'vue'

export function useGameKeyboard(showChat, hasSubmitted, question, isSpectator) {
  const focusChatInput = () => {
    showChat.value = true
    setTimeout(() => {
      const chatInput = 
        document.querySelector('.chat-input') ||
        document.querySelector('input[placeholder*="消息"]') ||
        document.querySelector('input[type="text"]')
      
      if (chatInput) {
        chatInput.focus()
        console.log('✅ 已聚焦到聊天输入框')
      } else {
        console.warn('⚠️ 未找到聊天输入框')
      }
    }, 100)
  }

  const handleKeydown = (e) => {
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
      if (e.key === 'Escape') {
        e.target.blur()
        showChat.value = false
      }
      return
    }
    
    if (e.key === ' ') {
      e.preventDefault()
      focusChatInput()
      return
    }
    
    if (e.key === 'Escape') {
      showChat.value = false
      return
    }
    
    // 🔥 观战者不能提交答案
    if (isSpectator?.value) {
      return
    }

    if (hasSubmitted.value || !question.value) {
      return
    }

    if (question.value.type === 'CHOICE') {
      const keyMap = { '1': 'A', '2': 'B', '3': 'C', '4': 'D' }
      if (keyMap[e.key]) {
        e.preventDefault()
        const event = new CustomEvent('select-option', {
          detail: { key: keyMap[e.key] }
        })
        window.dispatchEvent(event)
        return
      }
    }
    
    if (question.value.type === 'BID') {
      if (/^[0-9]$/.test(e.key)) {
        const numberInput = document.querySelector('.p-inputnumber-input')
        if (numberInput) {
          numberInput.focus()
          
          if (document.activeElement !== numberInput) {
            e.preventDefault()
            setTimeout(() => {
              numberInput.value = e.key
              numberInput.dispatchEvent(new Event('input', { bubbles: true }))
            }, 0)
          }
        }
        return
      }
    }
    
    if (e.key === 'Enter') {
      // 🔥 观战者不能提交答案
      if (isSpectator?.value) {
        return
      }
      e.preventDefault()
      const event = new CustomEvent('submit-answer')
      window.dispatchEvent(event)
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', handleKeydown)
  })

  onUnmounted(() => {
    window.removeEventListener('keydown', handleKeydown)
  })

  return {
    focusChatInput
  }
}