import axios from "axios";
import { API_TIMEOUT } from '@/config/constants';

const api = axios.create({
  baseURL: "/api",
  timeout: API_TIMEOUT,
});

api.interceptors.request.use(
  (config) => {
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

api.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    const silentError = error.config?.silentError;
    const shouldShowToast = !silentError && !isIgnorableError(error);

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

function getErrorMessage(error) {
  const isDev = import.meta.env.DEV;
  const status = error.response?.status;
  const backendMessage = error.response?.data?.message;

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
  else if (error.request) {
    return isDev
      ? '网络连接失败（服务器可能未启动）'
      : '网络连接失败，请检查网络';
  }
  else {
    return isDev ? `请求配置错误: ${error.message}` : '请求失败';
  }
}

function isIgnorableError(error) {
  const status = error.response?.status;
  const message = error.response?.data?.message || '';
  const url = error.config?.url || '';

  if ((status === 404 || status === 400) && url.includes('/rooms/')) {
    return true;
  }

  if (message.includes('房间不存在') ||
      message.includes('房间已结束') ||
      message.includes('房间已过期') ||
      message.includes('房间已满') ||
      message.includes('游戏已开始')) {
    return true;
  }

  if (message.includes('已经提交') ||
      message.includes('已提交') ||
      message.includes('已准备') ||
      message.includes('未准备')) {
    return true;
  }

  if (error.config?.method === 'get' && url.includes('/rooms/') && status === 404) {
    return true;
  }

  return false;
}

export const register = (username, password, name) =>
  api.post('/auth/register', { username, password, name });

export const login = (username, password) =>
  api.post('/auth/login', { username, password });

export const createRoom = (maxPlayers, questionCount, timeLimit = 30, password = null, questionTagIds = null) => {
  const params = { maxPlayers, questionCount, timeLimit, password };
  if (questionTagIds && questionTagIds.length > 0) {
    params.questionTagIds = questionTagIds;
  }
  return api.post('/rooms', null, { params });
};

export const joinRoom = (roomCode, playerId, playerName, password = null) =>
  api.post(`/rooms/${roomCode}/join`, null, {
    params: { playerId, playerName, password }
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

export const kickPlayer = (roomCode, ownerId, targetPlayerId) =>
  api.post(`/rooms/${roomCode}/kick`, null, {
    params: { ownerId, targetPlayerId }
  });


export const getAllQuestions = () =>
  api.get(`/question`);

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