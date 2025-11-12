// ============ Toast 通知相关 ============
export const TOAST_DEBOUNCE_TIME = 3000 // 相同Toast去重时间窗口（毫秒）
export const TOAST_CLEANUP_DELAY = 1000 // Toast记录清理延迟（毫秒）
export const TOAST_DEFAULT_LIFE = 3000 // Toast默认显示时长（毫秒）

// ============ API 请求相关 ============
export const API_TIMEOUT = 10000 // API请求超时时间（毫秒）

// ============ 本地存储相关 ============
export const ROOM_DATA_EXPIRY_TIME = 60 * 60 * 1000 // 房间数据过期时间（1小时）

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
export const WS_MAX_RECONNECT_ATTEMPTS = 5 // WebSocket最大重连次数
export const WS_BASE_RECONNECT_DELAY = 1000 // WebSocket基础重连延迟（毫秒）
export const WS_RECONNECT_DELAY = 3000 // WebSocket自动重连延迟（毫秒）
export const WS_CONNECT_TIMEOUT = 15000 // WebSocket连接超时时间（毫秒）
export const WS_CONNECT_PROMISE_TIMEOUT = 10000 // WebSocket连接Promise超时（毫秒）
