package org.example.repository;

import org.example.entity.GameEntity;
import org.example.entity.PlayerEntity;
import org.example.entity.PlayerGameEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerGameRepository extends JpaRepository<PlayerGameEntity, Long> {
    List<PlayerGameEntity> findByGame(GameEntity game);

    // 查找特定玩家在特定游戏中的记录
    Optional<PlayerGameEntity> findByPlayerAndGame(PlayerEntity player, GameEntity game);

    // 按分数排序查找游戏排行榜
    List<PlayerGameEntity> findByGameOrderByScoreDesc(GameEntity game);
}
