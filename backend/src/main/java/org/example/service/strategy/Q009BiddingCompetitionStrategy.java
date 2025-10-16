package org.example.service.strategy;

import lombok.extern.slf4j.Slf4j;
import org.example.pojo.GameEvent;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Q0XX: 竞价购买题
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

    @Override
    protected Map<String, Integer> calculateBaseScores(
            Map<String, String> submissions) {

        Map<String, Integer> scores = new HashMap<>();
        List<GameEvent> events = new ArrayList<>();

        // 1. 解析所有玩家的出价
        Map<String, Integer> bids = new HashMap<>();
        for (Map.Entry<String, String> entry : submissions.entrySet()) {
            String playerId = entry.getKey();
            int bid = Integer.parseInt(entry.getValue());
            bids.put(playerId, bid);

            events.add(GameEvent.builder()
                    .type("BID_SUBMITTED")
                    .targetPlayerId(playerId)
                    .description("出价：" + bid + "分")
                    .build());
        }

        // 2. 如果只有1个玩家（不应该发生，但防御性编程）
        if (bids.size() == 1) {
            String playerId = bids.keySet().iterator().next();
            int bid = bids.get(playerId);
            int profit = SELL_PRICE - bid;
            scores.put(playerId, profit);

            events.add(GameEvent.builder()
                    .type("SINGLE_PLAYER")
                    .targetPlayerId(playerId)
                    .description("单人游戏，获得利润：" + profit + "分")
                    .build());

            return scores;
        }

        // 3. 多人情况：计算每对玩家之间的竞争
        List<String> playerIds = new ArrayList<>(bids.keySet());

        // 🔥 两人游戏的特殊处理
        if (playerIds.size() == 2) {
            String player1 = playerIds.get(0);
            String player2 = playerIds.get(1);
            int bid1 = bids.get(player1);
            int bid2 = bids.get(player2);

            int difference = Math.abs(bid1 - bid2);

            events.add(GameEvent.builder()
                    .type("BID_COMPARISON")
                    .description(String.format("出价差值：%d分（阈值：%d分）",
                            difference, DIFFERENCE_THRESHOLD))
                    .build());

            if (difference < DIFFERENCE_THRESHOLD) {
                // 差值 < 3，都能购买
                int profit1 = SELL_PRICE - bid1;
                int profit2 = SELL_PRICE - bid2;
                scores.put(player1, profit1);
                scores.put(player2, profit2);

                events.add(GameEvent.builder()
                        .type("BOTH_CAN_BUY")
                        .description("差值小于3，双方都能购买")
                        .build());

                events.add(GameEvent.builder()
                        .type("PROFIT_CALCULATED")
                        .targetPlayerId(player1)
                        .description(String.format("获得利润：10 - %d = %d分", bid1, profit1))
                        .build());

                events.add(GameEvent.builder()
                        .type("PROFIT_CALCULATED")
                        .targetPlayerId(player2)
                        .description(String.format("获得利润：10 - %d = %d分", bid2, profit2))
                        .build());

            } else {
                // 差值 ≥ 3，出价低的人无法购买
                String winner = bid1 > bid2 ? player1 : player2;
                String loser = bid1 > bid2 ? player2 : player1;
                int winnerBid = Math.max(bid1, bid2);

                int winnerProfit = SELL_PRICE - winnerBid;
                scores.put(winner, winnerProfit);
                scores.put(loser, 0);

                events.add(GameEvent.builder()
                        .type("ONLY_HIGH_BIDDER_WINS")
                        .description("差值≥3，出价较低者无法购买")
                        .build());

                events.add(GameEvent.builder()
                        .type("WINNER_PROFIT")
                        .targetPlayerId(winner)
                        .description(String.format("出价较高，获得利润：10 - %d = %d分",
                                winnerBid, winnerProfit))
                        .build());

                events.add(GameEvent.builder()
                        .type("LOSER_NO_PROFIT")
                        .targetPlayerId(loser)
                        .description("出价较低，无法购买（得0分）")
                        .build());
            }
        } else {
            // 🔥 多人游戏（3人及以上）：每个人与最接近自己的对手比较
            // 简化处理：每个人都能买（或者你想要其他规则？）
            for (String playerId : playerIds) {
                int bid = bids.get(playerId);
                int profit = SELL_PRICE - bid;
                scores.put(playerId, profit);
            }

            events.add(GameEvent.builder()
                    .type("MULTI_PLAYER")
                    .description("多人游戏，所有人都能购买")
                    .build());
        }

        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q009";  // 🔥 改成实际的题目ID
    }
}