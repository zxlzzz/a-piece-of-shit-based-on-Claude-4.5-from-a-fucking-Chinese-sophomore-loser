package org.example.pojo;

import lombok.Builder;
import lombok.Data;
import org.example.entity.QuestionEntity;

import java.util.Map;

@Data
@Builder
public class GameContext {
    private String roomCode;
    private QuestionEntity currentQuestion;
    private Map<String, String> currentSubmissions;
    private Map<String, PlayerGameState> playerStates;
    private int currentQuestionIndex;
}
