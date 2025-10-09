package org.example.controller;

import org.example.exception.BusinessException;
import org.example.pojo.JoinRequest;
import org.example.dto.RoomDTO;
import org.example.pojo.SubmitRequest;
import org.example.service.GameService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
public class RoomWsController {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public RoomWsController(GameService gameService, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 加入房间
     * 前端发送: /app/join
     * payload: { "roomCode": "ABC123", "playerId": "123", "playerName": "Tom" }
     */
    @MessageMapping("/join")
    public void handleJoin(@Payload JoinRequest request) {
        try {
            RoomDTO room = gameService.joinRoom(request.getRoomCode(),
                    request.getPlayerId(), request.getPlayerName());

            // 广播房间更新
            broadcastRoomUpdate(room);

            // 给加入的玩家发送欢迎消息
            messagingTemplate.convertAndSendToUser(
                    request.getPlayerId(),
                    "/queue/welcome",
                    Map.of("message", "欢迎加入房间 " + request.getRoomCode(), "room", room)
            );

            log.info("WebSocket: 玩家 {} 加入房间 {}", request.getPlayerName(), request.getRoomCode());
        } catch (BusinessException e) {
            // 发送错误消息给请求的玩家
            sendErrorToPlayer(request.getPlayerId(), e.getMessage());
        }
    }

    /**
     * 开始游戏
     * 前端发送: /app/start
     * payload: { "roomCode": "ABC123" }
     */
    @MessageMapping("/start")
    public void handleStart(@Payload Map<String, String> request) {
        try {
            String roomCode = request.get("roomCode");
            RoomDTO room = gameService.startGame(roomCode);

            // 广播游戏开始
            broadcastRoomUpdate(room);

            log.info("WebSocket: 房间 {} 开始游戏", roomCode);
        } catch (BusinessException e) {
            // 广播错误消息到房间
            String roomCode = request.get("roomCode");
            sendErrorToRoom(roomCode, e.getMessage());
        }
    }

    /**
     * 提交答案
     * 前端发送: /app/submit
     * payload: { "roomCode": "ABC123", "playerId": "123", "choice": "A", "force": false }
     */
    @MessageMapping("/submit")
    public void handleSubmit(@Payload SubmitRequest request) {
        try {
            RoomDTO room = gameService.submitAnswer(
                    request.getRoomCode(),
                    request.getPlayerId(),
                    request.getChoice(),
                    request.isForce()
            );

            // 广播房间更新
            broadcastRoomUpdate(room);

            log.info("WebSocket: 玩家 {} 提交答案: {}", request.getPlayerId(), request.getChoice());
        } catch (BusinessException e) {
            sendErrorToPlayer(request.getPlayerId(), e.getMessage());
        }
    }

    /**
     * 设置准备状态
     * 前端发送: /app/ready
     * payload: { "roomCode": "ABC123", "playerId": "123", "ready": true }
     */
    @MessageMapping("/ready")
    public void handleReady(@Payload Map<String, Object> request) {
        try {
            String roomCode = (String) request.get("roomCode");
            String playerId = (String) request.get("playerId");
            boolean ready = (Boolean) request.get("ready");

            RoomDTO room = gameService.setPlayerReady(roomCode, playerId, ready);

            // 广播房间更新
            broadcastRoomUpdate(room);

            log.info("WebSocket: 玩家 {} 设置准备状态: {}", playerId, ready);
        } catch (BusinessException e) {
            String playerId = (String) request.get("playerId");
            sendErrorToPlayer(playerId, e.getMessage());
        }
    }

    /**
     * 离开房间
     * 前端发送: /app/leave
     * payload: { "roomCode": "ABC123", "playerId": "123" }
     */
    @MessageMapping("/leave")
    public void handleLeave(@Payload Map<String, String> payload) {
        String roomCode = payload.get("roomCode");
        String playerId = payload.get("playerId");

        try {
            RoomDTO room = gameService.leaveRoom(roomCode, playerId);

            if (room != null) {
                // 房间还在，广播更新给所有人
                messagingTemplate.convertAndSend("/topic/room/" + roomCode, room);
            } else {
                // 房间已解散，广播删除消息
                messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/deleted",
                        Map.of("message", "房间已解散"));
            }
        } catch (Exception e) {
            log.error("处理离开请求失败", e);
            messagingTemplate.convertAndSendToUser(playerId, "/queue/error",
                    Map.of("error", e.getMessage()));
        }
    }



    /**
     * 广播房间更新
     */
    private void broadcastRoomUpdate(RoomDTO room) {
        if (room == null) return;
        try {
            messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(), room);
        } catch (Exception e) {
            log.warn("广播房间更新失败, roomCode={}: {}", room.getRoomCode(), e.getMessage());
        }
    }

    /**
     * 发送错误消息给特定玩家
     */
    private void sendErrorToPlayer(String playerId, String errorMessage) {
        try {
            messagingTemplate.convertAndSendToUser(
                    playerId,
                    "/queue/error",
                    Map.of("error", errorMessage, "timestamp", System.currentTimeMillis())
            );
        } catch (Exception e) {
            log.warn("发送错误消息失败, playerId={}: {}", playerId, e.getMessage());
        }
    }

    /**
     * 发送错误消息到房间
     */
    private void sendErrorToRoom(String roomCode, String errorMessage) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomCode + "/error",
                    Map.of("error", errorMessage, "timestamp", System.currentTimeMillis())
            );
        } catch (Exception e) {
            log.warn("发送房间错误消息失败, roomCode={}: {}", roomCode, e.getMessage());
        }
    }
}

