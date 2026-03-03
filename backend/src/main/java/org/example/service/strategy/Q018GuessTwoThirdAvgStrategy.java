package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 所有人选择一个数（0-15，步长3），最接近所有人平均数*2/3的人获得10分（多人则平分）
 * 5人
 */
@Component
public class Q018GuessTwoThirdAvgStrategy extends BaseQuestionStrategy {

    public Q018GuessTwoThirdAvgStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> choices = submissions.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Integer.parseInt(e.getValue())));

        double avg = choices.values().stream().mapToInt(Integer::intValue).average().orElse(0);
        double target = avg * 2.0 / 3.0;

        double minDist = choices.values().stream()
                .mapToDouble(v -> Math.abs(v - target))
                .min().orElse(0);

        long winnerCount = choices.values().stream()
                .filter(v -> Math.abs(v - target) <= minDist + 1e-9)
                .count();
        int prize = (int) (10 / winnerCount);

        return choices.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> Math.abs(e.getValue() - target) <= minDist + 1e-9 ? prize : 0
        ));
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q018";
    }
}
