package org.example.service.strategy;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;

/**
 * 你们二人遗失了同一个物品，向保险公司索赔。如果两人价格相同，则都获得该分数，否则高价者得 出价-3，低价者得出价。
 * 2-8
 */
@Component
public class Q005InsuranceClaimStrategy extends BaseQuestionStrategy {

    public Q005InsuranceClaimStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q005";
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        // 按索赔金额排序（升序）
        var sorted = submissions.entrySet().stream()
            .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getValue())))
            .toList();

        int lowClaim = Integer.parseInt(sorted.get(0).getValue());
        int highClaim = Integer.parseInt(sorted.get(1).getValue());
        boolean same = (lowClaim == highClaim);

        // 相同都得原价，否则高价者-3
        return Map.of(
            sorted.get(0).getKey(), lowClaim,
            sorted.get(1).getKey(), same ? highClaim : highClaim - 3
        );
    }
}
