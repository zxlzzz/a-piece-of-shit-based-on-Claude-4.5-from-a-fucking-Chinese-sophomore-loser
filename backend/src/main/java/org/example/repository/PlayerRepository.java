package org.example.repository;

import org.example.entity.PlayerEntity;
import org.example.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<PlayerEntity, Long> {

    /**
     * 根据 playerId 查询玩家
     */
    Optional<PlayerEntity> findByPlayerId(String playerId);

    /**
     * 查询房间内的所有玩家
     */
    List<PlayerEntity> findByRoom(RoomEntity room);
}
