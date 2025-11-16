package org.example.service.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 聊天室管理器
 * 管理聊天室的生命周期，支持房间删除后继续存在
 */
@Service
@Slf4j
public class ChatRoomManager {

    // 聊天室最后活跃时间（roomCode -> lastActivityTime）
    private final Map<String, LocalDateTime> chatRoomLastActivity = new ConcurrentHashMap<>();

    // 聊天室在线用户（roomCode -> Set<playerId>）
    private final Map<String, Set<String>> chatRoomUsers = new ConcurrentHashMap<>();

    // 聊天室存在标记（即使房间删除也会保留）
    private final Set<String> activeChatRooms = ConcurrentHashMap.newKeySet();

    /**
     * 记录聊天室消息活动
     */
    public void recordActivity(String roomCode) {
        chatRoomLastActivity.put(roomCode, LocalDateTime.now());
        activeChatRooms.add(roomCode);
        log.debug("聊天室 {} 活动更新", roomCode);
    }

    /**
     * 玩家加入聊天室
     */
    public void playerJoin(String roomCode, String playerId) {
        chatRoomUsers.computeIfAbsent(roomCode, k -> new CopyOnWriteArraySet<>()).add(playerId);
        recordActivity(roomCode);
        log.debug("玩家 {} 加入聊天室 {}，当前在线: {}", playerId, roomCode, chatRoomUsers.get(roomCode).size());
    }

    /**
     * 玩家离开聊天室
     */
    public void playerLeave(String roomCode, String playerId) {
        Set<String> users = chatRoomUsers.get(roomCode);
        if (users != null) {
            users.remove(playerId);
            log.debug("玩家 {} 离开聊天室 {}，剩余在线: {}", playerId, roomCode, users.size());

            // 如果没有人了，记录最后活跃时间
            if (users.isEmpty()) {
                log.debug("聊天室 {} 已无在线用户", roomCode);
            }
        }
    }

    /**
     * 获取聊天室在线人数
     */
    public int getOnlineCount(String roomCode) {
        Set<String> users = chatRoomUsers.get(roomCode);
        return users != null ? users.size() : 0;
    }

    /**
     * 检查聊天室是否应该被清理
     * 清理条件：
     * 1. 无在线用户
     * 2. 最后活跃时间超过5分钟
     */
    @Scheduled(fixedRate = 60000) // 每分钟检查一次
    public void cleanupInactiveChatRooms() {
        LocalDateTime now = LocalDateTime.now();
        int cleaned = 0;

        for (String roomCode : activeChatRooms) {
            LocalDateTime lastActivity = chatRoomLastActivity.get(roomCode);
            int onlineCount = getOnlineCount(roomCode);

            // 如果有人在线，不清理
            if (onlineCount > 0) {
                continue;
            }

            // 如果没有活跃记录，或者超过5分钟无活动，清理
            if (lastActivity == null || lastActivity.plusMinutes(5).isBefore(now)) {
                log.info("清理不活跃聊天室: {}，最后活跃时间: {}", roomCode, lastActivity);
                activeChatRooms.remove(roomCode);
                chatRoomLastActivity.remove(roomCode);
                chatRoomUsers.remove(roomCode);
                cleaned++;
            }
        }

        if (cleaned > 0) {
            log.info("本次清理了 {} 个不活跃聊天室", cleaned);
        }
    }

    /**
     * 获取活跃聊天室数量
     */
    public int getActiveChatRoomCount() {
        return activeChatRooms.size();
    }

    /**
     * 获取聊天室统计信息
     */
    public Map<String, Object> getStats() {
        return Map.of(
                "activeChatRooms", activeChatRooms.size(),
                "totalOnlineUsers", chatRoomUsers.values().stream().mapToInt(Set::size).sum()
        );
    }
}
