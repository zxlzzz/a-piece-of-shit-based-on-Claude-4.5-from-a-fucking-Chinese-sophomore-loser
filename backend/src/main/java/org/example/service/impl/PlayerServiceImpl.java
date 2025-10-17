package org.example.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.PlayerEntity;
import org.example.entity.RoomEntity;
import org.example.exception.BusinessException;
import org.example.repository.PlayerRepository;
import org.example.service.PlayerService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerServiceImpl implements PlayerService {

    private final PlayerRepository playerRepository;

    /**
     * 创建玩家
     * @param playerId 玩家唯一标识（UUID）
     * @param playerName 玩家昵称
     */
    @Override
    @Transactional
    public PlayerEntity createPlayer(String playerId, String playerName) {
        // 检查 playerId 是否已存在
        if (playerRepository.findByPlayerId(playerId).isPresent()) {
            throw new BusinessException("玩家ID已存在");
        }

        PlayerEntity player = PlayerEntity.builder()
                .playerId(playerId)
                .name(playerName)
                .ready(false)
                .build();

        log.info("创建玩家: playerId={}, name={}", playerId, playerName);
        return playerRepository.save(player);
    }

    /**
     * 按 playerId 查询玩家
     */
    @Override
    public Optional<PlayerEntity> getPlayerByPlayerId(String playerId) {
        return playerRepository.findByPlayerId(playerId);
    }

    /**
     * 更新玩家准备状态
     */
    @Override
    @Transactional
    public PlayerEntity updatePlayerReady(String playerId, boolean ready) {
        PlayerEntity player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

        player.setReady(ready);
        log.info("更新玩家准备状态: playerId={}, ready={}", playerId, ready);
        return playerRepository.save(player);
    }

    /**
     * 删除玩家
     */
    @Override
    @Transactional
    public void deletePlayer(String playerId) {
        PlayerEntity player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

        playerRepository.delete(player);
        log.info("删除玩家: playerId={}", playerId);
    }

    /**
     * 查询所有玩家
     */
    @Override
    public List<PlayerEntity> getAllPlayers() {
        return playerRepository.findAll();
    }
}