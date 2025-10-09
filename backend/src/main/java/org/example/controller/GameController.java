package org.example.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.*;
import org.example.entity.GameResultEntity;
import org.example.entity.PlayerGameEntity;
import org.example.exception.BusinessException;
import org.example.repository.GameResultRepository;
import org.example.service.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class GameController {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final GameResultRepository gameResultRepository;

    /**
     * 创建房间
     * POST /api/rooms?maxPlayers=4&questionCount=10
     */
    @PostMapping("/rooms")
    public ResponseEntity<RoomDTO> createRoom(
            @RequestParam(defaultValue = "4") Integer maxPlayers,
            @RequestParam(defaultValue = "10") Integer questionCount) {
        try {
            RoomDTO room = gameService.createRoom(maxPlayers, questionCount);
            log.info("创建房间成功: {}", room.getRoomCode());
            return ResponseEntity.ok(room);
        } catch (BusinessException e) {
            log.error("创建房间失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 加入房间
     * POST /api/rooms/{roomCode}/join?playerId=123&playerName=Tom
     */
    @PostMapping("/rooms/{roomCode}/join")
    public ResponseEntity<RoomDTO> joinRoom(
            @PathVariable String roomCode,
            @RequestParam String playerId,
            @RequestParam String playerName) {
        try {
            RoomDTO room = gameService.joinRoom(roomCode, playerId, playerName);

            // 广播房间更新
            broadcastRoomUpdate(room);

            log.info("玩家 {} 加入房间 {} 成功", playerName, roomCode);
            return ResponseEntity.ok(room);
        } catch (BusinessException e) {
            log.error("加入房间失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 开始游戏
     * POST /api/rooms/{roomCode}/start
     */
    @PostMapping("/rooms/{roomCode}/start")
    public ResponseEntity<RoomDTO> startGame(@PathVariable String roomCode) {
        try {
            RoomDTO room = gameService.startGame(roomCode);

            // 广播游戏开始
            broadcastRoomUpdate(room);

            log.info("房间 {} 开始游戏", roomCode);
            return ResponseEntity.ok(room);
        } catch (BusinessException e) {
            log.error("开始游戏失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 提交答案
     * POST /api/rooms/{roomCode}/submit?playerId=123&choice=A&force=false
     */
    @PostMapping("/rooms/{roomCode}/submit")
    public ResponseEntity<RoomDTO> submitAnswer(
            @PathVariable String roomCode,
            @RequestParam String playerId,
            @RequestParam String choice,
            @RequestParam(defaultValue = "false") boolean force) {
        try {
            RoomDTO room = gameService.submitAnswer(roomCode, playerId, choice, force);

            // 广播房间更新
            broadcastRoomUpdate(room);

            log.info("玩家 {} 在房间 {} 提交答案: {}", playerId, roomCode, choice);
            return ResponseEntity.ok(room);
        } catch (BusinessException e) {
            log.error("提交答案失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 设置玩家准备状态
     * PUT /api/rooms/{roomCode}/players/{playerId}/ready?ready=true
     */
    @PutMapping("/rooms/{roomCode}/players/{playerId}/ready")
    public ResponseEntity<RoomDTO> setPlayerReady(
            @PathVariable String roomCode,
            @PathVariable String playerId,
            @RequestParam boolean ready) {
        try {
            RoomDTO room = gameService.setPlayerReady(roomCode, playerId, ready);

            // 广播房间更新
            broadcastRoomUpdate(room);

            log.info("玩家 {} 在房间 {} 设置准备状态: {}", playerId, roomCode, ready);
            return ResponseEntity.ok(room);
        } catch (BusinessException e) {
            log.error("设置准备状态失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 获取房间状态
     * GET /api/rooms/{roomCode}
     */
    @GetMapping("/rooms/{roomCode}")
    public ResponseEntity<RoomDTO> getRoomStatus(@PathVariable String roomCode) {
        try {
            RoomDTO room = gameService.getRoomStatus(roomCode);
            return ResponseEntity.ok(room);
        } catch (BusinessException e) {
            log.error("获取房间状态失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 获取游戏结果
     * GET /api/rooms/{roomCode}/results
     */
    @GetMapping("/rooms/{roomCode}/results")
    public ResponseEntity<List<PlayerGameEntity>> getGameResults(@PathVariable String roomCode) {
        try {
            List<PlayerGameEntity> results = gameService.getGameResults(roomCode);
            return ResponseEntity.ok(results);
        } catch (BusinessException e) {
            log.error("获取游戏结果失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 删除房间
     * DELETE /api/rooms/{roomCode}
     */
    @DeleteMapping("/rooms/{roomCode}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomCode) {
        try {
            gameService.removeRoom(roomCode);

            // 广播房间删除
            messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/deleted",
                    Map.of("message", "房间已被删除", "roomCode", roomCode));

            log.info("删除房间: {}", roomCode);
            return ResponseEntity.ok().build();
        } catch (BusinessException e) {
            log.error("删除房间失败: {}", e.getMessage());
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
                .roomCode(result.getGame().getRoomCode())  // ✅ 从 game 获取
                .startTime(result.getGame().getStartTime())  // ✅ 从 game 获取
                .endTime(result.getGame().getEndTime())  // ✅ 从 game 获取
                .questionCount(result.getQuestionCount())
                .playerCount(result.getPlayerCount())
                .leaderboard(leaderboard)
                .questionDetails(questionDetails)
                .build();
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
}
