package org.example.service.strategy.QR;

import org.example.dto.PlayerSubmissionDTO;
import org.example.dto.QuestionDetailDTO;
import org.example.pojo.GameContext;
import org.example.pojo.PlayerGameState;
import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 三支团队各选一个地区（A/B/C），每轮派1人留在该地区（累积），共3轮，每轮都结算一次。
 * 基础价值：A=2，B=3，C=4；第x轮第x个地区价值翻倍：
 *   第1轮：A=4，B=3，C=4
 *   第2轮：A=2，B=6，C=4
 *   第3轮：A=2，B=3，C=8
 * 每轮结算：每个地区，人数唯一最多的团队独得全部价值；并列最多则整除平分；无人则不计。
 * 3人，3轮
 */
@Component
public class QR003ThreeAreaStrategy extends BaseRepeatableStrategy {

    private static final String KEY_PREFIX = "QR003_area_";
    private static final String[] AREAS = {"A", "B", "C"};

    // ROUND_VALUES[round-1][areaIndex] → 第round轮时各地区价值
    private static final int[][] ROUND_VALUES = {
        {4, 3, 4}, // 第1轮：A=4, B=3, C=4
        {2, 6, 4}, // 第2轮：A=2, B=6, C=4
        {2, 3, 8}  // 第3轮：A=2, B=3, C=8
    };

    public QR003ThreeAreaStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public int getTotalRounds() {
        return 3;
    }

    @Override
    public QuestionDetailDTO calculateRoundResult(GameContext context, int currentRound) {
        Map<String, String> submissions = context.getCurrentSubmissions();

        // 累积本轮各队派驻的人数
        for (Map.Entry<String, String> e : submissions.entrySet()) {
            PlayerGameState state = context.getPlayerStates().get(e.getKey());
            if (state == null) continue;
            if (state.getCustomData() == null) state.setCustomData(new HashMap<>());
            String key = KEY_PREFIX + e.getValue();
            int cur = (int) state.getCustomData().getOrDefault(key, 0);
            state.getCustomData().put(key, cur + 1);
        }

        // 每轮都结算，使用当轮对应的地区价值
        Map<String, Integer> baseScores = calcRound(context, currentRound);
        Map<String, Integer> finalScores = new HashMap<>(baseScores);

        List<PlayerSubmissionDTO> playerSubs = buildPlayerSubmissions(context, submissions, baseScores, finalScores);
        Map<String, Integer> choiceCounts = calculateChoiceCounts(submissions);

        return QuestionDetailDTO.builder()
                .questionIndex(context.getCurrentQuestionIndex())
                .questionText(context.getCurrentQuestion().getText() + " (第" + currentRound + "/3轮)")
                .optionText(getOptionText(context.getCurrentQuestion()))
                .questionType(context.getCurrentQuestion().getType())
                .playerSubmissions(playerSubs)
                .choiceCounts(choiceCounts)
                .build();
    }

    private Map<String, Integer> calcRound(GameContext context, int currentRound) {
        List<String> playerIds = new ArrayList<>(context.getPlayerStates().keySet());
        Map<String, Integer> scores = new HashMap<>();
        playerIds.forEach(id -> scores.put(id, 0));

        int[] values = ROUND_VALUES[currentRound - 1];

        for (int i = 0; i < AREAS.length; i++) {
            String area = AREAS[i];
            int areaValue = values[i];
            String key = KEY_PREFIX + area;

            Map<String, Integer> areaCounts = new HashMap<>();
            for (String pid : playerIds) {
                PlayerGameState s = context.getPlayerStates().get(pid);
                int cnt = (s != null && s.getCustomData() != null)
                        ? (int) s.getCustomData().getOrDefault(key, 0) : 0;
                areaCounts.put(pid, cnt);
            }

            int maxCnt = areaCounts.values().stream().max(Integer::compareTo).orElse(0);
            if (maxCnt == 0) continue;

            List<String> winners = areaCounts.entrySet().stream()
                    .filter(e -> e.getValue() == maxCnt)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            int share = areaValue / winners.size();
            winners.forEach(pid -> scores.merge(pid, share, Integer::sum));
        }

        return scores;
    }

    @Override
    protected Map<String, Integer> calculateRoundBaseScores(Map<String, String> submissions, int currentRound) {
        // 练习模式：无跨轮状态，无法模拟完整逻辑，返回0
        return submissions.keySet().stream().collect(Collectors.toMap(id -> id, id -> 0));
    }

    @Override
    public String getQuestionIdentifier() {
        return "QR003";
    }
}
