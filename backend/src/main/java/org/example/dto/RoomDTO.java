package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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

    private String rankingMode;          // 排名模式
    private Integer targetScore;         // 目标分数
    private WinConditions winConditions; // 通关条件对象

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
