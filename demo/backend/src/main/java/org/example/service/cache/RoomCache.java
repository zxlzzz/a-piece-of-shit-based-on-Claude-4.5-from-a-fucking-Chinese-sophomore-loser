package org.example.service.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.exception.BusinessException;
import org.example.pojo.GameRoom;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 🔥 P1-5修复：房间缓存管理器 - 使用Redis作为单一数据源
 *
 * 移除双层缓存架构，解决本地缓存和Redis不同步的问题
 * 使用Redis作为唯一缓存，确保数据一致性和重启后的恢复能力
 *
 * 性能考量：Redis本身非常快（亚毫秒级），对于房间管理场景足够
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RoomCache {

    private final RedisTemplate<String, Object> redisTemplate;

    // 房间过期时间（毫秒）：30分钟
    private static final long ROOM_EXPIRY_MS = 30 * 60 * 1000;

    // Redis key 前缀
    private static final String REDIS_KEY_PREFIX = "game:room:";

    /**
     * 🔥 P1-5修复：存入房间（仅写入Redis）
     */
    public void put(String roomCode, GameRoom room) {
        try {
            redisTemplate.opsForValue().set(
                getRedisKey(roomCode),
                room,
                ROOM_EXPIRY_MS,
                TimeUnit.MILLISECONDS
            );
            log.debug("✅ 房间 {} 已保存到Redis", roomCode);
        } catch (Exception e) {
            log.error("❌ Redis 写入失败（roomCode={}）", roomCode, e);
            throw new BusinessException("房间保存失败");
        }
    }

    /**
     * 🔥 P1-5修复：获取房间（仅从Redis读取）
     */
    public GameRoom get(String roomCode) {
        try {
            Object redisValue = redisTemplate.opsForValue().get(getRedisKey(roomCode));
            if (redisValue instanceof GameRoom) {
                return (GameRoom) redisValue;
            }
        } catch (Exception e) {
            log.error("❌ Redis 读取失败（roomCode={}）", roomCode, e);
        }
        return null;
    }

    /**
     * 获取房间（不存在则抛异常）
     */
    public GameRoom getOrThrow(String roomCode) {
        GameRoom room = get(roomCode);
        if (room == null) {
            throw new BusinessException("房间不存在或已过期");
        }
        return room;
    }

    /**
     * 🔥 P1-5修复：检查房间是否存在（仅检查Redis）
     */
    public boolean exists(String roomCode) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(getRedisKey(roomCode)));
        } catch (Exception e) {
            log.error("❌ Redis 检查失败（roomCode={}）", roomCode, e);
            return false;
        }
    }

    /**
     * 🔥 P1-5修复：获取所有活跃房间（从Redis扫描）
     * 注意：这个操作比较昂贵，谨慎使用
     */
    public Collection<GameRoom> getAll() {
        try {
            java.util.Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return java.util.Collections.emptyList();
            }

            java.util.List<GameRoom> rooms = new java.util.ArrayList<>();
            for (String key : keys) {
                Object value = redisTemplate.opsForValue().get(key);
                if (value instanceof GameRoom) {
                    rooms.add((GameRoom) value);
                }
            }
            return rooms;
        } catch (Exception e) {
            log.error("❌ Redis扫描失败", e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 🔥 P1-5修复：获取房间数量（从Redis统计）
     */
    public int size() {
        try {
            java.util.Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("❌ Redis统计失败", e);
            return 0;
        }
    }

    /**
     * 🔥 P1-5修复：清空所有房间（删除Redis中所有房间）
     */
    public void clear() {
        try {
            java.util.Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                log.warn("⚠️ 清空所有房间缓存，当前房间数: {}", keys.size());
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.error("❌ Redis清空失败", e);
        }
    }

    /**
     * 🔥 P1-5修复：移除房间（仅删除Redis）
     */
    public void remove(String roomCode) {
        try {
            redisTemplate.delete(getRedisKey(roomCode));
            log.info("🗑️ 房间 {} 已从Redis移除", roomCode);
        } catch (Exception e) {
            log.error("❌ Redis 删除失败（roomCode={}）", roomCode, e);
        }
    }

    /**
     * 🔥 P1-5修复：移除房间（带重试机制）
     * 修复：删除失败后记录错误，但不中断流程（数据库删除更重要）
     */
    public void removeWithRetry(String roomCode) {
        String redisKey = getRedisKey(roomCode);
        int maxRetries = 3;
        boolean deleted = false;

        for (int i = 0; i < maxRetries; i++) {
            try {
                redisTemplate.delete(redisKey);
                log.info("🗑️ 房间 {} 已从Redis移除", roomCode);
                deleted = true;
                return; // 成功，直接返回
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    log.error("❌ Redis 删除失败（roomCode={}），已重试 {} 次，可能导致缓存残留", roomCode, maxRetries, e);
                } else {
                    log.warn("⚠️ Redis 删除失败（roomCode={}），第 {}/{} 次重试...", roomCode, i + 1, maxRetries);
                    try {
                        Thread.sleep(100 * (i + 1)); // 递增延迟：100ms, 200ms, 300ms
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // 🔥 修复：即使删除失败，也要记录警告日志（方便排查缓存残留问题）
        if (!deleted) {
            log.warn("⚠️ 房间 {} 的Redis缓存可能未完全删除，将依赖TTL自动过期", roomCode);
        }
    }

    /**
     * 🔥 P1-5修复：同步房间到Redis（用于修改GameRoom后持久化）
     * 从Redis读取最新对象，修改后调用此方法保存
     */
    /**
     * 同步GameRoom到Redis
     * @param roomCode 房间代码
     * @param gameRoom 要同步的GameRoom对象
     */
    public void syncToRedis(String roomCode, GameRoom gameRoom) {
        if (gameRoom != null) {
            put(roomCode, gameRoom);
        } else {
            log.warn("⚠️ 尝试同步null的房间对象: {}", roomCode);
        }
    }

    /**
     * 获取 Redis Key
     */
    private String getRedisKey(String roomCode) {
        return REDIS_KEY_PREFIX + roomCode;
    }
}