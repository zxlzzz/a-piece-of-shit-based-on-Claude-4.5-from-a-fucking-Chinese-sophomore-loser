package org.example.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.example.exception.BusinessException;
import org.example.pojo.GameRoom;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间缓存管理器
 * 负责管理活跃房间的内存状态
 */
@Component
@Slf4j
public class RoomCache {

    /**
     * 活跃房间缓存
     * Key: roomCode
     * Value: GameRoom（内存中的运行时状态）
     */
    private final Map<String, GameRoom> activeRooms = new ConcurrentHashMap<>();
    private final Map<String, Long> roomCreationTime = new ConcurrentHashMap<>();

    // 房间过期时间（毫秒）：30分钟
    private static final long ROOM_EXPIRY_MS = 30 * 60 * 1000;

    /**
     * 存入房间
     */
    public void put(String roomCode, GameRoom room) {
        activeRooms.put(roomCode, room);
        roomCreationTime.put(roomCode, System.currentTimeMillis());
        log.debug("房间 {} 已加入缓存", roomCode);
    }

    /**
     * 获取房间（可能为 null）
     */
    public GameRoom get(String roomCode) {
        // ✅ 获取时检查过期
        if (isExpired(roomCode)) {
            remove(roomCode);
            return null;
        }
        return activeRooms.get(roomCode);
    }

    /**
     * 获取房间（不存在则抛异常）
     */
    public GameRoom getOrThrow(String roomCode) {
        GameRoom room = activeRooms.get(roomCode);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        return room;
    }

    /**
     * 检查房间是否存在
     */
    public boolean exists(String roomCode) {
        return activeRooms.containsKey(roomCode);
    }

    /**
     * 获取所有活跃房间
     */
    public Collection<GameRoom> getAll() {
        return activeRooms.values();
    }

    /**
     * 获取房间数量
     */
    public int size() {
        return activeRooms.size();
    }

    /**
     * 清空所有房间（慎用，仅用于测试或系统重置）
     */
    public void clear() {
        log.warn("清空所有房间缓存，当前房间数: {}", activeRooms.size());
        activeRooms.clear();
    }

    public void remove(String roomCode) {
        activeRooms.remove(roomCode);
        roomCreationTime.remove(roomCode);
        log.info("房间 {} 已从缓存移除", roomCode);
    }

    private boolean isExpired(String roomCode) {
        Long createdAt = roomCreationTime.get(roomCode);
        if (createdAt == null) return false;

        return System.currentTimeMillis() - createdAt > ROOM_EXPIRY_MS;
    }
}