package org.example.service.strategy.Qbase;

import org.example.service.buff.BuffApplier;
import org.example.service.strategy.BaseQuestionStrategy;
import org.example.utils.StrategyUtil.SubmissionCountUtil;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
public class Q008SameMinusStrategy extends BaseQuestionStrategy {
    public Q008SameMinusStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Long> counts = SubmissionCountUtil.countChoices(submissions);
        return submissions.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            int val = Integer.parseInt(e.getValue());
                            return counts.get(e.getValue()) == 1 ? val : -val;
                        }
                ));
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q008";
    }
}
