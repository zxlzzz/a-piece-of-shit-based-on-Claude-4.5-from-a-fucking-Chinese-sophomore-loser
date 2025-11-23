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
     * Bot选择
     */
    private String botChoice;

    /**
     * 玩家得分
     */
    private Integer playerScore;

    /**
     * Bot得分
     */
    private Integer botScore;

    /**
     * 题目信息（用于显示）
     */
    private QuestionDTO question;

    /**
     * 所有玩家的分数（玩家+Bot）
     */
    private Map<String, Integer> allScores;
}
