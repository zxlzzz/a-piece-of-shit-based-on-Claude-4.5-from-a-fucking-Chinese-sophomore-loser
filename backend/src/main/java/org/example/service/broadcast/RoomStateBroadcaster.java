package org.example.service.broadcast;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.RoomDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 房间状态广播器
 * 统一管理 WebSocket 消息推送
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoomStateBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 广播房间状态更新
     * 发送到: /topic/room/{roomCode}
     */
    public void sendRoomUpdate(String roomCode, RoomDTO room) {
        if (room == null) {
            log.warn("⚠️ 尝试广播空房间状态, roomCode={}", roomCode);
            return;
        }
        try {
            messagingTemplate.convertAndSend("/topic/room/" + roomCode, room);
            log.debug("✅ 广播房间更新: {}", roomCode);
        } catch (Exception e) {
            log.error("❌ 广播房间更新失败, roomCode={}: {}", roomCode, e.getMessage());
        }
    }

    /**
     * 广播房间删除消息
     * 发送到: /topic/room/{roomCode}/deleted
     */
    public void sendRoomDeleted(String roomCode) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomCode + "/deleted",
                    Map.of("message", "房间已删除", "roomCode", roomCode)
            );
            log.info("✅ 广播房间删除: {}", roomCode);
        } catch (Exception e) {
            log.error("❌ 广播房间删除失败, roomCode={}: {}", roomCode, e.getMessage());
        }
    }

    /**
     * 通知玩家被踢出
     * 🔥 使用 topic 而不是 user queue，更简单可靠
     * 发送到: /topic/player/{playerId}/kicked
     */
    public void sendPlayerKicked(String roomCode, String playerId) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/player/" + playerId + "/kicked",
                    Map.of("message", "您已被房主踢出房间", "roomCode", roomCode)
            );
            log.info("✅ 通知玩家 {} 被踢出房间 {}", playerId, roomCode);
        } catch (Exception e) {
            log.error("❌ 通知玩家被踢出失败, playerId={}: {}", playerId, e.getMessage());
        }
    }

    /**
     * 发送错误消息给特定玩家
     * 发送到: /user/{playerId}/queue/error
     */
    public void sendErrorToPlayer(String playerId, String errorMessage) {
        try {
            messagingTemplate.convertAndSendToUser(
                    playerId,
                    "/queue/error",
                    Map.of("error", errorMessage, "timestamp", System.currentTimeMillis())
            );
            log.debug("✅ 发送错误给玩家 {}: {}", playerId, errorMessage);
        } catch (Exception e) {
            log.error("❌ 发送玩家错误失败, playerId={}: {}", playerId, e.getMessage());
        }
    }

    /**
     * 发送错误消息到房间
     * 发送到: /topic/room/{roomCode}/error
     */
    public void sendErrorToRoom(String roomCode, String errorMessage) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomCode + "/error",
                    Map.of("error", errorMessage, "timestamp", System.currentTimeMillis())
            );
            log.debug("✅ 发送错误到房间 {}: {}", roomCode, errorMessage);
        } catch (Exception e) {
            log.error("❌ 发送房间错误失败, roomCode={}: {}", roomCode, e.getMessage());
        }
    }

    /**
     * 发送欢迎消息给玩家
     * 发送到: /user/{playerId}/queue/welcome
     */
    public void sendWelcomeToPlayer(String playerId, String roomCode, RoomDTO room) {
        try {
            messagingTemplate.convertAndSendToUser(
                    playerId,
                    "/queue/welcome",
                    Map.of("message", "欢迎加入房间 " + roomCode, "room", room)
            );
            log.debug("✅ 发送欢迎消息给玩家 {}", playerId);
        } catch (Exception e) {
            log.error("❌ 发送欢迎消息失败, playerId={}: {}", playerId, e.getMessage());
        }
    }
}