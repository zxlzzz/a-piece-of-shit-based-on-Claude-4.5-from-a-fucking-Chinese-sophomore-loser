package org.example.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.*;
import org.example.exception.BusinessException;
import org.example.pojo.GameRoom;
import org.example.service.game.GameService;
import org.example.service.broadcast.RoomStateBroadcaster;
import org.example.service.cache.RoomCache;
import org.example.service.room.RoomLifecycleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class GameController {

    private final GameService gameService;
    private final RoomStateBroadcaster broadcaster;
    private final RoomCache roomCache;
    private final RoomLifecycleService roomLifecycleService;

    /**
     * 🔥 P1-3修复：移除try-catch，让全局异常处理器统一处理错误响应
     */
    @PostMapping("/rooms")
    public ResponseEntity<RoomDTO> createRoom(
            @RequestParam(defaultValue = "4") Integer maxPlayers,
            @RequestParam(defaultValue = "10") Integer questionCount,
            @RequestParam(defaultValue = "30") Integer timeLimit,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) List<Long> questionTagIds) {
        RoomDTO room = gameService.createRoom(maxPlayers, questionCount, timeLimit, password, questionTagIds);
        log.info("✅ 创建房间成功: {}", room.getRoomCode());
        return ResponseEntity.ok(room);
    }

    /**
     * 🔥 P1-3修复：移除try-catch并简化逻辑
     */
    @GetMapping("/rooms/{roomCode}")
    public ResponseEntity<RoomDTO> getRoomStatus(@PathVariable String roomCode) {
        log.info("🔍 获取房间状态: {}", roomCode);

        GameRoom gameRoom = roomCache.get(roomCode);
        if (gameRoom == null) {
            log.warn("⚠️ 房间不存在: {}", roomCode);
            throw new BusinessException("房间不存在: " + roomCode);
        }

        RoomDTO roomDTO = roomLifecycleService.toRoomDTO(roomCode);
        return ResponseEntity.ok(roomDTO);
    }

    @PutMapping("/rooms/{roomCode}/settings")
    public ResponseEntity<RoomDTO> updateRoomSettings(
            @PathVariable String roomCode,
            @RequestBody UpdateRoomSettingsRequest request) {
        try {
            RoomDTO room = gameService.updateRoomSettings(roomCode, request);
            log.info("✅ 更新房间 {} 设置成功", roomCode);
            return ResponseEntity.ok(room);
        } catch (BusinessException e) {
            log.error("❌ 更新房间设置失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/rooms/{roomCode}/join")
    public ResponseEntity<RoomDTO> joinRoom(
            @PathVariable String roomCode,
            @RequestParam String playerId,
            @RequestParam String playerName,
            @RequestParam(defaultValue = "false") Boolean spectator,
            @RequestParam(required = false) String password) {
        try {
            // 🔥 修复：添加重连检测逻辑
            GameRoom gameRoom = roomCache.get(roomCode);
            boolean isReconnect = gameRoom != null &&
                    gameRoom.getDisconnectedPlayers().containsKey(playerId);

            RoomDTO room;
            if (isReconnect) {
                // 重连场景
                roomLifecycleService.handleReconnect(roomCode, playerId);
                room = roomLifecycleService.toRoomDTO(roomCode);
                log.info("✅ 玩家 {} 重连房间 {}", playerName, roomCode);
            } else {
                // 新加入场景
                room = gameService.joinRoom(roomCode, playerId, playerName, spectator, password);
                log.info("✅ 玩家 {} 加入房间 {} 成功 (观战模式: {})", playerName, roomCode, spectator);
            }

            broadcaster.sendRoomUpdate(roomCode, room);
            return ResponseEntity.ok(room);
        } catch (BusinessException e) {
            log.error("❌ 加入/重连房间失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/rooms/{roomCode}/start")
    public ResponseEntity<RoomDTO> startGame(@PathVariable String roomCode) {
        try {
            RoomDTO room = gameService.startGame(roomCode);
            broadcaster.sendRoomUpdate(roomCode, room);
            log.info("✅ 房间 {} 开始游戏", roomCode);
            return ResponseEntity.ok(room);
        } catch (BusinessException e) {
            log.error("❌ 开始游戏失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/rooms/{roomCode}/submit")
    public ResponseEntity<RoomDTO> submitAnswer(
            @PathVariable String roomCode,
            @RequestParam String playerId,
            @RequestParam String choice,
            @RequestParam(defaultValue = "false") boolean force) {
        try {
            RoomDTO room = gameService.submitAnswer(roomCode, playerId, choice, force);
            broadcaster.sendRoomUpdate(roomCode, room);
            log.info("✅ 玩家 {} 在房间 {} 提交答案: {}", playerId, roomCode, choice);
            return ResponseEntity.ok(room);
        } catch (BusinessException e) {
            log.error("❌ 提交答案失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PutMapping("/rooms/{roomCode}/players/{playerId}/ready")
    public ResponseEntity<RoomDTO> setPlayerReady(
            @PathVariable String roomCode,
            @PathVariable String playerId,
            @RequestParam boolean ready) {
        try {
            RoomDTO room = gameService.setPlayerReady(roomCode, playerId, ready);
            broadcaster.sendRoomUpdate(roomCode, room);
            log.info("✅ 玩家 {} 在房间 {} 设置准备状态: {}", playerId, roomCode, ready);
            return ResponseEntity.ok(room);
        } catch (BusinessException e) {
            log.error("❌ 设置准备状态失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @DeleteMapping("/rooms/{roomCode}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomCode) {
        try {
            gameService.removeRoom(roomCode);
            broadcaster.sendRoomDeleted(roomCode);
            log.info("✅ 删除房间: {}", roomCode);
            return ResponseEntity.ok().build();
        } catch (BusinessException e) {
            log.error("❌ 删除房间失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/rooms/{roomCode}/kick")
    public ResponseEntity<RoomDTO> kickPlayer(
            @PathVariable String roomCode,
            @RequestParam String ownerId,
            @RequestParam String targetPlayerId) {
        try {
            RoomDTO room = gameService.kickPlayer(roomCode, ownerId, targetPlayerId);
            broadcaster.sendRoomUpdate(roomCode, room);
            broadcaster.sendPlayerKicked(roomCode, targetPlayerId);
            log.info("✅ 玩家 {} 被房主 {} 踢出房间 {}", targetPlayerId, ownerId, roomCode);
            return ResponseEntity.ok(room);
        } catch (BusinessException e) {
            log.error("❌ 踢出玩家失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<RoomDTO>> getActiveRooms() {
        try {
            List<RoomDTO> rooms = gameService.getAllActiveRoom();
            return ResponseEntity.ok(rooms);
        } catch (BusinessException e) {
            log.error(e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 获取房间的游戏历史/结果
     * 优先返回已保存的结果，否则返回当前游戏状态
     */
    @GetMapping("/rooms/{roomCode}/history")
    public ResponseEntity<GameHistoryDTO> getGameHistory(@PathVariable String roomCode) {
        try {
            GameHistoryDTO history = gameService.getGameHistoryByRoomCode(roomCode);
            return ResponseEntity.ok(history);
        } catch (BusinessException e) {
            log.error("获取游戏历史失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            log.error("获取游戏历史失败", e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UpdateRoomSettingsRequest {
        private Integer questionCount;
        private Integer timeLimit;
        private Boolean chatEnabled;
        private Boolean privateChatEnabled;  // 🔥 是否启用私聊功能
        private String rankingMode;
        private Integer targetScore;
        private RoomDTO.WinConditions winConditions;
        private List<Long> questionTagIds;
    }
}