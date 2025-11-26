package org.example.service.player;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.PlayerEntity;
import org.example.exception.BusinessException;
import org.example.repository.PlayerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerService {

    private final PlayerRepository playerRepository;

    /**
     * 按 playerId 查询玩家
     */
    public Optional<PlayerEntity> getPlayerByPlayerId(String playerId) {
        return playerRepository.findByPlayerId(playerId);
    }

    /**
     * 更新玩家准备状态
     */
    @Transactional
    public PlayerEntity updatePlayerReady(String playerId, boolean ready) {
        PlayerEntity player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

        player.setReady(ready);
        return playerRepository.save(player);
    }

    /**
     * 删除玩家（硬删除）
     * 🔥 P2-4修复：建议使用软删除代替，保留历史数据
     * ⚠️ 警告：硬删除会级联删除玩家的所有游戏记录和答题记录
     */
    @Transactional
    public void deletePlayer(String playerId) {
        PlayerEntity player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

        // 🔥 P2-4建议：应该使用软删除
        // player.setDeleted(true);
        // player.setDeletedAt(LocalDateTime.now());
        // playerRepository.save(player);

        playerRepository.delete(player);
        log.warn("⚠️ 硬删除玩家及其所有历史记录: playerId={}", playerId);
    }

    /**
     * 查询所有玩家
     */
    public List<PlayerEntity> getAllPlayers() {
        return playerRepository.findAll();
    }
}