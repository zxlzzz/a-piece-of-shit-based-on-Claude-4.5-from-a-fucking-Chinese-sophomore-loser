package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.example.service.strategy.template.SortBasedTemplateStrategy;
import org.example.service.strategy.template.StrategyConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 你们二人都想要一个物品（得到后可以十分的价格出售），现从第三人手里购买（均可购买），物品的价格由分别报价，如果你们的报价相差大于等于3分，则出价较低的人无法购买，否则都按自己的出价购买，则你的报价为
 * 1-9
 */
@Component
public class Q009BiddingCompetitionStrategy extends SortBasedTemplateStrategy {

    public Q009BiddingCompetitionStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q009";
    }

    @Override
    protected StrategyConfig.SortBasedConfig getConfig() {
        return new StrategyConfig.SortBasedConfig() {
            @Override
            public java.util.function.Function<Map.Entry<String, String>, Integer> getSortKey() {
                return e -> Integer.parseInt(e.getValue());
            }

            @Override
            public java.util.function.Function<java.util.List<Map.Entry<String, String>>, Map<String, Integer>> getScoreCalculator() {
                return sorted -> {
                    int lowBid = Integer.parseInt(sorted.get(0).getValue());
                    int highBid = Integer.parseInt(sorted.get(1).getValue());
                    boolean tooFarApart = (highBid - lowBid) >= 3;

                    return Map.of(
                        sorted.get(0).getKey(), tooFarApart ? 0 : 10 - lowBid,
                        sorted.get(1).getKey(), 10 - highBid
                    );
                };
            }
        };
    }
}
