package org.example.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.example.entity.GameEntity;
import org.example.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

public interface GameRepository extends JpaRepository<GameEntity, Long> {

    // ✅ 改成
    Optional<GameEntity> findByRoom(RoomEntity room);
    // ✅ 添加这个方法（JOIN FETCH）
    @Query("SELECT g FROM GameEntity g " +
            "JOIN FETCH g.room r " +
            "WHERE r.roomCode = :roomCode")
    Optional<GameEntity> findByRoomCodeWithRoom(@Param("roomCode") String roomCode);
}
