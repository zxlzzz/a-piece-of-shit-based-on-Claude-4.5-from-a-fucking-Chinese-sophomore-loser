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
 * 共5轮，4人，每轮选1-5并获得对应分数。
 * 本轮计分前，检查截至上一轮的组内累计得分：
 *   - 唯一最高者：本轮得0分
 *   - 唯一最低者：本轮得分翻倍
 * （第1轮所有人分数相同均为0，两个条件均不触发）
 * 4人，5轮
 */
@Component
public class QR005DynamicBidStrategy extends BaseRepeatableStrategy {

    private static final String KEY = "QR005_cumScore";

    public QR005DynamicBidStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public int getTotalRounds() {
        return 5;
    }

    @Override
    public QuestionDetailDTO calculateRoundResult(GameContext context, int currentRound) {
        Map<String, String> submissions = context.getCurrentSubmissions();

        // 读取上一轮结束后的组内累计得分
        Map<String, Integer> cumScores = new HashMap<>();
        for (Map.Entry<String, PlayerGameState> e : context.getPlayerStates().entrySet()) {
            int cum = (e.getValue().getCustomData() != null)
                    ? (int) e.getValue().getCustomData().getOrDefault(KEY, 0) : 0;
            cumScores.put(e.getKey(), cum);
        }

        int maxCum = cumScores.values().stream().max(Integer::compareTo).orElse(0);
        int minCum = cumScores.values().stream().min(Integer::compareTo).orElse(0);
        long maxCount = cumScores.values().stream().filter(v -> v == maxCum).count();
        long minCount = cumScores.values().stream().filter(v -> v == minCum).count();
        boolean hasUniqueMax = maxCount == 1;
        // 唯一最低还需排除"最高最低相同"（即所有人得分一样）的情况
        boolean hasUniqueMin = minCount == 1 && maxCum != minCum;

        // 计算本轮基础分
        Map<String, Integer> baseScores = new HashMap<>();
        for (Map.Entry<String, String> e : submissions.entrySet()) {
            String pid = e.getKey();
            int bid = Integer.parseInt(e.getValue());
            int score;
            if (hasUniqueMax && cumScores.getOrDefault(pid, 0) == maxCum) {
                score = 0;
            } else if (hasUniqueMin && cumScores.getOrDefault(pid, 0) == minCum) {
                score = bid * 2;
            } else {
                score = bid;
            }
            baseScores.put(pid, score);
        }

        Map<String, Integer> finalScores = applyBuffs(context, baseScores);
        decreaseBuffDuration(context);
        if (currentRound == getTotalRounds()) clearRepeatableBuffs(context);
        applyNextRoundBuffs(context, submissions, currentRound);

        // 更新组内累计得分（使用buff后的final分数）
        for (Map.Entry<String, String> e : submissions.entrySet()) {
            String pid = e.getKey();
            PlayerGameState s = context.getPlayerStates().get(pid);
            if (s == null) continue;
            if (s.getCustomData() == null) s.setCustomData(new HashMap<>());
            int prev = (int) s.getCustomData().getOrDefault(KEY, 0);
            s.getCustomData().put(KEY, prev + finalScores.getOrDefault(pid, 0));
        }

        List<PlayerSubmissionDTO> playerSubs = buildPlayerSubmissions(context, submissions, baseScores, finalScores);
        Map<String, Integer> choiceCounts = calculateChoiceCounts(submissions);

        return QuestionDetailDTO.builder()
                .questionIndex(context.getCurrentQuestionIndex())
                .questionText(context.getCurrentQuestion().getText() + " (第" + currentRound + "/5轮)")
                .optionText(getOptionText(context.getCurrentQuestion()))
                .questionType(context.getCurrentQuestion().getType())
                .playerSubmissions(playerSubs)
                .choiceCounts(choiceCounts)
                .build();
    }

    @Override
    protected Map<String, Integer> calculateRoundBaseScores(Map<String, String> submissions, int currentRound) {
        // 练习模式：无跨轮状态，直接返回出价（无修正）
        return submissions.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> Integer.parseInt(e.getValue())
        ));
    }

    @Override
    public String getQuestionIdentifier() {
        return "QR005";
    }
}
