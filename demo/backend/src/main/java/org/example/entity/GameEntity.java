package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // 是否为测试游戏（虚拟玩家）
    @Column(nullable = false)
    private Boolean isTest = false;

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

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;  // 创建时间

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;  // 更新时间
}
