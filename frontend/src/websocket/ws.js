import { Client } from "@stomp/stompjs";
import { logger } from "@/utils/logger";
import {
  WS_MAX_RECONNECT_ATTEMPTS,
  WS_BASE_RECONNECT_DELAY,
  WS_RECONNECT_DELAY,
  WS_CONNECT_TIMEOUT,
  WS_CONNECT_PROMISE_TIMEOUT
} from "@/config/constants";

const WS_URL = import.meta.env.VITE_WS_URL || '/ws';

let stompClient = null;
let connected = false;
let currentPlayerId = null;
let connectPromise = null;
let connectTimeoutId = null; // 🔥 修复：存储连接超时ID，避免泄漏
let reconnectAttempts = 0;
let reconnectTimer = null;
let isReconnecting = false; // 🔥 标记是否正在重连
let manualDisconnect = false; // 🔥 标记是否手动断开（手动断开不自动重连）
let subscriptionCallbacks = []; // 🔥 保存订阅回调用于重连后恢复
let personalSubscriptions = []; // 🔥 修复：存储个人消息订阅，避免内存泄漏

/**
 * 建立 STOMP 连接（单例模式）
 * @param {string} playerId - 玩家ID
 * @param {function} onConnect - 连接成功回调
 * @param {function} onError - 连接错误回调
 * @returns {Promise<Client>}
 */
export function connect(playerId, onConnect, onError) {
  // 如果已连接且是同一玩家，直接返回
  if (connected && currentPlayerId === playerId && stompClient?.connected) {
    if (onConnect) onConnect(stompClient);
    return Promise.resolve(stompClient);
  }

  // 🔥 修改：如果正在连接中，检查是否超时
  if (connectPromise) {
    const now = Date.now();
    // 如果连接 Promise 存在超过设定时间，强制重置
    if (!connectPromise._startTime) {
      connectPromise._startTime = now;
    } else if (now - connectPromise._startTime > WS_CONNECT_PROMISE_TIMEOUT) {
      logger.error('连接超时，强制重置');

      // 🔥 修复：清除超时处理器，避免资源泄漏
      if (connectTimeoutId) {
        clearTimeout(connectTimeoutId);
        connectTimeoutId = null;
      }

      connectPromise = null;
      if (stompClient) {
        try {
          stompClient.deactivate();
        } catch (e) {
          logger.error('强制断开失败:', e);
        }
        stompClient = null;
        connected = false;
      }
    } else {
      return connectPromise;
    }
  }

  // 如果切换玩家，先断开旧连接
  if (connected && currentPlayerId !== playerId) {
    disconnect();
  }

  currentPlayerId = playerId;

  // 创建新的连接 Promise
  connectPromise = new Promise((resolve, reject) => {
    // 🔥 修复：使用全局变量存储超时ID，避免泄漏
    connectTimeoutId = setTimeout(() => {
      logger.error(`连接超时（${WS_CONNECT_TIMEOUT / 1000}秒）`);
      connectTimeoutId = null;
      connectPromise = null;
      reject(new Error('连接超时'));
    }, WS_CONNECT_TIMEOUT);

    logger.info('🔌 WebSocket: 准备连接', { playerId, WS_URL })

    stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_URL),

      connectHeaders: {
        'playerId': playerId
      },

      reconnectDelay: WS_RECONNECT_DELAY,

      // ⚠️ 禁用心跳检测：玩家答题时可能长时间无操作，心跳会导致误判断连
      heartbeatIncoming: 0,
      heartbeatOutgoing: 0,

      onConnect: (frame) => {
        // 🔥 修复：清除超时处理器
        if (connectTimeoutId) {
          clearTimeout(connectTimeoutId);
          connectTimeoutId = null;
        }

        connected = true;
        connectPromise = null;
        manualDisconnect = false;

        // 重连成功
        if (isReconnecting) {
          isReconnecting = false;
          reconnectAttempts = 0;

          // 触发重连成功事件
          window.dispatchEvent(new CustomEvent('websocket-reconnected'));

          // 恢复所有订阅
          restoreSubscriptions();
        } else {
          reconnectAttempts = 0;
        }

        subscribeToPersonalMessages(playerId);

        if (onConnect) onConnect(stompClient);
        resolve(stompClient);
      },

      onDisconnect: () => {
        clearTimeout(timeoutId);
        connected = false;
        connectPromise = null;

        // 手动断开不自动重连
        if (manualDisconnect) {
          logger.debug('手动断开，不进行重连');
          return;
        }

        // 检查是否已达最大重连次数
        if (reconnectAttempts >= WS_MAX_RECONNECT_ATTEMPTS) {
          logger.error('已达到最大重连次数，停止重连');
          isReconnecting = false;
          window.dispatchEvent(new CustomEvent('websocket-max-reconnect-failed'));
          return;
        }

        // 避免重复触发重连
        if (isReconnecting && reconnectTimer) {
          logger.debug('已在重连中，跳过本次断开事件');
          return;
        }

        // 开始重连流程
        isReconnecting = true;
        const delay = WS_BASE_RECONNECT_DELAY * Math.pow(2, reconnectAttempts);
        reconnectAttempts++;

        logger.debug(`开始第 ${reconnectAttempts} 次重连，延迟 ${delay}ms`);

        // 触发重连中事件（带进度信息）
        window.dispatchEvent(new CustomEvent('websocket-reconnecting', {
          detail: {
            attempts: reconnectAttempts,
            maxAttempts: WS_MAX_RECONNECT_ATTEMPTS,
            delay: delay
          }
        }));

        // 清除旧的重连定时器（如果有）
        if (reconnectTimer) {
          clearTimeout(reconnectTimer);
        }

        reconnectTimer = setTimeout(async () => {
          reconnectTimer = null;
          try {
            await reconnect();
            // 重连成功会触发 onConnect，在那里重置状态
          } catch (err) {
            logger.error('重连失败:', err);
            isReconnecting = false;
            // 如果还没到最大次数，下次 onDisconnect 会再次触发重连
            // 如果已到最大次数，上面的检查会阻止重连
          }
        }, delay);
      },

      onStompError: (frame) => {
        clearTimeout(timeoutId);
        connectPromise = null;
        logger.error("STOMP error:", frame);

        window.dispatchEvent(new CustomEvent('websocket-error', {
          detail: { type: 'stomp', error: frame }
        }));

        if (onError) onError(frame);
        reject(frame);
      },

      onWebSocketError: (error) => {
        clearTimeout(timeoutId);
        connectPromise = null;
        logger.error("WebSocket error:", error);

        window.dispatchEvent(new CustomEvent('websocket-error', {
          detail: { type: 'websocket', error }
        }));

        if (onError) onError(error);
        reject(error);
      }
    });

    stompClient.activate();
  });

  // 🔥 添加时间戳用于超时检测
  connectPromise._startTime = Date.now();

  return connectPromise;
}


