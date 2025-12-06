package org.example.service.strategy;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;

/**
 * 你们二人都想要一个物品（得到后可以十分的价格出售），现从第三人手里购买（均可购买），物品的价格由分别报价，如果你们的报价相差大于等于3分，则出价较低的人无法购买，否则都按自己的出价购买，则你的报价为
 * 1-9
 */
@Component
public class Q009BiddingCompetitionStrategy extends BaseQuestionStrategy {


    @Override
    public String getQuestionIdentifier() {
        return "Q009";
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        // 按出价排序（升序）
        var sorted = submissions.entrySet().stream()
            .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getValue())))
            .toList();

        int lowBid = Integer.parseInt(sorted.get(0).getValue());
        int highBid = Integer.parseInt(sorted.get(1).getValue());
        boolean tooFarApart = (highBid - lowBid) >= 3;

        // 物品价值10分
        // 差距>=3：低价者无法购买得0分，高价者得10-出价
        // 差距<3：都可购买，各得10-出价
        return Map.of(
            sorted.get(0).getKey(), tooFarApart ? 0 : 10 - lowBid,
            sorted.get(1).getKey(), 10 - highBid
        );
    }
}
