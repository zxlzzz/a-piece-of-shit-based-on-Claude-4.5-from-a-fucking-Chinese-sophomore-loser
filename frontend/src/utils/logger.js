/**
 * 日志工具
 * 生产环境自动关闭所有日志
 */

const isDev = import.meta.env.DEV;

export const logger = {
  // 只保留错误日志
  error: (message, ...args) => {
    console.error(`❌ ${message}`, ...args);
  },
};
