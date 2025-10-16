package org.example.controller;

import org.example.exception.BusinessException;
import org.example.pojo.JoinRequest;
import org.example.dto.RoomDTO;
import org.example.pojo.SubmitRequest;
import org.example.service.GameService;
import org.example.service.broadcast.RoomStateBroadcaster;
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
    private final RoomStateBroadcaster broadcaster; // 🔥 改用 broadcaster

    public RoomWsController(GameService gameService, RoomStateBroadcaster broadcaster) {
        this.gameService = gameService;
        this.broadcaster = broadcaster;
    }

    @MessageMapping("/join")
    public void handleJoin(@Payload JoinRequest request) {
        try {
            RoomDTO room = gameService.joinRoom(request.getRoomCode(),
                    request.getPlayerId(), request.getPlayerName());

            // 🔥 改用 broadcaster
            broadcaster.sendRoomUpdate(request.getRoomCode(), room);
            broadcaster.sendWelcomeToPlayer(request.getPlayerId(), request.getRoomCode(), room);

            log.info("✅ WebSocket: 玩家 {} 加入房间 {}", request.getPlayerName(), request.getRoomCode());
        } catch (BusinessException e) {
            broadcaster.sendErrorToPlayer(request.getPlayerId(), e.getMessage());
        }
    }

    @MessageMapping("/start")
    public void handleStart(@Payload Map<String, String> request) {
        try {
            String roomCode = request.get("roomCode");
            RoomDTO room = gameService.startGame(roomCode);

            broadcaster.sendRoomUpdate(roomCode, room);

            log.info("✅ WebSocket: 房间 {} 开始游戏", roomCode);
        } catch (BusinessException e) {
            String roomCode = request.get("roomCode");
            broadcaster.sendErrorToRoom(roomCode, e.getMessage());
        }
    }

    @MessageMapping("/submit")
    public void handleSubmit(@Payload SubmitRequest request) {
        try {
            RoomDTO room = gameService.submitAnswer(
                    request.getRoomCode(),
                    request.getPlayerId(),
                    request.getChoice(),
                    request.isForce()
            );

            broadcaster.sendRoomUpdate(request.getRoomCode(), room);

            log.info("✅ WebSocket: 玩家 {} 提交答案: {}", request.getPlayerId(), request.getChoice());
        } catch (BusinessException e) {
            broadcaster.sendErrorToPlayer(request.getPlayerId(), e.getMessage());
        }
    }

    @MessageMapping("/ready")
    public void handleReady(@Payload Map<String, Object> request) {
        try {
            String roomCode = (String) request.get("roomCode");
            String playerId = (String) request.get("playerId");
            boolean ready = (Boolean) request.get("ready");

            RoomDTO room = gameService.setPlayerReady(roomCode, playerId, ready);

            broadcaster.sendRoomUpdate(roomCode, room);

            log.info("✅ WebSocket: 玩家 {} 设置准备状态: {}", playerId, ready);
        } catch (BusinessException e) {
            String playerId = (String) request.get("playerId");
            broadcaster.sendErrorToPlayer(playerId, e.getMessage());
        }
    }

    @MessageMapping("/leave")
    public void handleLeave(@Payload Map<String, String> payload) {
        String roomCode = payload.get("roomCode");
        String playerId = payload.get("playerId");

        try {
            RoomDTO room = gameService.leaveRoom(roomCode, playerId);

            if (room != null) {
                broadcaster.sendRoomUpdate(roomCode, room);
            } else {
                broadcaster.sendRoomDeleted(roomCode);
            }
        } catch (Exception e) {
            log.error("❌ 处理离开请求失败", e);
            broadcaster.sendErrorToPlayer(playerId, e.getMessage());
        }
    }

}

