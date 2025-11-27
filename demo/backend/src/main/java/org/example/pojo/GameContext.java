package org.example.pojo;

import lombok.Builder;
import lombok.Data;
import org.example.dto.QuestionDTO;  //  改成 DTO

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.io.Serializable;

@Data
@Builder
public class GameContext implements Serializable {
    private static final long serialVersionUID = 1L;
    private String roomCode;

    // 改成 QuestionDTO
    private QuestionDTO currentQuestion;

    private Map<String, String> currentSubmissions;
    private Map<String, PlayerGameState> playerStates;
    private int currentQuestionIndex;

    // 提交时间追踪
    private Map<String, LocalDateTime> submissionTimes;

    // ========== 辅助方法 ==========

    public String getPlayerName(String playerId) {
        PlayerGameState state = playerStates.get(playerId);
        return state != null ? state.getName() : "Unknown";
    }

    public LocalDateTime getSubmittedAt(String playerId) {
        if (submissionTimes != null && submissionTimes.containsKey(playerId)) {
            return submissionTimes.get(playerId);
        }
        return LocalDateTime.now();
    }

    public void recordSubmissionTime(String playerId) {
        if (submissionTimes == null) {
            submissionTimes = new HashMap<>();
        }
        submissionTimes.put(playerId, LocalDateTime.now());
    }

    public void clearSubmissionTimes() {
        if (submissionTimes != null) {
            submissionTimes.clear();
        }
    }
}