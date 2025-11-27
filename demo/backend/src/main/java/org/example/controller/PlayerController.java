package org.example.controller;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.PlayerEntity;
import org.example.exception.BusinessException;
import org.example.repository.PlayerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
@Slf4j
public class PlayerController {

    private final PlayerRepository playerRepository;

    /**
     * 获取所有玩家
     * GET /api/players
     */
    @GetMapping
    public ResponseEntity<List<PlayerEntity>> getAllPlayers() {
        List<PlayerEntity> players = playerRepository.findAll();
        return ResponseEntity.ok(players);
    }

    /**
     * 获取玩家详情
     * GET /api/players/{playerId}
     */
    @GetMapping("/{playerId}")
    public ResponseEntity<PlayerEntity> getPlayer(@PathVariable String playerId) {
        return playerRepository.findByPlayerId(playerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 更新玩家准备状态
     * PUT /api/players/{playerId}/ready?ready=true
     */
    @PutMapping("/{playerId}/ready")
    @Transactional
    public ResponseEntity<PlayerEntity> updatePlayerReady(
            @PathVariable String playerId,
            @RequestParam boolean ready) {
        try {
            PlayerEntity player = playerRepository.findByPlayerId(playerId)
                    .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));
            player.setReady(ready);
            PlayerEntity saved = playerRepository.save(player);
            return ResponseEntity.ok(saved);
        } catch (BusinessException e) {
            log.error("更新玩家准备状态失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 删除玩家（硬删除）
     * DELETE /api/players/{playerId}
     *  ��添加警告日志，建议使用软删除
     *  警告：此操作会永久删除玩家及其所有游戏历史记录
     */
    @DeleteMapping("/{playerId}")
    @Transactional
    public ResponseEntity<Void> deletePlayer(@PathVariable String playerId) {
        log.warn(" 收到玩家硬删除请求: playerId={}", playerId);
        PlayerEntity player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));
        playerRepository.delete(player);
        log.warn(" 硬删除玩家及其所有历史记录: playerId={}", playerId);
        return ResponseEntity.ok().build();
    }
}