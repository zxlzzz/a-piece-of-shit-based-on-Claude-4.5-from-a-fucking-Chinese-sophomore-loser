package org.example.service.broadcast;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.RoomDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 房间状态广播器
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoomStateBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 广播房间状态更新
     */
    public void sendRoomUpdate(String roomCode, RoomDTO room) {
        if (room == null) {
            log.warn("⚠️ 尝试广播空房间状态, roomCode={}", roomCode);
            return;
        }
        try {
            messagingTemplate.convertAndSend("/topic/room/" + roomCode, room);
            log.info("✅ 广播房间更新: roomCode={}, status={}", roomCode, room.getStatus());
        } catch (Exception e) {
            log.error("❌ 广播房间更新失败, roomCode={}: {}", roomCode, e.getMessage(), e);
        }
    }

    /**
     * 广播房间删除消息
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
}