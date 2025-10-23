import { Client } from "@stomp/stompjs";

let stompClient = null;
let connected = false;
let currentPlayerId = null;
let connectPromise = null;
let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 5;
const BASE_RECONNECT_DELAY = 1000; // 1秒
let reconnectTimer = null;

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
    console.log('♻️ 复用现有 WebSocket 连接');
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
      console.error('❌ 连接超时，强制重置');
      connectPromise = null;
      if (stompClient) {
        try {
          stompClient.deactivate();
        } catch (e) {
          console.warn('强制断开失败:', e);
        }
        stompClient = null;
        connected = false;
      }
    } else {
      console.log('⏳ 正在连接中，等待完成...');
      return connectPromise;
    }
  }
  
  // 如果切换玩家，先断开旧连接
  if (connected && currentPlayerId !== playerId) {
    console.log('🔄 切换玩家，断开旧连接');
    disconnect();
  }
  
  currentPlayerId = playerId;
  
  // 创建新的连接 Promise
  connectPromise = new Promise((resolve, reject) => {
    // 🔥 添加超时保护
    const timeoutId = setTimeout(() => {
      console.error('❌ 连接超时（15秒）');
      connectPromise = null;
      reject(new Error('连接超时'));
    }, 15000);
    
    stompClient = new Client({
      webSocketFactory: () => new SockJS("/ws"),
      
      connectHeaders: {
        'playerId': playerId
      },
      
      reconnectDelay: 3000,
      heartbeatIncoming: 0,  // 🔥 30秒（与后端一致）
      heartbeatOutgoing: 0,  // 🔥 30秒（与后端一致）
      
      onConnect: (frame) => {
        clearTimeout(timeoutId); // 🔥 清除超时
        connected = true;
        connectPromise = null;
        reconnectAttempts = 0;
        console.log("✅ STOMP connected for playerId:", playerId);
        console.log("📋 Connection frame:", frame);
        
        subscribeToPersonalMessages(playerId);
        
        if (onConnect) onConnect(stompClient);
        resolve(stompClient);
      },
      
      onDisconnect: () => {
        clearTimeout(timeoutId);
        connected = false;
        connectPromise = null;
        console.warn("⚠️ STOMP disconnected");
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
          const delay = BASE_RECONNECT_DELAY * Math.pow(2, reconnectAttempts);
          reconnectAttempts++;
          console.log(`🔄 将在 ${delay}ms 后重连 (尝试 ${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})`);
          
          reconnectTimer = setTimeout(() => {
            reconnect().catch(err => {
              console.error('重连失败:', err);
            });
          }, delay);
        } else {
          console.error('❌ 已达到最大重连次数，停止重连');
          window.dispatchEvent(new CustomEvent('websocket-max-reconnect-failed'));
        }
      },
      
      onStompError: (frame) => {
        clearTimeout(timeoutId);
        connectPromise = null;
        console.error("❌ STOMP error:", frame);
        
        window.dispatchEvent(new CustomEvent('websocket-error', { 
          detail: { type: 'stomp', error: frame } 
        }));
        
        if (onError) onError(frame);
        reject(frame);
      },
      
      onWebSocketError: (error) => {
        clearTimeout(timeoutId);
        connectPromise = null;
        console.error("❌ WebSocket error:", error);
        
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
    console.error("🔥 收到个人错误消息:", data);
    window.dispatchEvent(new CustomEvent('websocket-error', { 
      detail: { type: 'personal', data } 
    }));
  });
  
  // 订阅欢迎消息
  safeSubscribe(`/user/queue/welcome`, (data) => {
    console.log("🎉 收到欢迎消息:", data);
    window.dispatchEvent(new CustomEvent('websocket-welcome', { detail: data }));
  });
}

/**
 * 断开连接
 * @param {boolean} force - 是否强制清理所有状态
 */
export function disconnect(force = false) {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  if (stompClient) {
    try {
      stompClient.deactivate();
    } catch (e) {
      console.warn('❌ 断开连接失败:', e);
    }
  }
  
  // 🔥 强制清理所有状态
  stompClient = null;
  connected = false;
  currentPlayerId = null;
  connectPromise = null;
  
  if (force) {
    console.log("🔌 STOMP 强制断开并清理状态");
  } else {
    console.log("🔌 STOMP disconnected manually");
  }
}

/**
 * 确保连接可用
 */
function ensureConnected(action) {
  if (!stompClient || !connected) {
    console.error("❌ STOMP not connected yet, action skipped:", action);
    return false;
  }
  return true;
}

/**
 * 通用订阅（修改：增加错误处理）
 */
export function safeSubscribe(destination, onMessage) {
  if (!ensureConnected("subscribe " + destination)) {
    console.error('❌ 订阅失败：未连接', destination)
    throw new Error('WebSocket 未连接') // 🔥 抛出错误而不是返回 null
  }

  try {
    const sub = stompClient.subscribe(destination, (msg) => {
      try {
        const data = JSON.parse(msg.body);
        console.log(`📥 收到消息 [${destination}]:`, data);
        onMessage(data);
      } catch (e) {
        console.error("❌ JSON parse error:", e, "原始消息:", msg.body);
        onMessage(msg.body);
      }
    });

    console.log("✅ 订阅成功:", destination, "=> subscription ID:", sub?.id);
    return sub;
  } catch (error) {
    console.error("❌ 订阅失败:", destination, error);
    throw error; // 🔥 抛出错误而不是返回 null
  }
}

/**
 * 房间统一订阅
 */
export function subscribeRoom(roomCode, onRoomUpdate, onRoomError) {
  const subscriptions = [];
  
  const roomUpdateSub = safeSubscribe(`/topic/room/${roomCode}`, (data) => {
    if (data && onRoomUpdate) {
      onRoomUpdate(data);
    }
  });
  
  const roomErrorSub = safeSubscribe(`/topic/room/${roomCode}/error`, (data) => {
    console.error("🔥 房间错误:", data);
    if (onRoomError) {
      onRoomError(data);
    }
  });
  
  const roomDeletedSub = safeSubscribe(`/topic/room/${roomCode}/deleted`, (data) => {
    console.warn("🗑️ 房间已被删除:", data);
    window.dispatchEvent(new CustomEvent('room-deleted', { detail: data }));
  });
  
  // 修改：只添加成功的订阅
  if (roomUpdateSub) subscriptions.push(roomUpdateSub);
  if (roomErrorSub) subscriptions.push(roomErrorSub);
  if (roomDeletedSub) subscriptions.push(roomDeletedSub);
  
  return subscriptions;
}

/**
 * 取消订阅（修改：增加错误处理）
 */
export function unsubscribe(subscription) {
  if (subscription && typeof subscription.unsubscribe === 'function') {
    try {
      subscription.unsubscribe();
      console.log("✅ 取消订阅:", subscription.id);
    } catch (error) {
      console.error("❌ 取消订阅失败:", subscription.id, error);
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
  
  console.log("➡️ 发送加入房间:", payload);
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
  
  console.log("➡️ 发送开始游戏:", payload);
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
  
  console.log("➡️ 发送提交答案:", payload);
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
  
  console.log("➡️ 发送准备状态:", payload);
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
  
  console.log("➡️ 发送离开房间:", payload);
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
    console.log("🔄 尝试重新连接...");
    window.dispatchEvent(new CustomEvent('websocket-reconnecting', {
      detail: { attempts: reconnectAttempts }
    }));
    return connect(currentPlayerId);
  } else {
    console.error("❌ 无法重新连接：没有保存的玩家ID");
    return Promise.reject(new Error('没有保存的玩家ID'));
  }
}

export function getStompClient() {
  return stompClient;
}

export function sendMessage(destination, message) {
  if (!ensureConnected("sendMessage")) return;
  
  console.log("➡️ 发送消息到:", destination, message);
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
  getConnectionState
};