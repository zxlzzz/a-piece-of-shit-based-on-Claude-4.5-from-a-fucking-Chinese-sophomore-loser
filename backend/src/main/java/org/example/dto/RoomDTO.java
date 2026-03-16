package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pojo.GameMode;
import org.example.pojo.RoomStatus;


import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoomDTO {
    private String roomCode;
    private int maxPlayers;
    private int currentPlayers;
    private RoomStatus status;
    private Boolean finished;            // 游戏是否结束
    private List<PlayerDTO> players;
    private LocalDateTime questionStartTime;
    private Integer timeLimit;
    private Integer currentIndex;
    private QuestionDTO currentQuestion;
    private Integer questionCount;
    private Boolean hasPassword;         // 是否有密码保护
    private List<String> submittedPlayerIds; // 🔥 当前题目已提交的玩家ID列表（用于前端验证）

    private GameMode gameMode;           // 游戏模式（SYNCHRONIZED / ASYNC）
    private List<QuestionDTO> questions; // ASYNC 模式：全量题目列表（供前端本地推进）
    private java.util.Map<String, Integer> playerProgress; // ASYNC 模式：各玩家已完成题目数
    private String rankingMode;          // 排名模式
    private Integer targetScore;         // 目标分数
    private WinConditions winConditions; // 通关条件对象
    private Boolean chatEnabled;         // 是否启用聊天室
    private Boolean privateChatEnabled;  // 🔥 是否启用私聊功能

    // 内部类：通关条件
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class WinConditions {
        private Integer minScorePerPlayer; // 所有人最低分
        private Integer minTotalScore;     // 团队总分
        private Integer minAvgScore;       // 平均分
    }
}
