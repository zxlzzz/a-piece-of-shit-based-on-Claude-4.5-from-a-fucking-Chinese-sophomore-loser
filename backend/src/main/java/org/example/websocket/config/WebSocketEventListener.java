package org.example.websocket.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.ChatMessage;
import org.example.pojo.GameRoom;
import org.example.service.GameService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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

            // 🔥 只标记断线，不立即处理
            gameService.handlePlayerDisconnect(roomCode, playerId);

            // 🔥 延迟处理：5秒后检查是否重连
            CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
                try {
                    GameRoom gameRoom = gameService.getGameRoom(roomCode); // 🔥 需要添加这个方法
                    if (gameRoom != null) {
                        LocalDateTime disconnectTime = gameRoom.getDisconnectedPlayers().get(playerId);

                        // 如果5秒后还在断线列表，说明没重连
                        if (disconnectTime != null &&
                                Duration.between(disconnectTime, LocalDateTime.now()).getSeconds() >= 5) {

                            log.warn("玩家 {} 超时未重连，移除出房间", playerId);
                            gameService.removeDisconnectedPlayer(roomCode, playerId); // 🔥 需要添加这个方法

                            // 广播断线消息
                            ChatMessage disconnectMessage = ChatMessage.builder()
                                    .type(ChatMessage.MessageType.LEAVE)
                                    .roomCode(roomCode)
                                    .content(playerName + " 已离开房间")
                                    .timestamp(LocalDateTime.now())
                                    .build();
                            messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/chat", disconnectMessage);
                        }
                    }
                } catch (Exception e) {
                    log.error("处理玩家超时断线失败", e);
                }
            });
        }
    }
}