/**
 * 订阅个人消息（错误通知、欢迎消息等）
 * 🔥 修复：存储订阅对象，避免内存泄漏
 */
function subscribeToPersonalMessages(playerId) {
  if (!ensureConnected("subscribeToPersonalMessages")) return;

  // 🔥 先清理旧的个人订阅，避免重复订阅
  cleanupPersonalSubscriptions();

  // 订阅个人错误消息
  const errorSub = safeSubscribe(`/user/queue/error`, (data) => {
    logger.error("收到个人错误消息:", data);
    window.dispatchEvent(new CustomEvent('websocket-error', {
      detail: { type: 'personal', data }
    }));
  });

  // 订阅欢迎消息
  const welcomeSub = safeSubscribe(`/user/queue/welcome`, (data) => {
    window.dispatchEvent(new CustomEvent('websocket-welcome', { detail: data }));
  });

  // 🔥 修复：存储订阅以便后续清理
  if (errorSub) personalSubscriptions.push(errorSub);
  if (welcomeSub) personalSubscriptions.push(welcomeSub);

  logger.debug(`✅ 已订阅个人消息，当前订阅数: ${personalSubscriptions.length}`);
}

/**
 * 清理个人消息订阅
 * 🔥 修复：防止内存泄漏
 */
function cleanupPersonalSubscriptions() {
  personalSubscriptions.forEach(sub => {
    try {
      sub.unsubscribe();
    } catch (e) {
      logger.error('取消个人订阅失败:', e);
    }
  });
  personalSubscriptions = [];
}

/**
 * 恢复重连后的订阅
 */
function restoreSubscriptions() {
  subscriptionCallbacks.forEach(callback => {
    try {
      callback();
    } catch (err) {
      logger.error('恢复订阅失败:', err);
    }
  });
}

/**
 * 注册订阅回调（用于重连后恢复）
 */
export function registerSubscriptionCallback(callback) {
  if (typeof callback === 'function' && !subscriptionCallbacks.includes(callback)) {
    subscriptionCallbacks.push(callback);
  }
}

/**
 * 移除订阅回调
 */
export function unregisterSubscriptionCallback(callback) {
  const index = subscriptionCallbacks.indexOf(callback);
  if (index > -1) {
    subscriptionCallbacks.splice(index, 1);
  }
}

/**
 * 断开连接
 * @param {boolean} force - 是否强制清理所有状态
 */
