package org.example.websocket.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.ChatMessage;
import org.example.service.GameService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameService gameService;

    /**
     * 监听 WebSocket 断开连接事件
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // 从 session 中获取玩家信息
        String playerName = (String) headerAccessor.getSessionAttributes().get("playerName");
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        String roomCode = (String) headerAccessor.getSessionAttributes().get("roomCode");

        if (playerName != null && roomCode != null && playerId != null) {
            log.info("玩家 {} 从房间 {} 断开连接", playerName, roomCode);

            // 通知 GameService 处理断线
            try {
                gameService.handlePlayerDisconnect(roomCode, playerId);
            } catch (Exception e) {
                log.error("处理玩家断线失败", e);
            }

            // 创建断开连接消息
            ChatMessage disconnectMessage = ChatMessage.builder()
                    .type(ChatMessage.MessageType.LEAVE)
                    .roomCode(roomCode)
                    .content(playerName + " 断开连接")
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            // 广播断开连接消息
            messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/chat", disconnectMessage);
        }
    }
}
