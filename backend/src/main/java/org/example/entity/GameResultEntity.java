package org.example.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_results")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GameResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomCode;
    // ✅ 一对一：结果 -> 游戏
    @OneToOne
    @JoinColumn(name = "game_id", nullable = false, unique = true)
    private GameEntity game;  // ❌ 不加cascade，由Game端管理

    @Column(nullable = false)
    private Integer questionCount;

    @Column(nullable = false)
    private Integer playerCount;

    @Column(columnDefinition = "TEXT")
    private String leaderboardJson;

    @Column(columnDefinition = "TEXT")
    private String questionDetailsJson;

    @CreationTimestamp
    private LocalDateTime createdAt;
}