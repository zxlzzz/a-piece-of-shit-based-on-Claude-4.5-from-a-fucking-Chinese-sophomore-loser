import { Client } from "@stomp/stompjs";
import { logger } from "@/utils/logger";

let stompClient = null;
let connected = false;
let currentPlayerId = null;
let connectPromise = null;
let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 5;
const BASE_RECONNECT_DELAY = 1000; // 1秒
let reconnectTimer = null;
let isReconnecting = false; // 🔥 标记是否正在重连
let manualDisconnect = false; // 🔥 标记是否手动断开（手动断开不自动重连）
let subscriptionCallbacks = []; // 🔥 保存订阅回调用于重连后恢复

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
    // 如果连接 Promise 存在超过 10 秒，强制重置
    if (!connectPromise._startTime) {
      connectPromise._startTime = now;
    } else if (now - connectPromise._startTime > 10000) {
      logger.error('连接超时，强制重置');
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
    // 添加超时保护
    const timeoutId = setTimeout(() => {
      logger.error('连接超时（15秒）');
      connectPromise = null;
      reject(new Error('连接超时'));
    }, 15000);

    stompClient = new Client({
      webSocketFactory: () => new SockJS("/ws"),

      connectHeaders: {
        'playerId': playerId
      },

      reconnectDelay: 3000,

      onConnect: (frame) => {
        clearTimeout(timeoutId);
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

        // 手动断开或已经在重连中，不再触发新的重连
        if (manualDisconnect) {
          return;
        }

        // 只有非手动断开才自动重连
        if (!isReconnecting && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
          isReconnecting = true;
          const delay = BASE_RECONNECT_DELAY * Math.pow(2, reconnectAttempts);
          reconnectAttempts++;

          // 触发重连中事件（带进度信息）
          window.dispatchEvent(new CustomEvent('websocket-reconnecting', {
            detail: {
              attempts: reconnectAttempts,
              maxAttempts: MAX_RECONNECT_ATTEMPTS,
              delay: delay
            }
          }));

          reconnectTimer = setTimeout(() => {
            reconnect().catch(err => {
              logger.error('重连失败:', err);
              // 如果还没到最大次数，onDisconnect会再次触发重连
            });
          }, delay);
        } else if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
          logger.error('已达到最大重连次数，停止重连');
          isReconnecting = false;
          window.dispatchEvent(new CustomEvent('websocket-max-reconnect-failed'));
        }
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
 */
function subscribeToPersonalMessages(playerId) {
  if (!ensureConnected("subscribeToPersonalMessages")) return;

  // 订阅个人错误消息
  safeSubscribe(`/user/queue/error`, (data) => {
    logger.error("收到个人错误消息:", data);
    window.dispatchEvent(new CustomEvent('websocket-error', {
      detail: { type: 'personal', data }
    }));
  });

  // 订阅欢迎消息
  safeSubscribe(`/user/queue/welcome`, (data) => {
    window.dispatchEvent(new CustomEvent('websocket-welcome', { detail: data }));
  });
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
      logger.warn("👢 您已被踢出房间:", data);
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

// ============ 发送消息的方法 ============

export function sendJoin(req) {
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

export function sendStart(req) {
  if (!ensureConnected("sendStart")) return;

  const payload = {
    roomCode: req.roomCode
  };

  stompClient.publish({
    destination: "/app/start",
    body: JSON.stringify(payload),
  });
}

export function sendSubmit(req) {
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

export function sendReady(req) {
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

export function sendLeave(req) {
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
    maxAttempts: MAX_RECONNECT_ATTEMPTS,
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
