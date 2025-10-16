package org.example.service.strategy.QR;// ==================== 抽象基类：处理通用逻辑 ====================

import lombok.extern.slf4j.Slf4j;
import org.example.pojo.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 重复题目的抽象基类
 * 处理buff应用、持续时间、清理等通用逻辑
 *
 * 子类只需实现：
 * 1. calculateRoundBaseScores - 计算本轮基础分数
 * 2. getTotalRounds - 返回总轮次
 * 3. getQuestionIdentifier - 返回策略ID
 *
 * 可选重写：
 * - generateRoundEvents - 生成自定义事件
 * - applyNextRoundBuffs - 添加下一轮的buff
 */
@Component
@Slf4j
public abstract class BaseRepeatableStrategy implements RepeatableQuestionStrategy {

    /**
     * 计算某一轮的结果
     *
     * @param context 游戏上下文
     * @param currentRound 当前轮次（从1开始，第1轮=1，第2轮=2...）
     * @return 本轮的计算结果
     */
    @Override
    public QuestionResult calculateRoundResult(GameContext context, int currentRound) {
        Map<String, String> submissions = context.getCurrentSubmissions();

        // 1. 计算本轮基础分数
        Map<String, Integer> baseScores = calculateRoundBaseScores(submissions, currentRound);

        // 2. 生成本轮事件（如选择了什么选项）
        List<GameEvent> events = generateRoundEvents(submissions, baseScores, currentRound);

        // 3. 应用当前生效的buff（如上一轮的翻倍/减半）
        Map<String, Integer> finalScores = applyBuffs(context, baseScores, events);

        // 4. 根据本轮选择，给玩家添加下一轮的buff
        applyNextRoundBuffs(context, submissions, currentRound, events);

        // 5. 减少所有buff的持续时间
        decreaseBuffDuration(context);

        // 6. 检查是否是最后一轮，清理重复题buff
        if (currentRound == getTotalRounds()) {
            clearRepeatableBuffs(context, events);
        }

        return QuestionResult.builder()
                .questionIndex(context.getCurrentQuestionIndex())
                .baseScores(baseScores)
                .finalScores(finalScores)
                .events(events)
                .submissions(submissions)
                .build();
    }

    /**
     * 子类实现：计算本轮的基础分数
     * @param submissions 玩家提交
     * @param currentRound 当前轮次
     * @return playerId -> baseScore
     */
    protected abstract Map<String, Integer> calculateRoundBaseScores(
            Map<String, String> submissions, int currentRound);

    /**
     * 生成本轮事件（可选，子类可重写）
     *
     * @param currentRound 当前轮次（从1开始）
     */
    protected List<GameEvent> generateRoundEvents(
            Map<String, String> submissions,
            Map<String, Integer> baseScores,
            int currentRound) {
        List<GameEvent> events = new ArrayList<>();
        events.add(GameEvent.builder()
                .type("ROUND_INFO")
                .description(String.format("第 %d/%d 轮", currentRound, getTotalRounds()))
                .build());
        return events;
    }

    /**
     * 应用下一轮的buff（可选，子类可重写）
     */
    protected void applyNextRoundBuffs(
            GameContext context,
            Map<String, String> submissions,
            int currentRound,
            List<GameEvent> events) {
        // 默认不做处理，子类可重写
    }

    /**
     * 应用所有生效的buff
     */
    private Map<String, Integer> applyBuffs(
            GameContext context,
            Map<String, Integer> baseScores,
            List<GameEvent> events) {
        Map<String, Integer> finalScores = new HashMap<>();

        for (Map.Entry<String, Integer> entry : baseScores.entrySet()) {
            String playerId = entry.getKey();
            int score = entry.getValue();

            PlayerGameState state = context.getPlayerStates().get(playerId);
            if (state != null && state.getActiveBuffs() != null) {
                // 收集需要移除的buff
                List<Buff> buffsToRemove = new ArrayList<>();

                for (Buff buff : state.getActiveBuffs()) {
                    // duration为0表示本题生效
                    // duration为-1且有triggerOnScore标记，也需要检查
                    boolean shouldCheck = (buff.getDuration() == 0) ||
                            (buff.getDuration() == -1 && buff.getParams() != null &&
                                    Boolean.TRUE.equals(buff.getParams().get("triggerOnScore")));

                    if (shouldCheck) {
                        int[] result = applyBuffWithConsumption(buff, score, playerId, events);
                        score = result[0];

                        // 如果buff被消耗了，标记为待移除
                        if (result[1] == 1) {
                            buffsToRemove.add(buff);
                        }
                    }
                }

                // 移除已消耗的buff
                state.getActiveBuffs().removeAll(buffsToRemove);
            }
            finalScores.put(playerId, score);
        }

        return finalScores;
    }

