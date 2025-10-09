package org.example.repository;

import org.example.entity.GameEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameRepository extends JpaRepository<GameEntity, Long> {

    /**
     * 根据房间码查询游戏
     */
    Optional<GameEntity> findByRoomCode(String roomCode);
}
