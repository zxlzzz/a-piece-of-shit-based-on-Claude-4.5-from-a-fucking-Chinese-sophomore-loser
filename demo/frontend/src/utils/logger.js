/**
 * 日志工具
 * 生产环境自动关闭所有日志（除了 error）
 */

const isDev = import.meta.env.DEV;

// 空操作函数（生产环境使用）
const noop = () => {};

export const logger = {
  // 调试日志（仅开发环境）
  debug: isDev
    ? (message, ...args) => console.debug(`🐛 ${message}`, ...args)
    : noop,

  // 信息日志（仅开发环境）
  info: isDev
    ? (message, ...args) => console.info(`ℹ️ ${message}`, ...args)
    : noop,

  // 警告日志（仅开发环境）
  warn: isDev
    ? (message, ...args) => console.warn(`⚠️ ${message}`, ...args)
    : noop,

  // 错误日志（开发和生产环境都显示，但生产环境移除表情符号）
  error: isDev
    ? (message, ...args) => console.error(`❌ ${message}`, ...args)
    : (message, ...args) => console.error(message, ...args),
};
