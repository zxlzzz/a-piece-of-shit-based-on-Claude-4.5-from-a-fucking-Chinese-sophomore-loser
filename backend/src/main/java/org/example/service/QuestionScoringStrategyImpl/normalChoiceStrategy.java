package org.example.service.QuestionScoringStrategyImpl;

import lombok.extern.slf4j.Slf4j;
import org.example.pojo.*;
import org.example.service.QuestionScoringStrategy;

import java.util.*;

@Slf4j
public class normalChoiceStrategy implements QuestionScoringStrategy {

    @Override
    public QuestionResult calculateResult(GameContext context) {
        // 存储基础分数：playerId -> score
        Map<String, Integer> baseScores = new HashMap<>();

        // 存储最终分数：playerId -> score（应用buff后）
        Map<String, Integer> finalScores = new HashMap<>();

        // 记录本题产生的事件
        List<GameEvent> events = new ArrayList<>();

        // 获取本题所有玩家的提交：playerId -> choice (A/B/C)
        Map<String, String> submissions = context.getCurrentSubmissions();

        // 步骤1：计算基础分数
        calculateBaseScores(submissions, baseScores, events);

        // 步骤2：应用当前生效的buff（如上一题给的翻倍或减半）
        applyCurrentBuffs(context, baseScores, finalScores, events);

        // 步骤3：根据本题选择，给玩家添加下一题的buff
        applyNextQuestionBuffs(context, submissions, events);

        // 步骤4：减少所有buff的持续时间
        decreaseBuffDuration(context);

        // 构建并返回结果
        return QuestionResult.builder()
                .questionIndex(context.getCurrentQuestionIndex())
                .baseScores(baseScores)
                .finalScores(finalScores)
                .events(events)
                .submissions(submissions)
                .build();
    }

    /**
     * 步骤1：计算基础分数
     * 规则：
     * - 选A：0分（效果是下次翻倍）
     * - 选B：2分
     * - 选C：0分（效果是对手下次减半）
     */
    private void calculateBaseScores(Map<String, String> submissions,
                                     Map<String, Integer> baseScores,
                                     List<GameEvent> events) {
        for (Map.Entry<String, String> entry : submissions.entrySet()) {
            String playerId = entry.getKey();
            String choice = entry.getValue();

            int score = 0;

            switch (choice) {
                case "A":
                    // 选择翻倍buff，本题不得分
                    events.add(GameEvent.builder()
                            .type("CHOICE_DOUBLE_BUFF")
                            .targetPlayerId(playerId)
                            .description("选择了下次得分翻倍")
                            .build());
                    break;

                case "B":
                    // 直接获得2分
                    score = 2;
                    events.add(GameEvent.builder()
                            .type("CHOICE_IMMEDIATE_SCORE")
                            .targetPlayerId(playerId)
                            .description("选择了立即获得2分")
                            .data(Map.of("score", 2))
                            .build());
                    break;

                case "C":
                    // 选择削弱对手，本题不得分
                    events.add(GameEvent.builder()
                            .type("CHOICE_DEBUFF_OPPONENT")
                            .targetPlayerId(playerId)
                            .description("选择了削弱对手下次得分")
                            .build());
                    break;

                default:
                    log.warn("未知选项: {}, 玩家: {}", choice, playerId);
                    score = 0;
            }

            baseScores.put(playerId, score);
        }
    }

    /**
     * 步骤2：应用当前生效的buff
     * 遍历每个玩家的activeBuffs，根据buff类型修改分数
     */
    private void applyCurrentBuffs(GameContext context,
                                   Map<String, Integer> baseScores,
                                   Map<String, Integer> finalScores,
                                   List<GameEvent> events) {
        for (Map.Entry<String, Integer> entry : baseScores.entrySet()) {
            String playerId = entry.getKey();
            int baseScore = entry.getValue();
            int finalScore = baseScore;

            // 获取该玩家的状态
            PlayerGameState playerState = context.getPlayerStates().get(playerId);
            if (playerState == null || playerState.getActiveBuffs() == null) {
                finalScores.put(playerId, finalScore);
                continue;
            }

            // 遍历该玩家的所有buff
            for (Buff buff : playerState.getActiveBuffs()) {
                // 只处理持续时间为0的buff（即本题生效的buff）
                if (buff.getDuration() != 0) {
                    continue;
                }

                switch (buff.getType()) {
                    case "SCORE_DOUBLE":
                        // 得分翻倍
                        finalScore = finalScore * 2;
                        events.add(GameEvent.builder()
                                .type("BUFF_APPLIED_DOUBLE")
                                .targetPlayerId(playerId)
                                .description("得分翻倍buff生效")
                                .data(Map.of("originalScore", baseScore, "finalScore", finalScore))
                                .build());
                        log.info("玩家 {} 得分翻倍: {} -> {}", playerId, baseScore, finalScore);
                        break;

                    case "SCORE_HALVED":
                        // 得分减半（向下取整）
                        finalScore = finalScore / 2;
                        events.add(GameEvent.builder()
                                .type("DEBUFF_APPLIED_HALVED")
                                .targetPlayerId(playerId)
                                .sourcePlayerId((String) buff.getParams().get("sourcePlayerId"))
                                .description("得分减半debuff生效")
                                .data(Map.of("originalScore", baseScore, "finalScore", finalScore))
                                .build());
                        log.info("玩家 {} 得分减半: {} -> {}", playerId, baseScore, finalScore);
                        break;
                }
            }

            finalScores.put(playerId, finalScore);
        }
    }

