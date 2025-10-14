package org.example.service.QuestionScoringStrategyImpl.QR;

import lombok.extern.slf4j.Slf4j;
import org.example.pojo.GameContext;
import org.example.pojo.GameEvent;
import org.example.pojo.PlayerGameState;
import org.example.pojo.QuestionResult;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * QR002: 数字求和题（重复3次）
 *
 * 题目：选择一个偶数（2/4/6/8/10），本题共进行3次
 *
 * 计分规则（3轮全部结束后统一计分）：
 * 1. 计算每个玩家3次选择的数字之和
 * 2. 和最小的玩家额外获得5分（如有多人并列最小，都获得5分）
 * 3. 统计所有玩家3轮选择中，是否存在"唯一众数"（出现次数最多且唯一的数字）
 * 4. 如果存在唯一众数，则从每个玩家的和中扣除该数字的"出现次数×数值"
 *
 * 示例（3个玩家）：
 * - 玩家A选了：2, 4, 6 → 和=12
 * - 玩家B选了：4, 4, 6 → 和=14
 * - 玩家C选了：2, 6, 8 → 和=16
 * 计分过程：
 * 1. 和最小的是A（12），A额外+5分 → A暂时=17分
 * 2. 统计所有数字出现次数：2出现2次，4出现3次，6出现3次，8出现1次
 *    → 4和6并列最多（3次），没有"唯一"众数 → 不扣分
 * 3. 最终得分：A=17, B=14, C=16
 * 示例2（有唯一众数）：
 * - 玩家A选了：2, 4, 6 → 和=12
 * - 玩家B选了：4, 4, 6 → 和=14
 * - 玩家C选了：2, 6, 8 → 和=16
 * - 玩家D选了：4, 6, 10 → 和=20
 * 计分过程：
 * 1. 和最小的是A（12），A额外+5分 → A暂时=17分
 * 2. 统计所有数字出现次数：2出现2次，4出现4次，6出现4次，8出现1次，10出现1次
 *    → 4和6并列最多（4次），没有"唯一"众数 → 不扣分
 * 3. 最终得分：A=17, B=14, C=16, D=20
 * 示例3（有唯一众数）：
 * - 玩家A选了：2, 4, 6 → 和=12
 * - 玩家B选了：4, 4, 8 → 和=16
 * - 玩家C选了：2, 6, 10 → 和=18
 * 计分过程：
 * 1. 和最小的是A（12），A额外+5分 → A暂时=17分
 * 2. 统计所有数字出现次数：2出现2次，4出现3次，6出现2次，8出现1次，10出现1次
 *    → 唯一众数是4（出现3次）
 * 3. 扣除4的分数：
 *    - A：17 - 4×1 = 13（A选了1次4）
 *    - B：16 - 4×2 = 8（B选了2次4）
 *    - C：18 - 4×0 = 18（C没选4）
 * 4. 最终得分：A=13, B=8, C=18
 */
@Component
@Slf4j
public class QR002NumberSumStrategy extends BaseRepeatableStrategy {

    private static final int TOTAL_ROUNDS = 3;
    private static final String CHOICES_KEY = "QR002_choices";

    @Override
    public int getTotalRounds() {
        return TOTAL_ROUNDS;
    }

