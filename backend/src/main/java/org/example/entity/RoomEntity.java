package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pojo.RoomStatus;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 房间实体 - 临时数据，游戏结束后可删除
 */
@Entity
@Table(name = "rooms")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoomEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 房间码（唯一标识）
     */
    @Column(unique = true, nullable = false, length = 20)
    private String roomCode;

    /**
     * 房间状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status = RoomStatus.WAITING;

    /**
     * 最大玩家数
     */
    @Column(nullable = false)
    private Integer maxPlayers;

    /**
     * 题目数量
     */
    @Column(nullable = false)
    private Integer questionCount;

    /**
     * 房主玩家ID
     */
    @Column(length = 50)
    private String hostPlayerId;

    /**
     * 房间创建时间
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 房间内的玩家列表
     * 删除房间时，级联删除所有玩家
     */
    @OneToMany(mappedBy = "room",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<PlayerEntity> players = new ArrayList<>();

    // ❌ 不关联 GameEntity，完全解耦
    // Game 只存 roomCode 字符串，不做外键关联
}