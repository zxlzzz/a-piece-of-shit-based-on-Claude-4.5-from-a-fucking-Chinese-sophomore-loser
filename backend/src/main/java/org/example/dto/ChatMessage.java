package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessage {
    /**
            * 消息类型
     */
    private MessageType type;

    /**
     * 房间代码
     */
    private String roomCode;

    /**
     * 发送者ID
     */
    private String senderId;

    /**
     * 发送者昵称
     */
    private String senderName;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 发送时间
     */
    private LocalDateTime timestamp;

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        CHAT,           // 普通聊天消息
        SYSTEM,         // 系统消息
        JOIN,           // 玩家加入
        LEAVE,          // 玩家离开
        READY,          // 玩家准备
        UNREADY,        // 取消准备
        GAME_START,     // 游戏开始
        GAME_END        // 游戏结束
    }

    /**
     * 创建聊天消息
     */
    public static ChatMessage chat(String roomCode, String senderId, String senderName, String content) {
        return ChatMessage.builder()
                .type(MessageType.CHAT)
                .roomCode(roomCode)
                .senderId(senderId)
                .senderName(senderName)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建系统消息
     */
    public static ChatMessage system(String roomCode, String content) {
        return ChatMessage.builder()
                .type(MessageType.SYSTEM)
                .roomCode(roomCode)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建玩家加入消息
     */
    public static ChatMessage join(String roomCode, String playerName) {
        return ChatMessage.builder()
                .type(MessageType.JOIN)
                .roomCode(roomCode)
                .content(playerName + " 进入了房间")
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建玩家离开消息
     */
    public static ChatMessage leave(String roomCode, String playerName) {
        return ChatMessage.builder()
                .type(MessageType.LEAVE)
                .roomCode(roomCode)
                .content(playerName + " 离开了房间")
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建玩家准备消息
     */
    public static ChatMessage ready(String roomCode, String playerName, boolean isReady) {
        String action = isReady ? "已准备" : "取消准备";
        return ChatMessage.builder()
                .type(isReady ? MessageType.READY : MessageType.UNREADY)
                .roomCode(roomCode)
                .content(playerName + " " + action)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