    /**
     * 应用单个buff
     * @return 数组：[修改后的分数, 是否消耗了这个buff(1=消耗,0=不消耗)]
     */
    private int[] applyBuffWithConsumption(Buff buff, int score, String playerId, List<GameEvent> events) {
        boolean consumed = false;

        // 检查是否需要在得分时才触发
        if (buff.getParams() != null &&
                Boolean.TRUE.equals(buff.getParams().get("triggerOnScore"))) {
            // 如果分数<=0，不触发buff
            if (score <= 0) {
                return new int[]{score, 0};
            }
            // 分数>0，触发buff并标记为已消耗
            consumed = true;
        }

        int newScore = score;

        switch (buff.getType()) {
            case "SCORE_DOUBLE":
                newScore = score * 2;
                events.add(GameEvent.builder()
                        .type("BUFF_APPLIED")
                        .targetPlayerId(playerId)
                        .description("得分翻倍（" + score + " → " + newScore + "）")
                        .build());
                break;

            case "SCORE_HALVED":
                newScore = score / 2;
                events.add(GameEvent.builder()
                        .type("DEBUFF_APPLIED")
                        .targetPlayerId(playerId)
                        .description("得分减半（" + score + " → " + newScore + "）")
                        .build());
                break;

            default:
                return new int[]{score, 0};
        }

        return new int[]{newScore, consumed ? 1 : 0};
    }

    /**
     * 减少buff持续时间
     */
    private void decreaseBuffDuration(GameContext context) {
        for (PlayerGameState state : context.getPlayerStates().values()) {
            if (state.getActiveBuffs() == null) continue;

            Iterator<Buff> iterator = state.getActiveBuffs().iterator();
            while (iterator.hasNext()) {
                Buff buff = iterator.next();
                // duration为-1的buff（如triggerOnScore类型）不自动减少
                if (buff.getDuration() >= 0) {
                    buff.setDuration(buff.getDuration() - 1);
                    if (buff.getDuration() < 0) {
                        iterator.remove();
                    }
                }
            }
        }
    }

    /**
     * 清理重复题专用的buff（最后一轮时调用）
     * 只清理标记了 repeatableOnly 的 buff
     */
    private void clearRepeatableBuffs(GameContext context, List<GameEvent> events) {
        for (PlayerGameState state : context.getPlayerStates().values()) {
            if (state.getActiveBuffs() == null) continue;

            List<Buff> clearedBuffs = new ArrayList<>();

            Iterator<Buff> iterator = state.getActiveBuffs().iterator();
            while (iterator.hasNext()) {
                Buff buff = iterator.next();
                // 清理标记为"仅在重复题中生效"的buff
                if (buff.getParams() != null &&
                        Boolean.TRUE.equals(buff.getParams().get("repeatableOnly"))) {
                    iterator.remove();
                    clearedBuffs.add(buff);
                }
            }

            // 记录清理事件
            if (!clearedBuffs.isEmpty()) {
                events.add(GameEvent.builder()
                        .type("BUFF_CLEARED")
                        .targetPlayerId(state.getPlayerId())
                        .description("重复题结束，清理 " + clearedBuffs.size() + " 个buff")
                        .build());

                log.debug("🧹 玩家 {} 清理了 {} 个重复题buff", state.getPlayerId(), clearedBuffs.size());
            }
        }
    }

    /**
     * 工具方法：获取两个玩家
     */
    protected Map.Entry<String, String>[] getTwoPlayers(Map<String, String> submissions) {
        if (submissions.size() != 2) {
            throw new IllegalArgumentException("需要2人游戏");
        }
        List<Map.Entry<String, String>> list = new ArrayList<>(submissions.entrySet());
        return new Map.Entry[]{list.get(0), list.get(1)};
    }
}
