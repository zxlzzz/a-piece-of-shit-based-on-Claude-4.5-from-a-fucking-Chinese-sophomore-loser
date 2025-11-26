package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.ChatMessage;
import org.example.pojo.GameRoom;
import org.example.service.cache.RoomCache;
import org.example.service.chat.ChatRoomManager;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomManager chatRoomManager;
    private final RoomCache roomCache;

    /**
     * 发送聊天消息
     * 客户端发送到: /app/chat/{roomCode}
     * 广播到: /topic/room/{roomCode}/chat (公共消息)
     * 或点对点发送到: /user/queue/private (私聊消息)
     */
    @MessageMapping("/chat/{roomCode}")
    public void sendMessage(@DestinationVariable String roomCode,
                            @Payload ChatMessage message) {
        try {
            // 设置时间戳
            message.setTimestamp(LocalDateTime.now());
            message.setRoomCode(roomCode);

            // 🔥 检查发送者是否为观战者
            GameRoom gameRoom = roomCache.get(roomCode);
            if (gameRoom != null) {
                boolean isSpectator = gameRoom.getPlayers().stream()
                        .filter(p -> p.getPlayerId().equals(message.getSenderId()))
                        .findFirst()
                        .map(p -> Boolean.TRUE.equals(p.getSpectator()))
                        .orElse(false);
                message.setIsSpectator(isSpectator);
            }

            // 🔥 记录聊天室活动

            // 🔥 判断是否私聊消息
            if (message.getRecipientIds() != null && !message.getRecipientIds().isEmpty()) {
                // 私聊消息：点对点发送
                message.setIsPrivate(true);

                // 🔥 P0-3修复：使用user queue确保隐私，而非topic
                // 发送给所有收件人
                for (String recipientId : message.getRecipientIds()) {
                    messagingTemplate.convertAndSendToUser(
                        recipientId,
                        "/queue/private",
                        message
                    );
                }

                // 也发送给发送者自己（让发送者看到自己发的私聊）
                messagingTemplate.convertAndSendToUser(
                    message.getSenderId(),
                    "/queue/private",
                    message
                );
            } else {
                // 公共消息：广播到房间的所有订阅者
                message.setIsPrivate(false);
                messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/chat", message);
            }
        } catch (Exception e) {
            log.error("🔥 发送聊天消息失败: roomCode={}, senderId={}", roomCode, message.getSenderId(), e);
            // 发送错误通知给发送者
            try {
                // 🔥 P0-3修复：错误通知也使用user queue
                ChatMessage errorMsg = ChatMessage.system(roomCode, "消息发送失败，请重试");
                messagingTemplate.convertAndSendToUser(
                    message.getSenderId(),
                    "/queue/private",
                    errorMsg
                );
            } catch (Exception ex) {
                log.error("发送错误通知失败", ex);
            }
        }
    }

    /**
     * 玩家加入房间
     */
    @MessageMapping("/room/{roomCode}/join")
    public void playerJoin(@DestinationVariable String roomCode,
                           @Payload ChatMessage message,
                           SimpMessageHeaderAccessor headerAccessor) {
        try {
            // 保存玩家信息到 WebSocket session
            headerAccessor.getSessionAttributes().put("playerId", message.getSenderId());
            headerAccessor.getSessionAttributes().put("playerName", message.getSenderName());
            headerAccessor.getSessionAttributes().put("roomCode", roomCode);

            // 🔥 记录玩家加入聊天室
            chatRoomManager.playerJoin(roomCode, message.getSenderId());

            // 创建加入消息
            ChatMessage joinMessage = ChatMessage.join(roomCode, message.getSenderName());


            // 广播加入消息
            messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/chat", joinMessage);
        } catch (Exception e) {
            log.error("🔥 玩家加入房间失败: roomCode={}, playerId={}", roomCode, message.getSenderId(), e);
        }
    }

    /**
     * 玩家准备状态变更
     */
    @MessageMapping("/room/{roomCode}/ready")
    public void playerReady(@DestinationVariable String roomCode,
                            @Payload ChatMessage message) {
        try {
            // 从消息内容中判断是准备还是取消准备
            boolean isReady = message.getType() == ChatMessage.MessageType.READY;

            ChatMessage readyMessage = ChatMessage.ready(roomCode, message.getSenderName(), isReady);


            // 广播准备消息
            messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/chat", readyMessage);
        } catch (Exception e) {
            log.error("🔥 玩家准备状态变更失败: roomCode={}, playerId={}", roomCode, message.getSenderId(), e);
        }
    }

    /**
     * 发送系统消息（供其他服务调用）
     */
    public void sendSystemMessage(String roomCode, String content) {
        ChatMessage systemMessage = ChatMessage.system(roomCode, content);
        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/chat", systemMessage);
    }

    /**
     * 广播游戏开始消息
     */
    public void broadcastGameStart(String roomCode) {
        ChatMessage message = ChatMessage.builder()
                .type(ChatMessage.MessageType.GAME_START)
                .roomCode(roomCode)
                .content("游戏开始！")
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/chat", message);
    }

    /**
     * 广播游戏结束消息
     */
    public void broadcastGameEnd(String roomCode) {
        ChatMessage message = ChatMessage.builder()
                .type(ChatMessage.MessageType.GAME_END)
                .roomCode(roomCode)
                .content("游戏结束！")
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/chat", message);
    }
}
