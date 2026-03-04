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
 * 共4轮，2人对战。每轮选择：
 * A. 得3分（立即计入本轮得分）
 * B. 获得6点攻击力（累积）
 * C. 获得9点防御力（累积）
 * 最后一轮结算后，进行战斗：己方攻击力 - 对方防御力 = 战斗得分（可为负）。
 * 2人
 */
@Component
public class QR004AttackDefenseStrategy extends BaseRepeatableStrategy {

    private static final String KEY_ATTACK = "QR004_attack";
    private static final String KEY_DEFENSE = "QR004_defense";

    public QR004AttackDefenseStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public int getTotalRounds() {
        return 4;
    }

    @Override
    public QuestionDetailDTO calculateRoundResult(GameContext context, int currentRound) {
        Map<String, String> submissions = context.getCurrentSubmissions();

        // 应用本轮选择：A立即得3分，B/C累积攻击/防御
        Map<String, Integer> baseScores = new HashMap<>();
        for (Map.Entry<String, String> e : submissions.entrySet()) {
            String pid = e.getKey();
            String choice = e.getValue();
            PlayerGameState s = context.getPlayerStates().get(pid);
            if (s == null) continue;
            if (s.getCustomData() == null) s.setCustomData(new HashMap<>());

            int roundScore = 0;
            if ("A".equals(choice)) {
                roundScore = 3;
            } else if ("B".equals(choice)) {
                int cur = (int) s.getCustomData().getOrDefault(KEY_ATTACK, 0);
                s.getCustomData().put(KEY_ATTACK, cur + 6);
            } else if ("C".equals(choice)) {
                int cur = (int) s.getCustomData().getOrDefault(KEY_DEFENSE, 0);
                s.getCustomData().put(KEY_DEFENSE, cur + 9);
            }
            baseScores.put(pid, roundScore);
        }

        // 最后一轮：追加战斗得分
        if (currentRound == getTotalRounds()) {
            List<String> pids = new ArrayList<>(submissions.keySet());
            if (pids.size() == 2) {
                String pid1 = pids.get(0), pid2 = pids.get(1);
                int atk1 = getAttr(context, pid1, KEY_ATTACK);
                int def1 = getAttr(context, pid1, KEY_DEFENSE);
                int atk2 = getAttr(context, pid2, KEY_ATTACK);
                int def2 = getAttr(context, pid2, KEY_DEFENSE);
                baseScores.merge(pid1, atk1 - def2, Integer::sum);
                baseScores.merge(pid2, atk2 - def1, Integer::sum);
            }
        }

        Map<String, Integer> finalScores = applyBuffs(context, baseScores);
        decreaseBuffDuration(context);
        if (currentRound == getTotalRounds()) clearRepeatableBuffs(context);
        applyNextRoundBuffs(context, submissions, currentRound);

        List<PlayerSubmissionDTO> playerSubs = buildPlayerSubmissions(context, submissions, baseScores, finalScores);
        Map<String, Integer> choiceCounts = calculateChoiceCounts(submissions);

        return QuestionDetailDTO.builder()
                .questionIndex(context.getCurrentQuestionIndex())
                .questionText(context.getCurrentQuestion().getText() + " (第" + currentRound + "/4轮)")
                .optionText(getOptionText(context.getCurrentQuestion()))
                .questionType(context.getCurrentQuestion().getType())
                .playerSubmissions(playerSubs)
                .choiceCounts(choiceCounts)
                .build();
    }

    private int getAttr(GameContext context, String pid, String key) {
        PlayerGameState s = context.getPlayerStates().get(pid);
        if (s == null || s.getCustomData() == null) return 0;
        return (int) s.getCustomData().getOrDefault(key, 0);
    }

    @Override
    protected Map<String, Integer> calculateRoundBaseScores(Map<String, String> submissions, int currentRound) {
        // 练习模式：仅计算A选项的即时得分，不含战斗结算
        return submissions.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> "A".equals(e.getValue()) ? 3 : 0
        ));
    }

    @Override
    public String getQuestionIdentifier() {
        return "QR004";
    }
}