    /**
     * 步骤3：根据本题选择，给玩家添加下一题的buff
     * 注意：duration=1表示下一题生效（在下一题开始时会-1变成0）
     */
    private void applyNextQuestionBuffs(GameContext context,
                                        Map<String, String> submissions,
                                        List<GameEvent> events) {
        // 构建玩家ID列表，用于找到对手
        List<String> playerIds = new ArrayList<>(submissions.keySet());

        for (Map.Entry<String, String> entry : submissions.entrySet()) {
            String playerId = entry.getKey();
            String choice = entry.getValue();

            PlayerGameState playerState = context.getPlayerStates().get(playerId);
            if (playerState == null) {
                log.warn("玩家状态不存在: {}", playerId);
                continue;
            }

            // 确保activeBuffs已初始化
            if (playerState.getActiveBuffs() == null) {
                playerState.setActiveBuffs(new ArrayList<>());
            }

            switch (choice) {
                case "A":
                    // 给自己添加下次翻倍buff
                    Buff doubleBuff = Buff.builder()
                            .type("SCORE_DOUBLE")
                            .duration(1) // 下一题生效
                            .params(new HashMap<>())
                            .build();
                    playerState.getActiveBuffs().add(doubleBuff);

                    events.add(GameEvent.builder()
                            .type("BUFF_GRANTED_DOUBLE")
                            .targetPlayerId(playerId)
                            .description("获得下次得分翻倍buff")
                            .build());
                    log.info("玩家 {} 获得下次翻倍buff", playerId);
                    break;

                case "C":
                    // 给对手添加下次减半debuff
                    String opponentId = findOpponent(playerId, playerIds);
                    if (opponentId != null) {
                        PlayerGameState opponentState = context.getPlayerStates().get(opponentId);
                        if (opponentState != null) {
                            if (opponentState.getActiveBuffs() == null) {
                                opponentState.setActiveBuffs(new ArrayList<>());
                            }

                            Buff halvedBuff = Buff.builder()
                                    .type("SCORE_HALVED")
                                    .duration(1) // 下一题生效
                                    .params(Map.of("sourcePlayerId", playerId))
                                    .build();
                            opponentState.getActiveBuffs().add(halvedBuff);

                            events.add(GameEvent.builder()
                                    .type("DEBUFF_GRANTED_HALVED")
                                    .targetPlayerId(opponentId)
                                    .sourcePlayerId(playerId)
                                    .description("被施加下次得分减半debuff")
                                    .build());
                            log.info("玩家 {} 给对手 {} 施加了下次减半debuff", playerId, opponentId);
                        }
                    }
                    break;

                case "B":
                    // 选B没有额外效果
                    break;
            }
        }
    }

    /**
     * 步骤4：减少所有buff的持续时间
     * duration会递减，当变成负数时移除buff
     */
    private void decreaseBuffDuration(GameContext context) {
        for (PlayerGameState playerState : context.getPlayerStates().values()) {
            if (playerState.getActiveBuffs() == null) {
                continue;
            }

            // 使用迭代器安全地移除过期buff
            Iterator<Buff> iterator = playerState.getActiveBuffs().iterator();
            while (iterator.hasNext()) {
                Buff buff = iterator.next();

                // duration减1
                buff.setDuration(buff.getDuration() - 1);

                // 如果duration小于0，说明已经生效过了，移除
                if (buff.getDuration() < 0) {
                    iterator.remove();
                    log.info("移除过期buff: type={}, 剩余duration={}", buff.getType(), buff.getDuration());
                }
            }
        }
    }

    /**
     * 辅助方法：在两人游戏中找到对手
     * @param playerId 当前玩家ID
     * @param playerIds 所有玩家ID列表
     * @return 对手ID，如果找不到返回null
     */
    private String findOpponent(String playerId, List<String> playerIds) {
        for (String id : playerIds) {
            if (!id.equals(playerId)) {
                return id;
            }
        }
        return null;
    }

    @Override
    public String getQuestionIdentifier() {
        return "strategy_choice"; // 该策略的唯一标识符
    }
}