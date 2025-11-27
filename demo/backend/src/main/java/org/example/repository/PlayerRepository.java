package org.example.repository;

import org.example.entity.PlayerEntity;
import org.example.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<PlayerEntity, Long> {

    // 过滤软删除的记录
    @Query("SELECT p FROM PlayerEntity p WHERE p.playerId = ?1 AND p.deleted = false")
    Optional<PlayerEntity> findByPlayerId(String playerId);

    // ========== 新增 ==========
    @Query("SELECT p FROM PlayerEntity p WHERE p.username = ?1 AND p.deleted = false")
    Optional<PlayerEntity> findByUsername(String username);

    @Query("SELECT p FROM PlayerEntity p LEFT JOIN FETCH p.room WHERE p.username = ?1 AND p.deleted = false")
    Optional<PlayerEntity> findByUsernameWithRoom(String username);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PlayerEntity p WHERE p.username = ?1 AND p.deleted = false")
    boolean existsByUsername(String username);
    // =========================

    List<PlayerEntity> findByRoom(RoomEntity room);
}