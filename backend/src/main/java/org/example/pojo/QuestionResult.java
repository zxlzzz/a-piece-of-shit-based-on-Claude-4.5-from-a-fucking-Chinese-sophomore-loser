package org.example.pojo;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class QuestionResult {
    private int questionIndex;
    private Map<String, Integer> baseScores;
    private Map<String, Integer> finalScores;
    private List<GameEvent> events;
    private Map<String, String> submissions;
}
