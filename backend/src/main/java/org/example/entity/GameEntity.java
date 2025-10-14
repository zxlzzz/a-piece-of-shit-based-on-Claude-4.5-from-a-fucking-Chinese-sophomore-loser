package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "games")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GameEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "room_code", nullable = false)
    private String roomCode;  // ❌ 不加cascade

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // ✅ 一对多：游戏 -> 玩家参与记录
    @OneToMany(mappedBy = "game",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<PlayerGameEntity> playerGames = new ArrayList<>();

    // ✅ 一对多：游戏 -> 提交记录
    @OneToMany(mappedBy = "game",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<SubmissionEntity> submissions = new ArrayList<>();

    // ✅ 一对一：游戏 -> 结果
    @OneToOne(mappedBy = "game",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private GameResultEntity result;
}
