package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.ChatMessage;
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

    /**
     * 发送聊天消息
     * 客户端发送到: /app/chat/{roomCode}
     * 广播到: /topic/room/{roomCode}/chat
     */
    @MessageMapping("/chat/{roomCode}")
    public void sendMessage(@DestinationVariable String roomCode,
                            @Payload ChatMessage message) {
        // 设置时间戳
        message.setTimestamp(LocalDateTime.now());
        message.setRoomCode(roomCode);

        log.info("房间 {} 收到消息: {} - {}", roomCode, message.getSenderName(), message.getContent());

        // 广播到房间的所有订阅者
        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/chat", message);
    }

    /**
     * 玩家加入房间
     */
    @MessageMapping("/room/{roomCode}/join")
    public void playerJoin(@DestinationVariable String roomCode,
                           @Payload ChatMessage message,
                           SimpMessageHeaderAccessor headerAccessor) {

        // 保存玩家信息到 WebSocket session
        headerAccessor.getSessionAttributes().put("playerId", message.getSenderId());
        headerAccessor.getSessionAttributes().put("playerName", message.getSenderName());
        headerAccessor.getSessionAttributes().put("roomCode", roomCode);

        // 创建加入消息
        ChatMessage joinMessage = ChatMessage.join(roomCode, message.getSenderName());

        log.info("玩家 {} 加入房间 {}", message.getSenderName(), roomCode);

        // 广播加入消息
        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/chat", joinMessage);
    }

    /**
     * 玩家准备状态变更
     */
    @MessageMapping("/room/{roomCode}/ready")
    public void playerReady(@DestinationVariable String roomCode,
                            @Payload ChatMessage message) {

        // 从消息内容中判断是准备还是取消准备
        boolean isReady = message.getType() == ChatMessage.MessageType.READY;

        ChatMessage readyMessage = ChatMessage.ready(roomCode, message.getSenderName(), isReady);

        log.info("玩家 {} 在房间 {} 中{}", message.getSenderName(), roomCode,
                isReady ? "已准备" : "取消准备");

        // 广播准备消息
        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/chat", readyMessage);
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
