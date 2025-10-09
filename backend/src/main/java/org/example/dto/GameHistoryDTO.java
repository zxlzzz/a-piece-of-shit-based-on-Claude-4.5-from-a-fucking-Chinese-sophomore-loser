package org.example.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class GameHistoryDTO {
    private Long gameId;  // ✅ 添加
    private String roomCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer questionCount;  // ✅ 添加
    private Integer playerCount;  // ✅ 添加
    private List<PlayerRankDTO> leaderboard;
    private List<QuestionDetailDTO> questionDetails;
}