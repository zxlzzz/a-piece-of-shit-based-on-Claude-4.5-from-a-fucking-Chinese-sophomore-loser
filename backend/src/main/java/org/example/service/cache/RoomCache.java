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
 * 房间缓存管理器 - 双层缓存架构
 * L1: 本地 ConcurrentHashMap（极快）
 * L2: Redis（持久化，支持重启恢复）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RoomCache {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * L1 缓存：本地内存缓存
     * Key: roomCode
     * Value: GameRoom
     */
    private final Map<String, GameRoom> localCache = new ConcurrentHashMap<>();
    private final Map<String, Long> roomCreationTime = new ConcurrentHashMap<>();

    // 房间过期时间（毫秒）：30分钟
    private static final long ROOM_EXPIRY_MS = 30 * 60 * 1000;

    // Redis key 前缀
    private static final String REDIS_KEY_PREFIX = "game:room:";

    /**
     * 存入房间（双写：本地缓存 + Redis）
     */
    public void put(String roomCode, GameRoom room) {
        // 1. 写入本地缓存
        localCache.put(roomCode, room);
        roomCreationTime.put(roomCode, System.currentTimeMillis());

        // 2. 写入 Redis（异步，30分钟过期）
        try {
            redisTemplate.opsForValue().set(
                getRedisKey(roomCode),
                room,
                ROOM_EXPIRY_MS,
                TimeUnit.MILLISECONDS
            );
            log.debug("✅ 房间 {} 已加入双层缓存（L1+Redis）", roomCode);
        } catch (Exception e) {
            log.error("❌ Redis 写入失败（roomCode={}），降级为本地缓存", roomCode, e);
            // 不抛异常，降级为本地缓存
        }
    }

    /**
     * 获取房间（双层缓存读取）
     * 优先从本地缓存读取，miss 时从 Redis 加载
     */
    public GameRoom get(String roomCode) {
        // 1. 先查本地缓存（L1）
        GameRoom room = localCache.get(roomCode);
        if (room != null) {
            // 检查是否过期
            if (isExpired(roomCode)) {
                remove(roomCode);
                return null;
            }
            return room;
        }

        // 2. 本地缓存 miss，查 Redis（L2）
        try {
            Object redisValue = redisTemplate.opsForValue().get(getRedisKey(roomCode));
            if (redisValue instanceof GameRoom) {
                room = (GameRoom) redisValue;
                // 加载到本地缓存
                localCache.put(roomCode, room);
                roomCreationTime.put(roomCode, System.currentTimeMillis());
                log.info("🔄 从 Redis 恢复房间: {}", roomCode);
                return room;
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
        GameRoom room = get(roomCode);  // 使用双层缓存的 get 方法
        if (room == null) {
            throw new BusinessException("房间不存在或已过期");
        }
        return room;
    }

    /**
     * 检查房间是否存在（双层检查）
     */
    public boolean exists(String roomCode) {
        // 先查本地缓存
        if (localCache.containsKey(roomCode)) {
            return true;
        }
        // 再查 Redis
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(getRedisKey(roomCode)));
        } catch (Exception e) {
            log.error("❌ Redis 检查失败（roomCode={}）", roomCode, e);
            return false;
        }
    }

    /**
     * 获取所有活跃房间（仅返回本地缓存中的房间）
     */
    public Collection<GameRoom> getAll() {
        return localCache.values();
    }

    /**
     * 获取房间数量（仅统计本地缓存）
     */
    public int size() {
        return localCache.size();
    }

    /**
     * 清空所有房间（慎用，仅用于测试或系统重置）
     */
    public void clear() {
        log.warn("⚠️ 清空所有房间缓存，当前房间数: {}", localCache.size());
        localCache.clear();
        roomCreationTime.clear();
        // 注意：不清空 Redis，保留持久化数据
    }

    /**
     * 移除房间（双删：本地缓存 + Redis）
     */
    public void remove(String roomCode) {
        // 1. 删除本地缓存
        localCache.remove(roomCode);
        roomCreationTime.remove(roomCode);

        // 2. 删除 Redis
        try {
            redisTemplate.delete(getRedisKey(roomCode));
            log.info("🗑️ 房间 {} 已从双层缓存移除", roomCode);
        } catch (Exception e) {
            log.error("❌ Redis 删除失败（roomCode={}）", roomCode, e);
        }
    }

    /**
     * 🔥 修复问题5：移除房间，Redis删除失败时重试
     */
    public void removeWithRetry(String roomCode) {
        // 1. 删除本地缓存
        localCache.remove(roomCode);
        roomCreationTime.remove(roomCode);

        // 2. 删除 Redis（带重试）
        String redisKey = getRedisKey(roomCode);
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                redisTemplate.delete(redisKey);
                log.info("🗑️ 房间 {} 已从双层缓存移除", roomCode);
                return; // 成功，直接返回
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    log.error("❌ Redis 删除失败（roomCode={}），已重试 {} 次", roomCode, maxRetries, e);
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
    }

    /**
     * 检查房间是否过期
     */
    private boolean isExpired(String roomCode) {
        Long createdAt = roomCreationTime.get(roomCode);
        if (createdAt == null) return false;
        return System.currentTimeMillis() - createdAt > ROOM_EXPIRY_MS;
    }

    /**
     * 获取 Redis Key
     */
    private String getRedisKey(String roomCode) {
        return REDIS_KEY_PREFIX + roomCode;
    }

    /**
     * 手动刷新房间到 Redis（用于定期持久化）
     */
    public void syncToRedis(String roomCode) {
        GameRoom room = localCache.get(roomCode);
        if (room != null) {
            try {
                redisTemplate.opsForValue().set(
                    getRedisKey(roomCode),
                    room,
                    ROOM_EXPIRY_MS,
                    TimeUnit.MILLISECONDS
                );
                log.debug("🔄 房间 {} 已同步到 Redis", roomCode);
            } catch (Exception e) {
                log.error("❌ Redis 同步失败（roomCode={}）", roomCode, e);
            }
        }
    }
}