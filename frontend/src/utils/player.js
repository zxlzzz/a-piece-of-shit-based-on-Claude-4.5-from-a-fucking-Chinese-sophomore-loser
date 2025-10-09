/**
 * 验证玩家名称
 * @param {string} name - 玩家名称
 * @returns {Object} { valid: boolean, message: string }
 */
export function validatePlayerName(name) {
  if (!name || typeof name !== 'string') {
    return { valid: false, message: '请输入玩家名称' }
  }
  
  if (name.length < 2) {
    return { valid: false, message: '玩家名称至少需要2个字符' }
  }
  
  if (name.length > 20) {
    return { valid: false, message: '玩家名称不能超过20个字符' }
  }
  
  const validPattern = /^[a-zA-Z0-9\u4e00-\u9fa5_-]+$/
  if (!validPattern.test(name)) {
    return { valid: false, message: '玩家名称只能包含字母、数字、中文、下划线和连字符' }
  }
  
  return { valid: true, message: '' }
}

/**
 * 验证房间码
 * @param {string} roomCode - 房间码
 * @returns {Object} { valid: boolean, message: string }
 */
export function validateRoomCode(roomCode) {
  if (!roomCode || typeof roomCode !== 'string') {
    return { valid: false, message: '请输入房间码' }
  }
  
  if (roomCode.length !== 6) {
    return { valid: false, message: '房间码应为6位字符' }
  }
  
  return { valid: true, message: '' }
}

/**
 * 生成玩家头像颜色
 * @param {string} seed - 种子字符串
 * @returns {string} CSS颜色值
 */
export function generatePlayerColor(seed) {
  const colors = [
    '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', 
    '#FFEAA7', '#DDA0DD', '#98D8C8', '#F7DC6F',
    '#BB8FCE', '#85C1E9', '#F8C471', '#82E0AA'
  ]
  
  // 🔥 防御性检查
  if (!seed || typeof seed !== 'string') {
    console.warn('⚠️ generatePlayerColor: seed 无效，使用默认颜色', seed)
    return colors[0] // 返回默认颜色
  }
  
  let hash = 0
  for (let i = 0; i < seed.length; i++) {
    hash = seed.charCodeAt(i) + ((hash << 5) - hash)
  }
  
  const index = Math.abs(hash) % colors.length
  return colors[index]
}


/**
 * 生成唯一的玩家 ID（UUID v4 简化版）
 * @returns {string} 格式：xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
 */
export function generatePlayerId() {
  // 简化版 UUID v4
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

export default {
  validatePlayerName,
  validateRoomCode,
  generatePlayerColor,
  generatePlayerId
}