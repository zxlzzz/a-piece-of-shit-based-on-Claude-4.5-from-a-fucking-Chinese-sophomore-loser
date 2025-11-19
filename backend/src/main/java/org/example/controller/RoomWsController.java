package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.exception.BusinessException;
import org.example.pojo.GameRoom;
import org.example.pojo.JoinRequest;
import org.example.dto.RoomDTO;
import org.example.pojo.SubmitRequest;
import org.example.service.game.GameService;
import org.example.service.broadcast.RoomStateBroadcaster;
import org.example.service.room.RoomLifecycleService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * 🔥 已废弃：WebSocket命令处理器
 *
 * 优化策略：采用混合模式
 * - 所有操作命令改用HTTP API（GameController.java）
 * - WebSocket仅用于服务器推送状态更新（RoomStateBroadcaster.java）
 *
 * 优势：
 * 1. HTTP操作更可靠，掉线后可重试
 * 2. 减少WebSocket负担，连接更稳定
 * 3. 更容易调试和监控
 *
 * 本文件保留但所有@MessageMapping已禁用，防止客户端误调用
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomWsController {

    private final GameService gameService;
    private final RoomStateBroadcaster broadcaster;
    private final RoomLifecycleService roomLifecycleService;

    // 🔥 已废弃：请使用 HTTP POST /api/rooms/{roomCode}/join
    // @MessageMapping("/join")
    public void handleJoin_DEPRECATED(@Payload JoinRequest request) {
        try {
            // 🔥 检查是否是重连
            GameRoom gameRoom = gameService.getGameRoom(request.getRoomCode());
            boolean isReconnect = gameRoom != null &&
                    gameRoom.getDisconnectedPlayers().containsKey(request.getPlayerId());

            if (isReconnect) {
                // 🔥 重连逻辑
                roomLifecycleService.handleReconnect(request.getRoomCode(), request.getPlayerId());
            } else {
                // 🔥 正常加入逻辑（原有代码）
                RoomDTO room = gameService.joinRoom(request.getRoomCode(),
                        request.getPlayerId(), request.getPlayerName(), false, null);
            }

            // 🔥 统一广播（无论加入还是重连）
            RoomDTO updatedRoom = roomLifecycleService.toRoomDTO(request.getRoomCode());
            broadcaster.sendRoomUpdate(request.getRoomCode(), updatedRoom);
            broadcaster.sendWelcomeToPlayer(request.getPlayerId(), request.getRoomCode(), updatedRoom);

        } catch (BusinessException e) {
            log.error("❌ 加入房间失败（业务异常）: {}", e.getMessage());
            broadcaster.sendErrorToPlayer(request.getPlayerId(), e.getMessage());
        } catch (Exception e) {
            // 🔥 添加：捕获所有异常，防止断连
            log.error("❌ 加入房间失败（系统异常）", e);
            broadcaster.sendErrorToPlayer(request.getPlayerId(), "系统错误，请重试");
        }
    }

    // 🔥 已废弃：请使用 HTTP POST /api/rooms/{roomCode}/start
    // @MessageMapping("/start")
    public void handleStart_DEPRECATED(@Payload Map<String, String> request) {
        try {
            String roomCode = request.get("roomCode");
            RoomDTO room = gameService.startGame(roomCode);

            broadcaster.sendRoomUpdate(roomCode, room);

        } catch (BusinessException e) {
            String roomCode = request.get("roomCode");
            log.error("❌ 开始游戏失败（业务异常）: {}", e.getMessage());
            broadcaster.sendErrorToRoom(roomCode, e.getMessage());
        } catch (Exception e) {
            // 🔥 添加：捕获所有异常
            String roomCode = request.get("roomCode");
            log.error("❌ 开始游戏失败（系统异常）", e);
            broadcaster.sendErrorToRoom(roomCode, "系统错误，请重试");
        }
    }

    // 🔥 已废弃：请使用 HTTP POST /api/rooms/{roomCode}/submit
    // @MessageMapping("/submit")
    public void handleSubmit_DEPRECATED(@Payload SubmitRequest request) {
        try {
            RoomDTO room = gameService.submitAnswer(
                    request.getRoomCode(),
                    request.getPlayerId(),
                    request.getChoice(),
                    request.isForce()
            );

            broadcaster.sendRoomUpdate(request.getRoomCode(), room);

        } catch (BusinessException e) {
            log.error("❌ 提交答案失败（业务异常）: {}", e.getMessage());
            broadcaster.sendErrorToPlayer(request.getPlayerId(), e.getMessage());
        } catch (Exception e) {
            // 🔥 添加：捕获所有异常，防止断连
            log.error("❌ 提交答案失败（系统异常）", e);
            broadcaster.sendErrorToPlayer(request.getPlayerId(), "提交失败，请重试");

            // 🔥 重要：即使出错也要广播房间状态，避免界面卡住
            try {
                RoomDTO room = gameService.getRoomStatus(request.getRoomCode());
                broadcaster.sendRoomUpdate(request.getRoomCode(), room);
            } catch (Exception ex) {
                log.error("❌ 广播房间状态失败", ex);
            }
        }
    }

    // 🔥 已废弃：请使用 HTTP PUT /api/rooms/{roomCode}/players/{playerId}/ready
    // @MessageMapping("/ready")
    public void handleReady_DEPRECATED(@Payload Map<String, Object> request) {
        try {
            String roomCode = (String) request.get("roomCode");
            String playerId = (String) request.get("playerId");
            boolean ready = (Boolean) request.get("ready");

            RoomDTO room = gameService.setPlayerReady(roomCode, playerId, ready);

            broadcaster.sendRoomUpdate(roomCode, room);

        } catch (BusinessException e) {
            String playerId = (String) request.get("playerId");
            log.error("❌ 设置准备状态失败（业务异常）: {}", e.getMessage());
            broadcaster.sendErrorToPlayer(playerId, e.getMessage());
        } catch (Exception e) {
            // 🔥 添加：捕获所有异常
            String playerId = (String) request.get("playerId");
            log.error("❌ 设置准备状态失败（系统异常）", e);
            broadcaster.sendErrorToPlayer(playerId, "系统错误，请重试");
        }
    }

    // 🔥 已废弃：离开房间通过关闭页面/浏览器自动处理，或使用HTTP DELETE
    // @MessageMapping("/leave")
    public void handleLeave_DEPRECATED(@Payload Map<String, String> payload) {
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
            broadcaster.sendErrorToPlayer(playerId, "离开房间失败");
        }
    }

}

