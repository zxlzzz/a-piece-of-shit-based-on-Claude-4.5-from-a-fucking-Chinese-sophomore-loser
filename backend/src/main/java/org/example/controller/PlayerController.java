package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.PlayerEntity;
import org.example.exception.BusinessException;
import org.example.service.player.PlayerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
@Slf4j
public class PlayerController {

    private final PlayerService playerService;

    /**
     * 获取所有玩家
     * GET /api/players
     */
    @GetMapping
    public ResponseEntity<List<PlayerEntity>> getAllPlayers() {
        List<PlayerEntity> players = playerService.getAllPlayers();
        return ResponseEntity.ok(players);
    }

    /**
     * 获取玩家详情
     * GET /api/players/{playerId}
     */
    @GetMapping("/{playerId}")
    public ResponseEntity<PlayerEntity> getPlayer(@PathVariable String playerId) {
        return playerService.getPlayerByPlayerId(playerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 更新玩家准备状态
     * PUT /api/players/{playerId}/ready?ready=true
     */
    @PutMapping("/{playerId}/ready")
    public ResponseEntity<PlayerEntity> updatePlayerReady(
            @PathVariable String playerId,
            @RequestParam boolean ready) {
        try {
            PlayerEntity player = playerService.updatePlayerReady(playerId, ready);
            return ResponseEntity.ok(player);
        } catch (BusinessException e) {
            log.error("更新玩家准备状态失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 删除玩家
     * DELETE /api/players/{playerId}
     */
    @DeleteMapping("/{playerId}")
    public ResponseEntity<Void> deletePlayer(@PathVariable String playerId) {
        try {
            playerService.deletePlayer(playerId);
            return ResponseEntity.ok().build();
        } catch (BusinessException e) {
            log.error("删除玩家失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}