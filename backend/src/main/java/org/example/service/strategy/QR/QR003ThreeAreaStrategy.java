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
 * 三支团队各选一个地区（A/B/C），每轮派1人留在该地区，共3轮。
 * 三个地区基础价值：A=2，B=3，C=4；第x轮时第x个地区价值翻倍，最终为 A=4，B=6，C=8。
 * 最终计分：每个地区，人数唯一最多的团队独得全部价值；若并列最多则平分；人数为0则无人得分。
 * 3人，3轮
 *
 * 【实现说明】
 * "第x次选择时第x个地方价值会翻倍"解释为：三个地区在各自对应轮次翻倍后，
 * 统一在最后结算，最终价值为 A=4，B=6，C=8。
 */
@Component
public class QR003ThreeAreaStrategy extends BaseRepeatableStrategy {

    private static final String KEY_PREFIX = "QR003_area_";
    private static final Map<String, Integer> AREA_VALUES = Map.of("A", 4, "B", 6, "C", 8);

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

        // 累积每人每轮派到各地区的人数
        for (Map.Entry<String, String> e : submissions.entrySet()) {
            PlayerGameState state = context.getPlayerStates().get(e.getKey());
            if (state == null) continue;
            if (state.getCustomData() == null) state.setCustomData(new HashMap<>());
            String key = KEY_PREFIX + e.getValue();
            int cur = (int) state.getCustomData().getOrDefault(key, 0);
            state.getCustomData().put(key, cur + 1);
        }

        Map<String, Integer> baseScores = currentRound < getTotalRounds()
                ? submissions.keySet().stream().collect(Collectors.toMap(id -> id, id -> 0))
                : calcFinal(context);

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

    private Map<String, Integer> calcFinal(GameContext context) {
        List<String> playerIds = new ArrayList<>(context.getPlayerStates().keySet());
        Map<String, Integer> scores = new HashMap<>();
        playerIds.forEach(id -> scores.put(id, 0));

        for (String area : AREA_VALUES.keySet()) {
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

            int share = AREA_VALUES.get(area) / winners.size();
            winners.forEach(pid -> scores.merge(pid, share, Integer::sum));
        }

        return scores;
    }

    @Override
    protected Map<String, Integer> calculateRoundBaseScores(Map<String, String> submissions, int currentRound) {
        // 练习模式：无跨轮状态，按最终轮返回0（无法模拟完整逻辑）
        return submissions.keySet().stream().collect(Collectors.toMap(id -> id, id -> 0));
    }

    @Override
    public String getQuestionIdentifier() {
        return "QR003";
    }
}
