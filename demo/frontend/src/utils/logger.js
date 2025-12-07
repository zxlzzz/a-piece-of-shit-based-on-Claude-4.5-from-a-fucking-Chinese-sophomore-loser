const isDev = import.meta.env.DEV;

const noop = () => {};

export const logger = {
  debug: isDev
    ? (message, ...args) => console.debug(`[DEBUG] ${message}`, ...args)
    : noop,

  info: isDev
    ? (message, ...args) => console.info(`[INFO] ${message}`, ...args)
    : noop,

  warn: isDev
    ? (message, ...args) => console.warn(`[WARN] ${message}`, ...args)
    : noop,

  error: isDev
    ? (message, ...args) => console.error(`[ERROR] ${message}`, ...args)
    : (message, ...args) => console.error(message, ...args),
};
