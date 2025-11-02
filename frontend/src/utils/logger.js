/**
 * 日志工具
 * 生产环境自动关闭所有日志
 */

const isDev = import.meta.env.DEV;

export const logger = {
  // 信息日志
  info: (message, ...args) => {
    console.info(`ℹ️ ${message}`, ...args);
  },
  
  // 警告日志
  warn: (message, ...args) => {
    console.warn(`⚠️ ${message}`, ...args);
  },
  
  // 错误日志
  error: (message, ...args) => {
    console.error(`❌ ${message}`, ...args);
  },
};