export function disconnect(force = false) {
  // 标记为手动断开，防止自动重连
  manualDisconnect = true;
  isReconnecting = false;
  reconnectAttempts = 0;

  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }

  // 🔥 修复：清理连接超时处理器
  if (connectTimeoutId) {
    clearTimeout(connectTimeoutId);
    connectTimeoutId = null;
  }

  // 🔥 修复：清理个人消息订阅，避免内存泄漏
  cleanupPersonalSubscriptions();

  if (stompClient) {
    try {
      stompClient.deactivate();
    } catch (e) {
      logger.error('断开连接失败:', e);
    }
  }

  // 清理所有状态
  stompClient = null;
  connected = false;
  currentPlayerId = null;
  connectPromise = null;

  // 清理订阅回调
  if (force) {
    subscriptionCallbacks = [];
  }
}

/**
 * 确保连接可用
 */
function ensureConnected(action) {
  if (!stompClient || !connected) {
    logger.error("STOMP not connected yet, action skipped:", action);
    return false;
  }
  return true;
}

/**
 * 等待 WebSocket 连接建立（事件驱动，避免轮询）
 * @param {number} maxWait - 最大等待时间（毫秒）
 * @returns {Promise<void>}
 */
export function waitForConnection(maxWait = 10000) {
  return new Promise((resolve, reject) => {
    if (isConnected()) {
      resolve();
      return;
    }

    logger.warn('⚠️ WebSocket 未连接，等待连接...');

    const timeout = setTimeout(() => {
      window.removeEventListener('websocket-connected', onConnected);
      reject(new Error(`WebSocket 连接超时（${maxWait / 1000}秒）`));
    }, maxWait);

    const onConnected = () => {
      clearTimeout(timeout);
      window.removeEventListener('websocket-connected', onConnected);
      logger.info('✅ WebSocket 连接成功');
      resolve();
    };

    window.addEventListener('websocket-connected', onConnected);
  });
}

/**
 * 通用订阅（修改：增加错误处理）
 */
export function safeSubscribe(destination, onMessage) {
  if (!ensureConnected("subscribe " + destination)) {
    logger.error('订阅失败：未连接', destination)
    throw new Error('WebSocket 未连接')
  }

  try {
    const sub = stompClient.subscribe(destination, (msg) => {
      try {
        const data = JSON.parse(msg.body);
        onMessage(data);
      } catch (e) {
        logger.error("JSON parse error:", e, "原始消息:", msg.body);
        onMessage(msg.body);
      }
    });

    return sub;
  } catch (error) {
    logger.error("订阅失败:", destination, error);
    throw error;
  }
}

/**
 * 房间统一订阅
 * @param {string} roomCode - 房间码
 * @param {function} onRoomUpdate - 房间更新回调
 * @param {function} onRoomError - 房间错误回调
 * @param {string} playerId - 玩家ID（可选，用于订阅被踢事件）
 */
export function subscribeRoom(roomCode, onRoomUpdate, onRoomError, playerId = null) {
  const subscriptions = [];

  const roomUpdateSub = safeSubscribe(`/topic/room/${roomCode}`, (data) => {
    if (data && onRoomUpdate) {
      onRoomUpdate(data);
    }
  });

  const roomErrorSub = safeSubscribe(`/topic/room/${roomCode}/error`, (data) => {
    logger.error("房间错误:", data);
    if (onRoomError) {
      onRoomError(data);
    }
  });

  const roomDeletedSub = safeSubscribe(`/topic/room/${roomCode}/deleted`, (data) => {
    window.dispatchEvent(new CustomEvent('room-deleted', { detail: data }));
  });

  // 🔥 订阅被踢事件（使用 topic 而不是 user queue）
  let kickedSub = null;
  if (playerId) {
    kickedSub = safeSubscribe(`/topic/player/${playerId}/kicked`, (data) => {
      window.dispatchEvent(new CustomEvent('player-kicked', { detail: data }));
    });
  }

  // 只添加成功的订阅
  if (roomUpdateSub) subscriptions.push(roomUpdateSub);
  if (roomErrorSub) subscriptions.push(roomErrorSub);
  if (roomDeletedSub) subscriptions.push(roomDeletedSub);
  if (kickedSub) subscriptions.push(kickedSub);

  return subscriptions;
}

/**
 * 取消订阅（修改：增加错误处理）
 */
export function unsubscribe(subscription) {
  if (subscription && typeof subscription.unsubscribe === 'function') {
    try {
      subscription.unsubscribe();
    } catch (error) {
      logger.error("取消订阅失败:", subscription.id, error);
    }
  }
}

