import { Client } from "@stomp/stompjs";

const WS_URL = import.meta.env.VITE_WS_URL || '/ws';

let stompClient = null;
let connected = false;

/**
 * 建立 WebSocket 连接
 */
export function connect(playerId) {
  if (connected && stompClient?.connected) {
    return Promise.resolve(stompClient);
  }

  return new Promise((resolve, reject) => {
    stompClient = new Client({
      brokerURL: WS_URL.startsWith('http') ? WS_URL.replace(/^http/, 'ws') :
                 `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}${WS_URL}`,
      connectHeaders: {
        playerId: playerId
      },
      onConnect: () => {
        connected = true;
        console.log('✅ WebSocket 已连接');
        resolve(stompClient);
      },
      onStompError: (frame) => {
        console.error('❌ WebSocket 错误:', frame);
        connected = false;
        reject(new Error('WebSocket 连接失败'));
      },
      onWebSocketClose: () => {
        console.log('🔌 WebSocket 已断开');
        connected = false;
      }
    });

    stompClient.activate();
  });
}

/**
 * 断开连接
 */
export function disconnect() {
  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
    connected = false;
    console.log('🔌 WebSocket 已断开');
  }
}

/**
 * 检查连接状态
 */
export function isConnected() {
  return connected && stompClient?.connected;
}

/**
 * 订阅房间消息
 */
export function subscribeRoom(roomCode, onRoomUpdate, onError, playerId) {
  if (!isConnected()) {
    console.error('WebSocket 未连接');
    return [];
  }

  const subscriptions = [];

  // 订阅房间更新
  const roomSub = stompClient.subscribe(`/topic/room/${roomCode}`, (message) => {
    try {
      const data = JSON.parse(message.body);
      onRoomUpdate(data);
    } catch (error) {
      console.error('解析房间消息失败:', error);
    }
  });
  subscriptions.push(roomSub);

  // 订阅房间错误
  const errorSub = stompClient.subscribe(`/topic/room/${roomCode}/error`, (message) => {
    try {
      const data = JSON.parse(message.body);
      if (onError) onError(data);
    } catch (error) {
      console.error('解析错误消息失败:', error);
    }
  });
  subscriptions.push(errorSub);

  // 订阅被踢出消息
  if (playerId) {
    const kickSub = stompClient.subscribe(`/user/queue/kick`, (message) => {
      try {
        const data = JSON.parse(message.body);
        window.dispatchEvent(new CustomEvent('player-kicked', { detail: data }));
      } catch (error) {
        console.error('解析踢出消息失败:', error);
      }
    });
    subscriptions.push(kickSub);
  }

  console.log(`✅ 已订阅房间: ${roomCode}`);
  return subscriptions;
}

/**
 * 订阅聊天消息
 */
export function subscribeChat(roomCode, onChatMessage) {
  if (!isConnected()) {
    console.error('WebSocket 未连接');
    return null;
  }

  const subscription = stompClient.subscribe(`/topic/room/${roomCode}/chat`, (message) => {
    try {
      const data = JSON.parse(message.body);
      onChatMessage(data);
    } catch (error) {
      console.error('解析聊天消息失败:', error);
    }
  });

  console.log(`✅ 已订阅聊天: ${roomCode}`);
  return subscription;
}

/**
 * 发送聊天消息
 */
export function sendChatMessage(roomCode, playerId, playerName, message) {
  if (!isConnected()) {
    console.error('WebSocket 未连接');
    return;
  }

  stompClient.publish({
    destination: `/app/chat/${roomCode}`,
    body: JSON.stringify({
      playerId,
      playerName,
      message,
      timestamp: new Date().toISOString()
    })
  });
}

/**
 * 取消所有订阅
 */
export function unsubscribeAll(subscriptions) {
  if (subscriptions && subscriptions.length > 0) {
    subscriptions.forEach(sub => {
      if (sub && typeof sub.unsubscribe === 'function') {
        sub.unsubscribe();
      }
    });
    console.log(`✅ 已取消 ${subscriptions.length} 个订阅`);
  }
}

/**
 * 获取 STOMP 客户端实例
 */
export function getStompClient() {
  return stompClient;
}

/**
 * 通用消息发送函数
 */
export function sendMessage(destination, body) {
  if (!isConnected()) {
    console.error('WebSocket 未连接');
    return;
  }

  stompClient.publish({
    destination,
    body: JSON.stringify(body)
  });
}

/**
 * 等待连接建立（简化版 - demo 用）
 */
export function waitForConnection(timeout = 3000) {
  return new Promise((resolve, reject) => {
    if (isConnected()) {
      resolve();
      return;
    }

    const startTime = Date.now();
    const checkInterval = setInterval(() => {
      if (isConnected()) {
        clearInterval(checkInterval);
        resolve();
      } else if (Date.now() - startTime > timeout) {
        clearInterval(checkInterval);
        reject(new Error('等待连接超时'));
      }
    }, 100);
  });
}

// 重连回调列表（简化版 - demo 用）
const subscriptionCallbacks = [];

/**
 * 注册订阅恢复回调
 */
export function registerSubscriptionCallback(callback) {
  if (!subscriptionCallbacks.includes(callback)) {
    subscriptionCallbacks.push(callback);
  }
}

/**
 * 注销订阅恢复回调
 */
export function unregisterSubscriptionCallback(callback) {
  const index = subscriptionCallbacks.indexOf(callback);
  if (index > -1) {
    subscriptionCallbacks.splice(index, 1);
  }
}

// 房间订阅映射（简化版 - demo 用）
const roomSubscriptions = new Map();

/**
 * 取消房间订阅（简化版）
 */
export function unsubscribeRoom(roomCode) {
  const subs = roomSubscriptions.get(roomCode);
  if (subs) {
    unsubscribeAll(subs);
    roomSubscriptions.delete(roomCode);
    console.log(`✅ 已取消房间订阅: ${roomCode}`);
  }
}
