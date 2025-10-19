package org.example.service.strategy.QR;

import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerSubmissionDTO;
import org.example.dto.QuestionDetailDTO;
import org.example.pojo.GameContext;
import org.example.pojo.PlayerGameState;
import org.example.service.buff.BuffApplier;
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

    public QR002NumberSumStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public int getTotalRounds() {
        return TOTAL_ROUNDS;
    }

    /**
     * ✅ 重写顶层方法，处理所有逻辑
     */
    @Override
    public QuestionDetailDTO calculateRoundResult(GameContext context, int currentRound) {
        Map<String, String> submissions = context.getCurrentSubmissions();

        // 1. 记录本轮选择
        recordChoices(context, submissions);

        // 2. 计算分数
        Map<String, Integer> baseScores;
        Map<String, Integer> finalScores;

        if (currentRound < TOTAL_ROUNDS) {
            // 前两轮：不计分，只记录
            baseScores = submissions.keySet().stream()
                    .collect(Collectors.toMap(id -> id, id -> 0));
            finalScores = new HashMap<>(baseScores);

            log.info("第 {}/{} 轮，暂不计分", currentRound, TOTAL_ROUNDS);

        } else {
            // 第3轮：统一计算
            baseScores = calculateFinalScores(context);
            finalScores = new HashMap<>(baseScores);  // 本题无buff

            log.info("第 {} 轮完成，开始计分", TOTAL_ROUNDS);
        }

        // 3. 构建玩家提交列表
        List<PlayerSubmissionDTO> playerSubmissions = buildPlayerSubmissions(
                context, submissions, baseScores, finalScores
        );

        // 4. 计算选项分布
        Map<String, Integer> choiceCounts = calculateChoiceCounts(submissions);

        // 5. 获取选项文本
        String optionText = getOptionText(context.getCurrentQuestion());

        // 6. 返回 DTO
        return QuestionDetailDTO.builder()
                .questionIndex(context.getCurrentQuestionIndex())
                .questionText(context.getCurrentQuestion().getText() +
                        " (第" + currentRound + "/" + TOTAL_ROUNDS + "轮)")
                .optionText(optionText)
                .questionType(context.getCurrentQuestion().getType())
                .playerSubmissions(playerSubmissions)
                .choiceCounts(choiceCounts)
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

            if (state.getCustomData() == null) {
                state.setCustomData(new HashMap<>());
            }

            @SuppressWarnings("unchecked")
            List<Integer> choices = (List<Integer>) state.getCustomData()
                    .computeIfAbsent(CHOICES_KEY, k -> new ArrayList<>());
            choices.add(choice);

            log.info("💾 玩家 {} 第 {} 次选择: {}", playerId, choices.size(), choice);
        }
    }

    /**
     * 计算最终得分（第3轮调用）
     */
    private Map<String, Integer> calculateFinalScores(GameContext context) {
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

            if (choices == null || choices.size() != TOTAL_ROUNDS) {
                log.warn("⚠️ 玩家 {} 选择记录异常", playerId);
                continue;
            }

            allChoices.put(playerId, new ArrayList<>(choices));
            log.info("✅ 玩家 {} 的3次选择: {}", playerId, choices);
        }

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
            log.info("玩家 {} 选择 {}，和为 {}", entry.getKey(), entry.getValue(), sum);
        }

        // 3. 找出最小和，+5分
        int minSum = sums.values().stream().min(Integer::compareTo).orElse(0);
        for (Map.Entry<String, Integer> entry : sums.entrySet()) {
            String playerId = entry.getKey();
            int sum = entry.getValue();

            if (sum == minSum) {
                scores.put(playerId, sum + 5);
                log.info("玩家 {} 和最小（{}），额外+5分", playerId, sum);
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
            log.info("唯一众数: {}（出现{}次），开始扣分", mode, maxCount);

            for (Map.Entry<String, List<Integer>> entry : allChoices.entrySet()) {
                String playerId = entry.getKey();
                long count = entry.getValue().stream().filter(n -> n == mode).count();

                if (count > 0) {
                    int penalty = mode * (int) count;
                    scores.put(playerId, scores.get(playerId) - penalty);
                    log.info("玩家 {} 选了{}次众数{}，扣除{}分", playerId, count, mode, penalty);
                }
            }
        } else {
            log.info("没有唯一众数（{}并列），不扣分", modes);
        }

        return scores;
    }

    @Override
    protected Map<String, Integer> calculateRoundBaseScores(
            Map<String, String> submissions, int currentRound) {
        throw new UnsupportedOperationException("请使用 calculateRoundResult");
    }

    @Override
    public String getQuestionIdentifier() {
        return "QR002";
    }
}