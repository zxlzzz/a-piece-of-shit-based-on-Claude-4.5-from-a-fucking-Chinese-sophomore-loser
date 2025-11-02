package org.example.websocket.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.service.game.GameService;
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

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        String playerName = (String) headerAccessor.getSessionAttributes().get("playerName");
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        String roomCode = (String) headerAccessor.getSessionAttributes().get("roomCode");

        if (playerName != null && roomCode != null && playerId != null) {
            log.info("玩家 {} 从房间 {} 断开连接", playerName, roomCode);

            // 🔥 只标记断线，不自动移除（未来会有房主踢人功能）
            gameService.handlePlayerDisconnect(roomCode, playerId);
        }
    }
}
