package org.example.service.scoring;

import lombok.Builder;
import lombok.Data;
import org.example.pojo.GameRoom;

import java.util.Map;

/**
 * 计分结果
 */
@Data
@Builder
public class ScoringResult {
    /**
     * 基础得分（策略计算的原始分数）
     */
    private Map<String, Integer> baseScores;

    /**
     * 最终得分（应用 Buff 后的分数）
     */
    private Map<String, Integer> finalScores;

    /**
     * 得分详情
     */
    private Map<String, GameRoom.QuestionScoreDetail> scoreDetails;

    /**
     * 是否是重复题
     */
    private boolean repeatableQuestion;

    /**
     * 当前轮次（从1开始）
     */
    private int currentRound;

    /**
     * 总轮次
     */
    private int totalRounds;
}