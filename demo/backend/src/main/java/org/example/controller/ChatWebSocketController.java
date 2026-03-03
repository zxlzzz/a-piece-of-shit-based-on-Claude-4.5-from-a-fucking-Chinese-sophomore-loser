package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.ChatMessage;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

/**
 * 聊天 WebSocket 控制器
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 发送聊天消息
     */
    @MessageMapping("/chat/{roomCode}")
    public void sendMessage(@DestinationVariable String roomCode,
                            @Payload ChatMessage message) {
        try {
            message.setTimestamp(LocalDateTime.now());
            message.setRoomCode(roomCode);

            // 私聊消息
            if (message.getRecipientIds() != null && !message.getRecipientIds().isEmpty()) {
                message.setIsPrivate(true);
                for (String recipientId : message.getRecipientIds()) {
                    messagingTemplate.convertAndSendToUser(recipientId, "/queue/private", message);
                }
                messagingTemplate.convertAndSendToUser(message.getSenderId(), "/queue/private", message);
            } else {
                // 公共消息
                message.setIsPrivate(false);
                messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/chat", message);
            }
        } catch (Exception e) {
            log.error("发送聊天消息失败: roomCode={}, senderId={}", roomCode, message.getSenderId(), e);
        }
    }
}
