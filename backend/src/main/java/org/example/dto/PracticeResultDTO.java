package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 练习结果DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PracticeResultDTO {
    /**
     * 玩家选择
     */
    private String playerChoice;

    /**
     * 所有Bot的选择（key: "bot1","bot2"... value: 选择）
     */
    private Map<String, String> botChoices;

    /**
     * 玩家得分
     */
    private Integer playerScore;

    /**
     * 题目信息（用于显示）
     */
    private QuestionDTO question;

    /**
     * 所有玩家的分数（玩家+所有Bot）
     */
    private Map<String, Integer> allScores;
}
