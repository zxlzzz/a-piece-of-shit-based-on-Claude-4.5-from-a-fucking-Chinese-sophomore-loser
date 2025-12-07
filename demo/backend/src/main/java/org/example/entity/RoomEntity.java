package org.example.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pojo.RoomStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
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
@JsonIgnoreProperties(value = {"players", "hibernateLazyInitializer", "handler"}, ignoreUnknown = true)
public class RoomEntity implements Serializable {

    private static final long serialVersionUID = 1L;

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
     * 每题时长限制（秒）
     */
    @Column
    @Builder.Default
    private Integer timeLimit = 30;

    /**
     * 房主玩家ID
     */
    @Column(length = 50)
    private String hostPlayerId;

    @OneToMany(mappedBy = "room",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH},
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<PlayerEntity> players = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;  // 创建时间

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;  // 更新时间
}