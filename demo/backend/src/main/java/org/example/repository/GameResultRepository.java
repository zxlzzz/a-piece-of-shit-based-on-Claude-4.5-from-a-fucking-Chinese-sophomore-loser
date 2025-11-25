package org.example.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.example.entity.GameEntity;
import org.example.entity.GameResultEntity;
import org.example.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GameResultRepository extends JpaRepository<GameResultEntity, Long> {

    // ✅ 新增：通过 gameId 查询（带 JOIN FETCH）
    @Query("SELECT gr FROM GameResultEntity gr " +
            "JOIN FETCH gr.game g " +
            "JOIN FETCH g.room " +
            "WHERE g.id = :gameId")
    Optional<GameResultEntity> findByGameIdWithDetails(@Param("gameId") Long gameId);

    // ✅ 修改：通过 roomCode 查询（带 JOIN FETCH）
    @Query("SELECT gr FROM GameResultEntity gr " +
            "JOIN FETCH gr.game g " +
            "JOIN FETCH g.room r " +
            "WHERE r.roomCode = :roomCode")
    Optional<GameResultEntity> findByRoomCodeWithDetails(@Param("roomCode") String roomCode);

    // ✅ 修改：时间过滤查询（带 JOIN FETCH）
    @Query("SELECT gr FROM GameResultEntity gr " +
            "JOIN FETCH gr.game g " +
            "JOIN FETCH g.room " +
            "WHERE gr.createdAt > :after " +
            "ORDER BY gr.createdAt DESC")
    List<GameResultEntity> findByCreatedAtAfterOrderByCreatedAtDesc(@Param("after") LocalDateTime after);

    // ✅ 修改：查询所有（带 JOIN FETCH）
    @Query("SELECT gr FROM GameResultEntity gr " +
            "JOIN FETCH gr.game g " +
            "JOIN FETCH g.room " +
            "ORDER BY gr.createdAt DESC")
    List<GameResultEntity> findAllByOrderByCreatedAtDesc();
}
