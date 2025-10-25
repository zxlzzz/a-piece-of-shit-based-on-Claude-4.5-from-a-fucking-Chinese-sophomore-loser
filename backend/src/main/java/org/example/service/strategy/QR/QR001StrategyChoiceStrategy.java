package org.example.service.strategy.QR;

import org.example.pojo.*;
import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class QR001StrategyChoiceStrategy extends BaseRepeatableStrategy {
    public QR001StrategyChoiceStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public int getTotalRounds() {
        return 4;
    }

    @Override
    protected Map<String, Integer> calculateRoundBaseScores(Map<String, String> submissions, int currentRound) {
        Map<String, Integer> scores = new HashMap<>();
        for (Map.Entry<String, String> e : submissions.entrySet()) {
            scores.put(e.getKey(), e.getValue().equals("A") ? 2 : 0);
        }
        return scores;
    }

    @Override
    protected void applyNextRoundBuffs(GameContext context, Map<String, String> submissions, int currentRound) {
        List<String> pids = new ArrayList<>(submissions.keySet());
        
        for (Map.Entry<String, String> e : submissions.entrySet()) {
            String pid = e.getKey();
            String choice = e.getValue();
            PlayerGameState state = context.getPlayerStates().get(pid);
            if (state == null) continue;
            if (state.getActiveBuffs() == null) state.setActiveBuffs(new ArrayList<>());

            if (choice.equals("B")) {
                state.getActiveBuffs().add(Buff.builder()
                    .type(BuffType.MULTIPLIER)
                    .value(2.0)
                    .duration(-1)
                    .params(Map.of("repeatableOnly", true, "triggerOnScore", true))
                    .build());
            } else if (choice.equals("C")) {
                String opp = pids.stream().filter(id -> !id.equals(pid)).findFirst().orElse(null);
                if (opp != null) {
                    PlayerGameState os = context.getPlayerStates().get(opp);
                    if (os != null) {
                        if (os.getActiveBuffs() == null) os.setActiveBuffs(new ArrayList<>());
                        os.getActiveBuffs().add(Buff.builder()
                            .type(BuffType.MULTIPLIER)
                            .value(0.5)
                            .duration(1)
                            .params(Map.of("repeatableOnly", true))
                            .build());
                    }
                }
            }
        }
    }

    @Override
    public String getQuestionIdentifier() {
        return "QR001";
    }
}
