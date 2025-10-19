package org.example.service.strategy;

import lombok.extern.slf4j.Slf4j;
import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Q009: 竞价购买题
 *
 * 题目：你们二人都想要一个物品（得到后可以十分的价格出售），
 *      现从第三人手里购买（均可购买），物品的价格由分别报价，
 *      如果你们的报价相差大于等于3分，则出价较低的人无法购买，
 *      否则都按自己的出价购买
 *
 * 规则：
 * - 物品售价：10分
 * - 报价范围：0-10分
 * - 差值 = |出价A - 出价B|
 *
 * 情况1：差值 < 3
 *   → 双方都能购买
 *   → 得分 = 10 - 出价
 *
 * 情况2：差值 ≥ 3
 *   → 出价较低的人无法购买（得0分）
 *   → 出价较高的人能购买（得分 = 10 - 出价）
 *
 * 示例：
 * - A出价5，B出价3 → 差值2 → 都能买 → A得5分，B得7分
 * - A出价8，B出价5 → 差值3 → B无法买 → A得2分，B得0分
 * - A出价5，B出价5 → 差值0 → 都能买 → A得5分，B得5分
 */
@Component
@Slf4j
public class Q009BiddingCompetitionStrategy extends BaseQuestionStrategy {

    private static final int SELL_PRICE = 10;  // 物品售价
    private static final int DIFFERENCE_THRESHOLD = 3;  // 差值阈值

    public Q009BiddingCompetitionStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();

        // 1. 解析所有玩家的出价
        Map<String, Integer> bids = new HashMap<>();
        for (Map.Entry<String, String> entry : submissions.entrySet()) {
            String playerId = entry.getKey();
            int bid = Integer.parseInt(entry.getValue());
            bids.put(playerId, bid);
            log.debug("玩家 {} 出价：{} 分", playerId, bid);
        }

        // 2. 如果只有1个玩家（不应该发生，但防御性编程）
        if (bids.size() == 1) {
            String playerId = bids.keySet().iterator().next();
            int bid = bids.get(playerId);
            int profit = SELL_PRICE - bid;
            scores.put(playerId, profit);
            log.info("单人游戏，玩家 {} 获得利润：{} 分", playerId, profit);
            return scores;
        }

        // 3. 两人游戏的处理
        if (bids.size() == 2) {
            List<String> playerIds = new ArrayList<>(bids.keySet());
            String player1 = playerIds.get(0);
            String player2 = playerIds.get(1);
            int bid1 = bids.get(player1);
            int bid2 = bids.get(player2);

            int difference = Math.abs(bid1 - bid2);
            log.info("出价差值：{} 分（阈值：{} 分）", difference, DIFFERENCE_THRESHOLD);

            if (difference < DIFFERENCE_THRESHOLD) {
                // 差值 < 3，都能购买
                int profit1 = SELL_PRICE - bid1;
                int profit2 = SELL_PRICE - bid2;
                scores.put(player1, profit1);
                scores.put(player2, profit2);

                log.info("差值小于3，双方都能购买");
                log.info("玩家 {} 获得利润：10 - {} = {} 分", player1, bid1, profit1);
                log.info("玩家 {} 获得利润：10 - {} = {} 分", player2, bid2, profit2);

            } else {
                // 差值 ≥ 3，出价低的人无法购买
                String winner = bid1 > bid2 ? player1 : player2;
                String loser = bid1 > bid2 ? player2 : player1;
                int winnerBid = Math.max(bid1, bid2);

                int winnerProfit = SELL_PRICE - winnerBid;
                scores.put(winner, winnerProfit);
                scores.put(loser, 0);

                log.info("差值≥3，出价较低者无法购买");
                log.info("玩家 {} 出价较高，获得利润：10 - {} = {} 分", winner, winnerBid, winnerProfit);
                log.info("玩家 {} 出价较低，无法购买（得0分）", loser);
            }
        }

        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q009";
    }
}