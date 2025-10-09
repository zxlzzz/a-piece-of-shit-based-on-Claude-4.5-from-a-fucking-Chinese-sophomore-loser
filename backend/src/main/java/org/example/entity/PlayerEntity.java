package org.example.entity;

import jakarta.persistence.*;
import lombok.*;

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
    private String playerId;  // ✅ 你新加的

    @Column(nullable = false)
    private String name;

    private Boolean ready;

    // ✅ 多对一：玩家 -> 房间
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private RoomEntity room;  // ❌ 不加cascade，防止删除玩家时误删房间

    // ✅ 一对多：玩家 -> 参与的游戏
    @OneToMany(mappedBy = "player",
            cascade = CascadeType.ALL,   // 删除玩家时删除参与记录
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<PlayerGameEntity> playerGames = new ArrayList<>();

    // ✅ 一对多：玩家 -> 提交记录
    // 这里有两种选择：

    //删除玩家时保留历史提交（推荐）
    @OneToMany(mappedBy = "player", fetch = FetchType.LAZY)
    private List<SubmissionEntity> submissions = new ArrayList<>();

}

