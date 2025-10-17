package org.example.repository;

import org.example.entity.GameEntity;
import org.example.entity.GameResultEntity;
import org.example.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GameResultRepository extends JpaRepository<GameResultEntity, Long> {

    // 或用自定义查询
    @Query("SELECT gr FROM GameResultEntity gr WHERE gr.room.roomCode = ?1")
    Optional<GameResultEntity> findByRoomCode(String roomCode);
    /**
     * 查询某个时间之后的所有游戏结果
     */
    List<GameResultEntity> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime after);

    /**
     * 查询所有游戏结果，按创建时间倒序
     */
    List<GameResultEntity> findAllByOrderByCreatedAtDesc();

    Optional<GameResultEntity> findByGame(GameEntity game);
}