    /**
     * 重写顶层方法，直接处理所有逻辑
     * 这样就能访问到 GameContext 了
     */
    @Override
    public QuestionResult calculateRoundResult(GameContext context, int currentRound) {
        Map<String, String> submissions = context.getCurrentSubmissions();

        // 1. 记录本轮选择
        recordChoices(context, submissions);

        // 2. 计算分数（前两轮返回0，第3轮统一计算）
        Map<String, Integer> baseScores;
        Map<String, Integer> finalScores;
        List<GameEvent> events = new ArrayList<>();

        if (currentRound < TOTAL_ROUNDS) {
            // 前两轮：不计分，只记录
            baseScores = submissions.keySet().stream()
                    .collect(Collectors.toMap(id -> id, id -> 0));
            finalScores = new HashMap<>(baseScores);

            events.add(GameEvent.builder()
                    .type("ROUND_INFO")
                    .description("第 " + currentRound + "/" + TOTAL_ROUNDS + " 轮选择（暂不计分）")
                    .build());

        } else {
            // 第3轮：统一计算
            baseScores = calculateFinalScores(context, events);
            finalScores = new HashMap<>(baseScores);  // 本题无buff，直接复制

            events.add(GameEvent.builder()
                    .type("ROUND_INFO")
                    .description("第 " + TOTAL_ROUNDS + " 轮完成，开始计分")
                    .build());
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
     * 记录本轮选择到 customData
     */
    private void recordChoices(GameContext context, Map<String, String> submissions) {
        for (Map.Entry<String, String> entry : submissions.entrySet()) {
            String playerId = entry.getKey();
            int choice = Integer.parseInt(entry.getValue());

            PlayerGameState state = context.getPlayerStates().get(playerId);
            if (state == null) {
                log.warn("⚠️ 玩家状态不存在: {}", playerId);
                continue;
            }

            // 🔥 确保 customData 不为 null
            if (state.getCustomData() == null) {
                state.setCustomData(new HashMap<>());
                log.debug("🆕 初始化玩家 {} 的 customData", playerId);
            }

            @SuppressWarnings("unchecked")
            List<Integer> choices = (List<Integer>) state.getCustomData()
                    .computeIfAbsent(CHOICES_KEY, k -> new ArrayList<>());
            choices.add(choice);

            log.info("💾 玩家 {} 第 {} 次选择: {} (累计: {})",
                    playerId, choices.size(), choice, choices);
        }
    }

    /**
     * 计算最终得分（第3轮调用）
     */
    private Map<String, Integer> calculateFinalScores(GameContext context, List<GameEvent> events) {
        Map<String, Integer> scores = new HashMap<>();
        Map<String, List<Integer>> allChoices = new HashMap<>();

        // 1. 收集所有玩家的3次选择
        for (Map.Entry<String, PlayerGameState> entry : context.getPlayerStates().entrySet()) {
            String playerId = entry.getKey();
            PlayerGameState state = entry.getValue();

            if (state.getCustomData() == null) {
                log.error("❌ 玩家 {} 的 customData 为 null", playerId);
                continue;
            }

            @SuppressWarnings("unchecked")
            List<Integer> choices = (List<Integer>) state.getCustomData().get(CHOICES_KEY);

            if (choices == null) {
                log.error("❌ 玩家 {} 没有选择记录 ({})", playerId, CHOICES_KEY);
                continue;
            }

            if (choices.size() != TOTAL_ROUNDS) {
                log.warn("⚠️ 玩家 {} 选择次数不正确: {} (期望 {})",
                        playerId, choices.size(), TOTAL_ROUNDS);
                continue;
            }

            allChoices.put(playerId, new ArrayList<>(choices));
            log.info("✅ 玩家 {} 的3次选择: {}", playerId, choices);
        }

        // 🔥 如果没有收集到任何选择，返回全0
        if (allChoices.isEmpty()) {
            log.error("❌ 没有收集到任何玩家的选择记录！");
            return context.getPlayerStates().keySet().stream()
                    .collect(Collectors.toMap(id -> id, id -> 0));
        }

        // 2. 计算每个玩家的和
        Map<String, Integer> sums = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : allChoices.entrySet()) {
            int sum = entry.getValue().stream().mapToInt(Integer::intValue).sum();
            sums.put(entry.getKey(), sum);

            events.add(GameEvent.builder()
                    .type("PLAYER_SUM")
                    .targetPlayerId(entry.getKey())
                    .description("选择了 " + entry.getValue() + "，和为 " + sum)
                    .build());
        }

        // 3. 找出最小和，给对应玩家+5分
        int minSum = sums.values().stream().min(Integer::compareTo).orElse(0);
        for (Map.Entry<String, Integer> entry : sums.entrySet()) {
            String playerId = entry.getKey();
            int sum = entry.getValue();

            if (sum == minSum) {
                scores.put(playerId, sum + 5);
                events.add(GameEvent.builder()
                        .type("MIN_SUM_BONUS")
                        .targetPlayerId(playerId)
                        .description("和最小（" + sum + "），额外获得5分")
                        .build());
            } else {
                scores.put(playerId, sum);
            }
        }

        // 4. 统计所有数字的出现次数
        Map<Integer, Long> frequency = allChoices.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(n -> n, Collectors.counting()));

        // 5. 找出唯一众数
        long maxCount = frequency.values().stream().max(Long::compareTo).orElse(0L);
        List<Integer> modes = frequency.entrySet().stream()
                .filter(e -> e.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 6. 如果有唯一众数，扣除对应分数
        if (modes.size() == 1) {
            int mode = modes.get(0);

            events.add(GameEvent.builder()
                    .type("UNIQUE_MODE_FOUND")
                    .description("唯一众数为 " + mode + "（出现 " + maxCount + " 次），开始扣分")
                    .build());

            for (Map.Entry<String, List<Integer>> entry : allChoices.entrySet()) {
                String playerId = entry.getKey();
                long count = entry.getValue().stream().filter(n -> n == mode).count();

                if (count > 0) {
                    int penalty = mode * (int) count;
                    scores.put(playerId, scores.get(playerId) - penalty);

                    events.add(GameEvent.builder()
                            .type("MODE_PENALTY")
                            .targetPlayerId(playerId)
                            .description("选了 " + count + " 次众数 " + mode + "，扣除 " + penalty + " 分")
                            .build());
                }
            }
        } else {
            events.add(GameEvent.builder()
                    .type("NO_UNIQUE_MODE")
                    .description("没有唯一众数（" + modes + " 并列），不扣分")
                    .build());
        }

        return scores;
    }

    @Override
    protected Map<String, Integer> calculateRoundBaseScores(
            Map<String, String> submissions, int currentRound) {
        // 这个方法不会被调用，因为重写了 calculateRoundResult
        throw new UnsupportedOperationException("请使用 calculateRoundResult");
    }

    @Override
    public String getQuestionIdentifier() {
        return "QR002";
    }
}