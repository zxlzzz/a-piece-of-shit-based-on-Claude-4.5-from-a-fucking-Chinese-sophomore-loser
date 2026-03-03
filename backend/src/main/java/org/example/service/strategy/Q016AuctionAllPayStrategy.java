package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 每人出价拍一个商品，出价均会被扣除，出价最高的人获得10分（多人出价最高则平分）
 * BID 0-7，4人
 */
@Component
public class Q016AuctionAllPayStrategy extends BaseQuestionStrategy {

    public Q016AuctionAllPayStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> bids = submissions.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Integer.parseInt(e.getValue())));

        int maxBid = bids.values().stream().max(Integer::compareTo).orElse(0);
        long winnerCount = bids.values().stream().filter(v -> v == maxBid).count();
        int prize = (int) (10 / winnerCount);

        return bids.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> (e.getValue() == maxBid ? prize : 0) - e.getValue()
        ));
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q016";
    }
}
