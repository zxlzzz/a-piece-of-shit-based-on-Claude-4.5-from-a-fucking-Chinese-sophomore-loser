package org.example.service.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 聊天室管理器
 */
@Service
@Slf4j
public class ChatRoomManager {

    private final Map<String, Set<String>> chatRoomUsers = new ConcurrentHashMap<>();

    public void playerJoin(String roomCode, String playerId) {
        chatRoomUsers.computeIfAbsent(roomCode, k -> new CopyOnWriteArraySet<>()).add(playerId);
        log.debug("玩家 {} 加入聊天室 {}", playerId, roomCode);
    }

}
