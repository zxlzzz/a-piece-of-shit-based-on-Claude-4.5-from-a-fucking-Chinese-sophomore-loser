package org.example.repository;

import org.example.entity.GameResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GameResultRepository extends JpaRepository<GameResultEntity, Long> {

    Optional<GameResultEntity> findByGameId(Long gameId);
    Optional<GameResultEntity> findByRoomCode(String roomCode);
    /**
     * 根据游戏ID查询结果（一对一关系）
     */
    Optional<GameResultEntity> findByGame_Id(Long gameId);

    /**
     * 根据房间码查询结果（通过 game.roomCode）
     */
    Optional<GameResultEntity> findByGame_RoomCode(String roomCode);

    /**
     * 查询某个时间之后的所有游戏结果
     */
    List<GameResultEntity> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime after);

    /**
     * 查询所有游戏结果，按创建时间倒序
     */
    List<GameResultEntity> findAllByOrderByCreatedAtDesc();
}
