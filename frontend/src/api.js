import axios from "axios";

const api = axios.create({
  baseURL: "/api",
  timeout: 10000,
});

// ============ 请求拦截器（添加 token）============
api.interceptors.request.use(
  (config) => {
    console.log('🚀 API Request:', config.method?.toUpperCase(), config.url, config.params);
    
    // 🔥 自动添加 token 到请求头
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    
    return config;
  },
  (error) => {
    console.error('❌ Request Error:', error);
    return Promise.reject(error);
  }
);

// ============ 响应拦截器 ============
api.interceptors.response.use(
  (response) => {
    console.log('✅ API Response:', response.config.url, response.data);
    return response;
  },
  (error) => {
    console.error('❌ API Error:', error.response?.data || error.message);
    
    // 🔥 处理 401 未授权错误
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('playerId');
      localStorage.removeItem('playerName');
      window.location.href = '/login';
    }
    
    // 触发全局错误事件
    window.dispatchEvent(new CustomEvent('api-error', {
      detail: {
        message: error.response?.data?.message || error.message || '请求失败',
        status: error.response?.status,
        url: error.config?.url
      }
    }));
    
    return Promise.reject(error);
  }
);

// ============ 认证相关API（新增）============

export const register = (username, password, name) =>
  api.post('/auth/register', { username, password, name });

export const login = (username, password) =>
  api.post('/auth/login', { username, password });

// ============ 房间相关API ============

export const createRoom = (maxPlayers, questionCount) =>
  api.post('/rooms', null, {
    params: {maxPlayers, questionCount}
  });

export const joinRoom = (roomCode, playerId, playerName) =>
  api.post(`/rooms/${roomCode}/join`, null, {
    params: { playerId, playerName }
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

export const getRoomStatus = (roomCode) =>
  api.get(`/rooms/${roomCode}`);

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