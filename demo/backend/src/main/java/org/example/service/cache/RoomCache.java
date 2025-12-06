package org.example.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.example.exception.BusinessException;
import org.example.pojo.GameRoom;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间缓存管理器（内存实现）
 */
@Component
@Slf4j
public class RoomCache {

    private final Map<String, GameRoom> roomMap = new ConcurrentHashMap<>();

    public void put(String roomCode, GameRoom room) {
        roomMap.put(roomCode, room);
        log.debug("✅ 房间 {} 已保存到内存", roomCode);
    }

    public GameRoom get(String roomCode) {
        return roomMap.get(roomCode);
    }

    public GameRoom getOrThrow(String roomCode) {
        GameRoom room = get(roomCode);
        if (room == null) {
            throw new BusinessException("房间不存在或已过期");
        }
        return room;
    }

    public void remove(String roomCode) {
        roomMap.remove(roomCode);
        log.info("🗑️ 房间 {} 已从内存移除", roomCode);
    }

    public Collection<GameRoom> getAll() {
        return roomMap.values();
    }
}
