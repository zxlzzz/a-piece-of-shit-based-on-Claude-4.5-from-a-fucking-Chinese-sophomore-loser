package org.example.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.*;
import org.example.entity.GameResultEntity;
import org.example.exception.BusinessException;
import org.example.repository.GameResultRepository;
import org.example.service.GameService;
import org.example.service.broadcast.RoomStateBroadcaster;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class GameController {

    private final GameService gameService;
    private final RoomStateBroadcaster broadcaster; // 🔥 新增
    private final ObjectMapper objectMapper;
    private final GameResultRepository gameResultRepository;

    @PostMapping("/rooms")
    public ResponseEntity<RoomDTO> createRoom(
            @RequestParam(defaultValue = "4") Integer maxPlayers,
            @RequestParam(defaultValue = "10") Integer questionCount) {
        try {
            RoomDTO room = gameService.createRoom(maxPlayers, questionCount);
            log.info("✅ 创建房间成功: {}", room.getRoomCode());
            return ResponseEntity.ok(room);
        } catch (BusinessException e) {
            log.error("❌ 创建房间失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
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
            @RequestParam String playerName) {
        try {
            RoomDTO room = gameService.joinRoom(roomCode, playerId, playerName);

            // 🔥 改用 broadcaster
            broadcaster.sendRoomUpdate(roomCode, room);

            log.info("✅ 玩家 {} 加入房间 {} 成功", playerName, roomCode);
            return ResponseEntity.ok(room);
        } catch (BusinessException e) {
            log.error("❌ 加入房间失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/rooms/{roomCode}/start")
    public ResponseEntity<RoomDTO> startGame(@PathVariable String roomCode) {
        try {
            RoomDTO room = gameService.startGame(roomCode);

            // 🔥 改用 broadcaster
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

            // 🔥 改用 broadcaster
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

            // 🔥 改用 broadcaster
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

            // 🔥 改用 broadcaster
            broadcaster.sendRoomDeleted(roomCode);

            log.info("✅ 删除房间: {}", roomCode);
            return ResponseEntity.ok().build();
        } catch (BusinessException e) {
            log.error("❌ 删除房间失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<RoomDTO>> getActiveRooms() {
        try{
            List<RoomDTO> rooms = gameService.getAllActiveRoom();
            return ResponseEntity.ok(rooms);
        }catch (BusinessException e){
            log.error(e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/rooms/{roomCode}/history")
    public ResponseEntity<GameHistoryDTO> getGameHistory(@PathVariable String roomCode) {
        try {
            // 先尝试从数据库查询
            Optional<GameResultEntity> resultOpt = gameResultRepository.findByRoomCode(roomCode);

            if (resultOpt.isPresent()) {
                // ✅ 从数据库读取
                GameResultEntity result = resultOpt.get();
                GameHistoryDTO history = parseGameResultEntity(result);
                return ResponseEntity.ok(history);
            } else {
                // ✅ 从内存读取
                GameHistoryDTO history = gameService.getCurrentGameStatus(roomCode);
                return ResponseEntity.ok(history);
            }
        } catch (BusinessException e) {
            log.error("获取游戏历史失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            log.error("解析游戏历史失败", e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    private GameHistoryDTO parseGameResultEntity(GameResultEntity result) throws Exception {
        List<PlayerRankDTO> leaderboard = objectMapper.readValue(
                result.getLeaderboardJson(),
                new TypeReference<List<PlayerRankDTO>>() {}
        );

        List<QuestionDetailDTO> questionDetails = objectMapper.readValue(
                result.getQuestionDetailsJson(),
                new TypeReference<List<QuestionDetailDTO>>() {}
        );

        return GameHistoryDTO.builder()
                .gameId(result.getGame().getId())  // ✅ 添加
                .roomCode(result.getGame().getRoom().getRoomCode())  // ✅ 从 game 获取
                .startTime(result.getGame().getStartTime())  // ✅ 从 game 获取
                .endTime(result.getGame().getEndTime())  // ✅ 从 game 获取
                .questionCount(result.getQuestionCount())
                .playerCount(result.getPlayerCount())
                .leaderboard(leaderboard)
                .questionDetails(questionDetails)
                .build();
    }

    /**
     * 更新房间设置请求体
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UpdateRoomSettingsRequest {
        private Integer questionCount;      // 题目数量（可选）
        private String rankingMode;         // 排名模式
        private Integer targetScore;        // 目标分数
        private RoomDTO.WinConditions winConditions;  // 通关条件
    }
}


