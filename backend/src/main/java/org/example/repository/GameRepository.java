package org.example.repository;

import org.example.entity.GameEntity;
import org.example.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

public interface GameRepository extends JpaRepository<GameEntity, Long> {

    // ✅ 改成
    Optional<GameEntity> findByRoom(RoomEntity room);
    // 或者用自定义查询
    @Query("SELECT g FROM GameEntity g WHERE g.room.roomCode = ?1")
    Optional<GameEntity> findByRoomCode(String roomCode);
}
