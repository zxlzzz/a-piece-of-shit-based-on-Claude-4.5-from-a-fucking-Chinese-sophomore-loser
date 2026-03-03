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

    @PostMapping("/rooms")
    public ResponseEntity<RoomDTO> createRoom(
            @RequestParam(defaultValue = "4") Integer maxPlayers,
            @RequestParam(defaultValue = "10") Integer questionCount,
            @RequestParam(defaultValue = "30") Integer timeLimit) {
        RoomDTO room = gameService.createRoom(maxPlayers, questionCount, timeLimit);
        return ResponseEntity.ok(room);
    }

    @GetMapping("/rooms/{roomCode}")
    public ResponseEntity<RoomDTO> getRoomStatus(@PathVariable String roomCode) {
        GameRoom gameRoom = roomCache.get(roomCode);
        if (gameRoom == null) {
            log.warn(" 房间不存在: {}", roomCode);
            throw new BusinessException("房间不存在: " + roomCode);
        }

        RoomDTO roomDTO = roomLifecycleService.toRoomDTO(roomCode);
        return ResponseEntity.ok(roomDTO);
    }


    @PostMapping("/rooms/{roomCode}/join")
    public ResponseEntity<RoomDTO> joinRoom(
            @PathVariable String roomCode,
            @RequestParam String playerId,
            @RequestParam String playerName) {
        RoomDTO room = gameService.joinRoom(roomCode, playerId, playerName);
        broadcaster.sendRoomUpdate(roomCode, room);
        return ResponseEntity.ok(room);
    }

    @PostMapping("/rooms/{roomCode}/start")
    public ResponseEntity<RoomDTO> startGame(@PathVariable String roomCode) {
        RoomDTO room = gameService.startGame(roomCode);
        broadcaster.sendRoomUpdate(roomCode, room);
        return ResponseEntity.ok(room);
    }

    @PostMapping("/rooms/{roomCode}/submit")
    public ResponseEntity<RoomDTO> submitAnswer(
            @PathVariable String roomCode,
            @RequestParam String playerId,
            @RequestParam String choice,
            @RequestParam(defaultValue = "false") boolean force) {
        RoomDTO room = gameService.submitAnswer(roomCode, playerId, choice, force);
        broadcaster.sendRoomUpdate(roomCode, room);
        return ResponseEntity.ok(room);
    }

    @PutMapping("/rooms/{roomCode}/players/{playerId}/ready")
    public ResponseEntity<RoomDTO> setPlayerReady(
            @PathVariable String roomCode,
            @PathVariable String playerId,
            @RequestParam boolean ready) {
        RoomDTO room = gameService.setPlayerReady(roomCode, playerId, ready);
        broadcaster.sendRoomUpdate(roomCode, room);
        return ResponseEntity.ok(room);
    }

    @DeleteMapping("/rooms/{roomCode}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomCode) {
        gameService.removeRoom(roomCode);
        broadcaster.sendRoomDeleted(roomCode);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<RoomDTO>> getActiveRooms() {
        List<RoomDTO> rooms = gameService.getAllActiveRoom();
        return ResponseEntity.ok(rooms);
    }

    /**
     * 获取房间的游戏历史/结果
     * 优先返回已保存的结果，否则返回当前游戏状态
     */
    @GetMapping("/rooms/{roomCode}/history")
    public ResponseEntity<GameHistoryDTO> getGameHistory(@PathVariable String roomCode) {
        GameHistoryDTO history = gameService.getGameHistoryByRoomCode(roomCode);
        return ResponseEntity.ok(history);
    }

}