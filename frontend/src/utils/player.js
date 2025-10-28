import { logger } from '@/utils/logger'
/**
 * 验证用户名（登录用）
 * @param {string} username - 用户名
 * @returns {Object} { valid: boolean, message: string }
 */
export function validateUsername(username) {
  if (!username || typeof username !== 'string') {
    return { valid: false, message: '请输入用户名' }
  }
  
  const trimmed = username.trim()
  
  if (trimmed.length < 2) {
    return { valid: false, message: '用户名至少需要2个字符' }
  }
  
  if (trimmed.length > 20) {
    return { valid: false, message: '用户名不能超过20个字符' }
  }
  
  const validPattern = /^[a-zA-Z0-9\u4e00-\u9fa5_-]+$/
  if (!validPattern.test(trimmed)) {
    return { valid: false, message: '用户名只能包含字母、数字、中文、下划线和连字符' }
  }
  
  return { valid: true, message: '' }
}

/**
 * 验证密码
 * @param {string} password - 密码
 * @returns {Object} { valid: boolean, message: string }
 */
export function validatePassword(password) {
  if (!password || typeof password !== 'string') {
    return { valid: false, message: '请输入密码' }
  }
  
  if (password.length < 6) {
    return { valid: false, message: '密码至少需要6位' }
  }
  
  if (password.length > 20) {
    return { valid: false, message: '密码不能超过20个字符' }
  }
  
  return { valid: true, message: '' }
}

/**
 * 验证玩家昵称（游戏内显示）
 * @param {string} name - 玩家昵称
 * @returns {Object} { valid: boolean, message: string }
 */
export function validatePlayerName(name) {
  if (!name || typeof name !== 'string') {
    return { valid: false, message: '请输入昵称' }
  }
  
  const trimmed = name.trim()
  
  if (trimmed.length < 2) {
    return { valid: false, message: '昵称至少需要2个字符' }
  }
  
  if (trimmed.length > 20) {
    return { valid: false, message: '昵称不能超过20个字符' }
  }
  
  const validPattern = /^[a-zA-Z0-9\u4e00-\u9fa5_-]+$/
  if (!validPattern.test(trimmed)) {
    return { valid: false, message: '昵称只能包含字母、数字、中文、下划线和连字符' }
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
  
  if (!seed || typeof seed !== 'string') {
    return colors[0]
  }
  
  let hash = 0
  for (let i = 0; i < seed.length; i++) {
    hash = seed.charCodeAt(i) + ((hash << 5) - hash)
  }
  
  const index = Math.abs(hash) % colors.length
  return colors[index]
}

// ❌ 删除 generatePlayerId（后端自动生成UUID）

export default {
  validateUsername,
  validatePassword,
  validatePlayerName,
  validateRoomCode,
  generatePlayerColor
}