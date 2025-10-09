package org.example.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.dto.PlayerDTO;
import org.example.entity.QuestionEntity;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏房间 - 内存中的运行时状态
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameRoom {

    /**
     * 房间码
     */
    private String roomCode;

    /**
     * 最大玩家数
     */
    private Integer maxPlayers;

    /**
     * 房间内的玩家列表
     */
    private List<PlayerDTO> players = new ArrayList<>();

    /**
     * 题目列表
     */
    private List<QuestionEntity> questions;

    /**
     * 当前题目索引（-1 表示未开始）
     */
    private int currentIndex = -1;

    /**
     * 游戏是否已开始
     */
    private boolean started = false;

    /**
     * 游戏是否已结束
     */
    private boolean finished = false;

    /**
     * 提交记录
     * 外层键：题目索引（从0开始）
     * 内层键：playerId
     * 内层值：选择/答案
     */
    private Map<Integer, Map<String, String>> submissions = new ConcurrentHashMap<>();

    /**
     * 玩家总分
     * 键：playerId
     * 值：总分
     */
    private Map<String, Integer> scores = new ConcurrentHashMap<>();

    /**
     * 关联的游戏ID（数据库）
     */
    private Long gameId;

    /**
     * 当前题目开始时间
     */
    private LocalDateTime questionStartTime;

    /**
     * 答题时间限制（秒）
     */
    private Integer timeLimit;

    /**
     * 每道题的得分详情
     * 外层键：题目索引
     * 内层键：playerId
     * 内层值：得分详情（基础分+最终分）
     */
    private Map<Integer, Map<String, QuestionScoreDetail>> questionScores = new ConcurrentHashMap<>();

    /**
     * 断线玩家记录
     * 键：playerId
     * 值：断线时间
     */
    private Map<String, LocalDateTime> disconnectedPlayers = new ConcurrentHashMap<>();

    // ❌ 已删除：virtualPlayers（不再需要）
    // ❌ 已删除：leftPlayers（用 disconnectedPlayers 替代）

    /**
     * 获取当前题目
     */
    public QuestionEntity getCurrentQuestion() {
        if (questions == null || questions.isEmpty() || currentIndex < 0 || currentIndex >= questions.size()) {
            return null;
        }
        return questions.get(currentIndex);
    }

    /**
     * 推进到下一题
     * @return true-成功推进, false-已是最后一题
     */
    public boolean nextQuestion() {
        if (currentIndex + 1 < questions.size()) {
            currentIndex++;
            return true;
        } else {
            finished = true;
            return false;
        }
    }

    /**
     * 增加玩家分数
     */
    public void addScore(String playerId, int delta) {
        scores.put(playerId, scores.getOrDefault(playerId, 0) + delta);
    }

    /**
     * 题目得分详情
     */
    @Data
    @lombok.Builder
    public static class QuestionScoreDetail {
        /**
         * 基础得分（策略计算的原始分数）
         */
        private Integer baseScore;

        /**
         * 最终得分（应用 Buff 后的分数）
         */
        private Integer finalScore;
    }
}