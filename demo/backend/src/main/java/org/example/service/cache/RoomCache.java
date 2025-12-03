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
 * 房间缓存管理器
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RoomCache {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final long ROOM_EXPIRY_MS = 30 * 60 * 1000;
    private static final String REDIS_KEY_PREFIX = "game:room:";

    public void put(String roomCode, GameRoom room) {
        try {
            redisTemplate.opsForValue().set(
                getRedisKey(roomCode),
                room,
                ROOM_EXPIRY_MS,
                TimeUnit.MILLISECONDS
            );
            log.debug(" 房间 {} 已保存到Redis", roomCode);
        } catch (Exception e) {
            log.error(" Redis 写入失败（roomCode={}）", roomCode, e);
            throw new BusinessException("房间保存失败");
        }
    }

    public GameRoom get(String roomCode) {
        try {
            Object redisValue = redisTemplate.opsForValue().get(getRedisKey(roomCode));
            if (redisValue instanceof GameRoom) {
                return (GameRoom) redisValue;
            }
        } catch (Exception e) {
            log.error(" Redis 读取失败（roomCode={}）", roomCode, e);
        }
        return null;
    }

    public GameRoom getOrThrow(String roomCode) {
        GameRoom room = get(roomCode);
        if (room == null) {
            throw new BusinessException("房间不存在或已过期");
        }
        return room;
    }

    public boolean exists(String roomCode) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(getRedisKey(roomCode)));
        } catch (Exception e) {
            log.error(" Redis 检查失败（roomCode={}）", roomCode, e);
            return false;
        }
    }

    public void remove(String roomCode) {
        try {
            redisTemplate.delete(getRedisKey(roomCode));
            log.info("🗑️ 房间 {} 已从Redis移除", roomCode);
        } catch (Exception e) {
            log.error(" Redis 删除失败（roomCode={}）", roomCode, e);
        }
    }

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

    private String getRedisKey(String roomCode) {
        return REDIS_KEY_PREFIX + roomCode;
    }
}