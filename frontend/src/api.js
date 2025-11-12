import axios from "axios";

const api = axios.create({
  baseURL: "/api",
  timeout: 10000,
});

// ============ 请求拦截器（添加 token）============
api.interceptors.request.use(
  (config) => {
    // 自动添加 token 到请求头
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => {

    return Promise.reject(error);
  }
);

// ============ 响应拦截器 ============
api.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    // 🔥 检查是否需要静默处理（配置中设置了 silentError: true）
    const silentError = error.config?.silentError;

    // 🔥 过滤不需要全局提示的错误
    const shouldShowToast = !silentError && !isIgnorableError(error);

    // 只有需要提示的错误才触发全局事件
    if (shouldShowToast) {
      const errorMessage = getErrorMessage(error);
      window.dispatchEvent(new CustomEvent('api-error', {
        detail: {
          message: errorMessage,
          status: error.response?.status,
          url: error.config?.url,
          isDev: import.meta.env.DEV
        }
      }));
    }

    return Promise.reject(error);
  }
);

// 🔥 根据错误类型返回友好的提示信息
function getErrorMessage(error) {
  const isDev = import.meta.env.DEV;
  const status = error.response?.status;
  const backendMessage = error.response?.data?.message;

  // 有响应（HTTP 错误）
  if (error.response) {
    switch (status) {
      case 400:
        return backendMessage || '请求参数错误';
      case 401:
        return '请先登录';
      case 403:
        return '无权限访问';
      case 404:
        return backendMessage || '请求的资源不存在';
      case 500:
      case 502:
      case 503:
        return isDev
          ? `服务器异常 (${status}): ${backendMessage || error.message}`
          : '服务器异常，请稍后重试';
      default:
        return backendMessage || (isDev ? error.message : '请求失败');
    }
  }
  // 请求发出去了但没收到响应（网络断了、后端没启动）
  else if (error.request) {
    return isDev
      ? '网络连接失败（服务器可能未启动）'
      : '网络连接失败，请检查网络';
  }
  // 请求配置错误
  else {
    return isDev ? `请求配置错误: ${error.message}` : '请求失败';
  }
}

// 🔥 判断是否是可忽略的错误（不需要弹窗提示）
function isIgnorableError(error) {
  const status = error.response?.status;
  const message = error.response?.data?.message || '';
  const url = error.config?.url || '';

  // 房间不存在（404/400）- 静默处理
  if ((status === 404 || status === 400) && url.includes('/rooms/')) {
    return true;
  }

  // 房间已结束/不存在等业务错误 - 静默处理
  if (message.includes('房间不存在') ||
      message.includes('房间已结束') ||
      message.includes('房间已过期') ||
      message.includes('房间已满') ||
      message.includes('游戏已开始')) {
    return true;
  }

  // 重复提交等正常业务逻辑 - 静默处理
  if (message.includes('已经提交') ||
      message.includes('已提交') ||
      message.includes('已准备') ||
      message.includes('未准备')) {
    return true;
  }

  // 🔥 自动恢复操作失败 - 静默处理（GET请求且是查询房间状态）
  if (error.config?.method === 'get' && url.includes('/rooms/') && status === 404) {
    return true;
  }

  return false;
}

// ============ 认证相关API（新增）============

export const register = (username, password, name) =>
  api.post('/auth/register', { username, password, name });

export const login = (username, password) =>
  api.post('/auth/login', { username, password });

export const guestLogin = (name) =>
  api.post('/auth/guest', { name });

// ============ 房间相关API ============

export const createRoom = (maxPlayers, questionCount, timeLimit = 30, password = null, questionTagIds = null) => {
  const params = { maxPlayers, questionCount, timeLimit, password };
  if (questionTagIds && questionTagIds.length > 0) {
    params.questionTagIds = questionTagIds;
  }
  return api.post('/rooms', null, { params });
};

export const joinRoom = (roomCode, playerId, playerName, spectator = false, password = null) =>
  api.post(`/rooms/${roomCode}/join`, null, {
    params: { playerId, playerName, spectator, password }
  });

export const startGame = (roomCode) =>
  api.post(`/rooms/${roomCode}/start`);

export const submitAnswer = (roomCode, playerId, choice, force = false) =>
  api.post(`/rooms/${roomCode}/submit`, null, {
    params: { playerId, choice, force }
  });

export const setPlayerReady = (roomCode, playerId, ready) =>
  api.put(`/rooms/${roomCode}/players/${playerId}/ready`, null, {
    params: { ready }
  });

export const getRoomStatus = (roomCode, silentError = false) =>
  api.get(`/rooms/${roomCode}`, { silentError });

export const getGameResults = (roomCode) =>
  api.get(`/rooms/${roomCode}/results`);

export const deleteRoom = (roomCode) =>
  api.delete(`/rooms/${roomCode}`);

export const getAllActiveRooms = () =>
  api.get(`/rooms`);

export const updateRoomSettings = (roomCode, settings) =>
  api.put(`/rooms/${roomCode}/settings`, settings);

export const loadTags = () =>
  api.get(`/tags`);

export const listPlayers = () =>
  api.get(`/players`);

export const getPlayer = (playerId) =>
  api.get(`/players/${playerId}`);

export const updatePlayerReady = (playerId, ready) =>
  api.put(`/players/${playerId}/ready`, null, {
    params: { ready }
  });

export const deletePlayer = (playerId) =>
  api.delete(`/players/${playerId}`);

export const kickPlayer = (roomCode, ownerId, targetPlayerId) =>
  api.post(`/rooms/${roomCode}/kick`, null, {
    params: { ownerId, targetPlayerId }
  });


// ============ 题目相关API ============

export const getAllQuestions = () =>
  api.get(`/question`);

export const getRandomQuestions = (count = 10) =>
  api.get(`/question/random`, { params: { count } });

export const getSuitableQuestions = (playerCount, questionCount = 10) =>
  api.get(`/questions/suitable`, {
    params: { playerCount, questionCount }
  });

// ============ 游戏历史相关API ============

export const getGameHistory = (roomCode) => 
  api.get(`/rooms/${roomCode}/history`);

export const getHistoryList = (playerId, days) => {
  const params = { playerId };
  if (days) params.days = days;
  return api.get('/games/history', { params });
};

export const getHistoryDetail = (gameId) => 
  api.get(`/games/history/${gameId}`);

export default api;