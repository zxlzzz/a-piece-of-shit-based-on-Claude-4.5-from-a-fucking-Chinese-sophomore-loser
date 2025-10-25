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

    // ========== 新增字段 ==========
    @Column(unique = true, nullable = false)
    private String username;  // 用户名（登录用，唯一）

    @Column(nullable = false)
    private String password;  // 密码（BCrypt加密）
    // =============================

    @Column(nullable = false)
    private String name;  // 游戏昵称（可重复）

    private Boolean ready;

    @Column
    private Boolean spectator;  // 观战模式（观战者不参与答题，不计分）

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