/**
 * 取消多个订阅
 */
export function unsubscribeAll(subscriptions) {
  if (Array.isArray(subscriptions)) {
    subscriptions.forEach(unsubscribe);
  }
}

// ============ 发送消息的方法（已废弃） ============

/**
 * 🔥 以下WebSocket命令发送方法已废弃
 *
 * 优化策略：采用混合模式
 * - 所有操作命令改用HTTP API（见 api.js）
 * - WebSocket仅用于接收服务器推送的状态更新
 *
 * 优势：
 * 1. HTTP操作更可靠，掉线后可重试
 * 2. 减少WebSocket负担，连接更稳定
 * 3. 更容易调试和监控
 *
 * HTTP API替代方案：
 * - sendJoin → api.joinRoom()
 * - sendStart → api.startGame()
 * - sendSubmit → api.submitAnswer()
 * - sendReady → api.setPlayerReady()
 * - sendLeave → 关闭页面自动处理或使用 api.deleteRoom()
 */

// @deprecated 请使用 api.joinRoom()
export function sendJoin(req) {
  console.warn('⚠️ sendJoin已废弃，请使用 api.joinRoom()');
  if (!ensureConnected("sendJoin")) return;

  const payload = {
    roomCode: req.roomCode,
    playerId: req.playerId,
    playerName: req.playerName
  };

  stompClient.publish({
    destination: "/app/join",
    body: JSON.stringify(payload),
  });
}

// @deprecated 请使用 api.startGame()
export function sendStart(req) {
  console.warn('⚠️ sendStart已废弃，请使用 api.startGame()');
  if (!ensureConnected("sendStart")) return;

  const payload = {
    roomCode: req.roomCode
  };

  stompClient.publish({
    destination: "/app/start",
    body: JSON.stringify(payload),
  });
}

// @deprecated 请使用 api.submitAnswer()
export function sendSubmit(req) {
  console.warn('⚠️ sendSubmit已废弃，请使用 api.submitAnswer()');
  if (!ensureConnected("sendSubmit")) return;

  const payload = {
    roomCode: req.roomCode,
    playerId: req.playerId,
    choice: req.choice?.toString(),
    force: req.force === true
  };

  stompClient.publish({
    destination: "/app/submit",
    body: JSON.stringify(payload),
  });
}

// @deprecated 请使用 api.setPlayerReady()
export function sendReady(req) {
  console.warn('⚠️ sendReady已废弃，请使用 api.setPlayerReady()');
  if (!ensureConnected("sendReady")) return;

  const payload = {
    roomCode: req.roomCode,
    playerId: req.playerId,
    ready: req.ready === true
  };

  stompClient.publish({
    destination: "/app/ready",
    body: JSON.stringify(payload),
  });
}

// @deprecated 离开房间通过关闭页面自动处理
export function sendLeave(req) {
  console.warn('⚠️ sendLeave已废弃，离开房间通过关闭页面自动处理');
  if (!ensureConnected("sendLeave")) return;

  const payload = {
    roomCode: req.roomCode,
    playerId: req.playerId
  };

  stompClient.publish({
    destination: "/app/leave",
    body: JSON.stringify(payload),
  });
}

// ============ 工具方法 ============

export function isConnected() {
  return connected && stompClient && stompClient.connected;
}

export function getCurrentPlayerId() {
  return currentPlayerId;
}

/**
 * 重新连接（修改：返回 Promise）
 */
export function reconnect() {
  if (currentPlayerId) {
    window.dispatchEvent(new CustomEvent('websocket-reconnecting', {
      detail: { attempts: reconnectAttempts }
    }));
    return connect(currentPlayerId);
  } else {
    logger.error("无法重新连接：没有保存的玩家ID");
    return Promise.reject(new Error('没有保存的玩家ID'));
  }
}

export function getStompClient() {
  return stompClient;
}

export function sendMessage(destination, message) {
  if (!ensureConnected("sendMessage")) return;

  stompClient.publish({
    destination: destination,
    body: JSON.stringify(message),
  });
}

export function getConnectionState() {
  return {
    connected,
    reconnectAttempts,
    maxAttempts: WS_MAX_RECONNECT_ATTEMPTS,
    playerId: currentPlayerId
  };
}

export default {
  connect,
  disconnect,
  reconnect,
  subscribeRoom,
  unsubscribe,
  unsubscribeAll,
  sendJoin,
  sendStart,
  sendSubmit,
  sendReady,
  sendLeave,
  isConnected,
  getCurrentPlayerId,
  getStompClient,
  sendMessage,
  getConnectionState,
  registerSubscriptionCallback,
  unregisterSubscriptionCallback
};
