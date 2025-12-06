package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 你们二人都想在商店街开店，分核心地段和边缘地段，若选择相同则收益减少。
 * 高人流量：1核心（14）1边缘（10） 2核心（8） 2边缘（5）
 * 低人流量：核心（12） 边缘（8）
 * A.核心地段（消耗5分）
 * B.边缘地段（消耗3分）
 * C.什么都不做
 */
@Component
public class Q008ShopLocationStrategy extends BaseQuestionStrategy {
    public Q008ShopLocationStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        List<String> choices = submissions.values().stream().toList();
        boolean lowTraffic = choices.contains("C");

        return submissions.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> calculateSingleScore(e.getValue(), choices, lowTraffic)
                ));
    }

    private int calculateSingleScore(String myChoice, List<String> allChoices, boolean lowTraffic) {
        if ("C".equals(myChoice)) return 0;

        int cost = "A".equals(myChoice) ? 5 : 3;
        int revenue;

        if (lowTraffic) {
            // 低人流量：核心12，边缘8
            revenue = "A".equals(myChoice) ? 12 : 8;
        } else {
            // 高人流量：检查是否有人和我选择相同
            long sameCount = allChoices.stream().filter(c -> c.equals(myChoice)).count();

            if ("A".equals(myChoice)) {
                // 核心：独占14，共享8
                revenue = sameCount == 1 ? 14 : 8;
            } else {
                // 边缘：独占10，共享5
                revenue = sameCount == 1 ? 10 : 5;
            }
        }

        return revenue - cost;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q008";
    }
}
