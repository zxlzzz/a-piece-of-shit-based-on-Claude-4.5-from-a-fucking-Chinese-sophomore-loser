package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "player_game")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlayerGameEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ 多对一：参与记录 -> 玩家
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerEntity player;  // ❌ 不加cascade

    // ✅ 多对一：参与记录 -> 游戏
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private GameEntity game;  // ❌ 不加cascade

    @Column(nullable = false)
    private Integer score;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;  // 创建时间

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;  // 更新时间
}
