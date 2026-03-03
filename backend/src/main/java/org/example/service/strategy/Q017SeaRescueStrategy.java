package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.example.utils.StrategyUtil.SubmissionCountUtil;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 海难逃生，6人选择逃生工具：
 * A. 专业潜水服（1人用，得10分；超过1人则每人-3分）
 * B. 木筏（不超过4人，每人6分；超过4人则每人-5分）
 * C. 皮划艇（恰好2人，每人8分；非2人则每人0分）
 */
@Component
public class Q017SeaRescueStrategy extends BaseQuestionStrategy {

    public Q017SeaRescueStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Long> counts = SubmissionCountUtil.countChoices(submissions);
        return submissions.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> {
                    long cnt = counts.getOrDefault(e.getValue(), 0L);
                    return switch (e.getValue()) {
                        case "A" -> cnt == 1 ? 10 : -3;
                        case "B" -> cnt <= 4 ? 6 : -5;
                        case "C" -> cnt == 2 ? 8 : 0;
                        default -> 0;
                    };
                }
        ));
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q017";
    }
}
