package org.example.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "players")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String playerId;  // UUID，游戏逻辑用

    // ========== 账号字段 ==========
    @Column(unique = true, nullable = true)
    private String username;  // 用户名（登录用，唯一，可为空表示游客）

    @Column(nullable = true)
    private String password;  // 密码（BCrypt加密，游客账号为空）
    // =============================

    @Column(nullable = false)
    private String name;  // 游戏昵称（可重复）

    private Boolean ready;

    @Column
    private Boolean spectator;  // 观战模式（观战者不参与答题，不计分）

    // ========== 软删除字段 ==========
    @Column(nullable = false)
    private Boolean deleted = false;  // 软删除标记

    @Column
    private LocalDateTime deletedAt;  // 删除时间
    // =============================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private RoomEntity room;

    @OneToMany(mappedBy = "player",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<PlayerGameEntity> playerGames = new ArrayList<>();

    @OneToMany(mappedBy = "player", fetch = FetchType.LAZY)
    private List<SubmissionEntity> submissions = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}