package org.example.repository;

import org.example.entity.PlayerEntity;
import org.example.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<PlayerEntity, Long> {

    Optional<PlayerEntity> findByPlayerId(String playerId);

    // ========== 新增 ==========
    Optional<PlayerEntity> findByUsername(String username);

    boolean existsByUsername(String username);
    // =========================

    List<PlayerEntity> findByRoom(RoomEntity room);
}