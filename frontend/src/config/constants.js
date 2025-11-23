// ============ Toast 通知相关 ============
export const TOAST_DEBOUNCE_TIME = 3000 // 相同Toast去重时间窗口（毫秒）
export const TOAST_CLEANUP_DELAY = 1000 // Toast记录清理延迟（毫秒）
export const TOAST_DEFAULT_LIFE = 3000 // Toast默认显示时长（毫秒）

// ============ API 请求相关 ============
export const API_TIMEOUT = 10000 // API请求超时时间（毫秒）

// ============ 本地存储相关 ============
export const ROOM_DATA_EXPIRY_TIME = 10 * 60 * 1000 // 房间数据过期时间（10分钟，与后端删除逻辑对齐）
export const FINISHED_ROOM_EXPIRY_TIME = 15 * 1000 // 已结束房间的缓存时间（15秒，略大于后端10秒删除延迟）

// ============ 草稿画板相关 ============
export const CANVAS_LINE_WIDTHS = [1, 2, 4] // 可选的画笔粗细
export const CANVAS_ERASER_MULTIPLIER = 8 // 橡皮擦相对画笔的粗细倍数
export const CANVAS_MAX_HISTORY = 20 // 画布历史记录最大保存数量

// ============ 悬浮按钮相关 ============
export const FLOATING_BUTTON_SIZE = 56 // 悬浮按钮尺寸（像素）
export const FLOATING_BUTTON_DEFAULT_OFFSET = 80 // 悬浮按钮默认距边缘距离（像素）

// ============ 抽屉相关 ============
export const DRAWER_HEIGHT_VH = 60 // 抽屉高度（vh）
export const DRAWER_MAX_HEIGHT = 600 // 抽屉最大高度（像素）

// ============ WebSocket 相关 ============
export const WS_MAX_RECONNECT_ATTEMPTS = 2 // WebSocket最大重连次数（本地开发环境，简化重连）
export const WS_BASE_RECONNECT_DELAY = 1000 // WebSocket基础重连延迟（毫秒）
export const WS_RECONNECT_DELAY = 3000 // WebSocket自动重连延迟（毫秒）
export const WS_CONNECT_TIMEOUT = 15000 // WebSocket连接超时时间（毫秒）
export const WS_CONNECT_PROMISE_TIMEOUT = 20000 // WebSocket连接Promise超时（毫秒，必须大于CONNECT_TIMEOUT）

// 🔥 修复P1-5：定义WebSocket主题路径常量，确保前后端路径一致
// 🔥 P0-3修复：私聊使用user queue确保隐私安全
export const WS_TOPIC_PRIVATE_MESSAGE = '/user/queue/private' // 私聊消息主题（user queue）
export const WS_TOPIC_ROOM_CHAT = (roomCode) => `/topic/room/${roomCode}/chat` // 房间聊天主题
