package org.example.service.strategy.Qbase;

import org.example.service.buff.BuffApplier;
import org.example.service.strategy.BaseQuestionStrategy;
import org.example.utils.StrategyUtil.SubmissionCountUtil;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 选择一项，如果你的选择仅有一人选择，获得选项对应的分数
 * A=3分，B=6分，C=10分
 * 2人
 */
@Component
public class Q015UniqueChoiceStrategy extends BaseQuestionStrategy {

    private static final Map<String, Integer> VALUES = Map.of("A", 3, "B", 6, "C", 10);

    public Q015UniqueChoiceStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Long> counts = SubmissionCountUtil.countChoices(submissions);
        return submissions.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> counts.get(e.getValue()) == 1 ? VALUES.getOrDefault(e.getValue(), 0) : 0
        ));
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q015";
    }
}
