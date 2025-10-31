import axios from "axios";
import { logger } from "@/utils/logger";

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
    logger.error('Request Error:', error);
    return Promise.reject(error);
  }
);

// ============ 响应拦截器 ============
api.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    logger.error('API Error:', error.response?.data || error.message);

    // 🔥 处理 401 未授权错误
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('playerId');
      localStorage.removeItem('playerName');
      window.location.href = '/login';
      return Promise.reject(error);
    }

    // 🔥 检查是否需要静默处理（配置中设置了 silentError: true）
    const silentError = error.config?.silentError;

    // 🔥 过滤不需要全局提示的错误
    const shouldShowToast = !silentError && !isIgnorableError(error);

    // 只有需要提示的错误才触发全局事件
    if (shouldShowToast) {
      window.dispatchEvent(new CustomEvent('api-error', {
        detail: {
          message: error.response?.data?.message || error.message || '请求失败',
          status: error.response?.status,
          url: error.config?.url
        }
      }));
    }

    return Promise.reject(error);
  }
);

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

// ============ 房间相关API ============

export const createRoom = (maxPlayers, questionCount, questionTagIds = null) => {
  const params = { maxPlayers, questionCount };
  if (questionTagIds && questionTagIds.length > 0) {
    params.questionTagIds = questionTagIds;
  }
  return api.post('/rooms', null, { params });
};

export const joinRoom = (roomCode, playerId, playerName, spectator = false) =>
  api.post(`/rooms/${roomCode}/join`, null, {
    params: { playerId, playerName, spectator }
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

// ============ 玩家相关API ============

// ❌ 删除 createPlayer（已被 register 取代）
// export const createPlayer = (playerId, name) =>
//   api.post(`/players`, null, { params: { playerId, name } });

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