/**
 * 玩家相关工具函数（简化版 - demo 用）
 */

export function validateUsername(username) {
  if (!username?.trim()) {
    return { valid: false, message: '请输入用户名' }
  }
  return { valid: true, message: '' }
}

export function validatePassword(password) {
  if (!password || password.length < 6) {
    return { valid: false, message: '密码至少需要6位' }
  }
  return { valid: true, message: '' }
}

export function validatePlayerName(name) {
  if (!name?.trim()) {
    return { valid: false, message: '请输入昵称' }
  }
  return { valid: true, message: '' }
}

export function validateRoomCode(roomCode) {
  if (!roomCode || roomCode.length !== 6) {
    return { valid: false, message: '房间码应为6位字符' }
  }
  return { valid: true, message: '' }
}

export function generatePlayerColor(seed) {
  const colors = [
    '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4',
    '#FFEAA7', '#DDA0DD', '#98D8C8', '#F7DC6F',
    '#BB8FCE', '#85C1E9', '#F8C471', '#82E0AA'
  ]

  if (!seed) return colors[0]

  let hash = 0
  for (let i = 0; i < seed.length; i++) {
    hash = seed.charCodeAt(i) + ((hash << 5) - hash)
  }

  return colors[Math.abs(hash) % colors.length]
}

export default {
  validateUsername,
  validatePassword,
  validatePlayerName,
  validateRoomCode,
  generatePlayerColor
}
