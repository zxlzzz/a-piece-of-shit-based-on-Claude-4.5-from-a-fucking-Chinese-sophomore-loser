package org.example.service.strategy;

import lombok.extern.slf4j.Slf4j;
import org.example.pojo.GameEvent;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Q010: 小偷分赃题
 *
 * 题目：你们三人分别有一支小偷团队，计划偷7家连续的无人房子（编号1-7）
 *
 * 规则：
 * 1. 每个玩家选择一个房子作为起点（可重复选择）
 * 2. 计算每个玩家到所有房子的距离（曼哈顿距离，|选择的房子 - 目标房子|）
 * 3. 对于每个房子：
 *    - 如果只有1人离它最近 → 该玩家独得该房子的全部价值
 *    - 如果2人并列最近 → 两人平分该房子的价值（向下取整）
 *    - 如果3人一样近 → 该房子的价值作废，没人获得
 * 4. 距离为0（自己选择的房子）也算"唯一最近"
 *
 * 房子价值：
 * 房子编号：  1   2   3   4   5   6   7
 * 价值：     6   5   5   4   4   4   3
 *
 * 示例（3个玩家）：
 * - 玩家A选3号房
 * - 玩家B选5号房
 * - 玩家C选6号房
 *
 * 距离计算：
 * 房子1: A距离2, B距离4, C距离5 → A唯一最近 → A+6分
 * 房子2: A距离1, B距离3, C距离4 → A唯一最近 → A+5分
 * 房子3: A距离0, B距离2, C距离3 → A唯一最近 → A+5分
 * 房子4: A距离1, B距离1, C距离2 → A和B并列 → A+2分, B+2分
 * 房子5: A距离2, B距离0, C距离1 → B唯一最近 → B+4分
 * 房子6: A距离3, B距离1, C距离0 → C唯一最近 → C+4分
 * 房子7: A距离4, B距离2, C距离1 → C唯一最近 → C+3分
 *
 * 最终得分：A=18, B=6, C=7
 */
@Component
@Slf4j
public class Q010HouseTheftStrategy extends BaseQuestionStrategy {

    // 房子价值（下标0不用，1-7号房对应下标1-7）
    private static final int[] HOUSE_VALUES = {0, 6, 5, 5, 4, 4, 4, 3};

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();

        // 解析玩家选择
        Map<String, Integer> playerChoices = new HashMap<>();
        for (Map.Entry<String, String> entry : submissions.entrySet()) {
            playerChoices.put(entry.getKey(), Integer.parseInt(entry.getValue()));
        }

        // 初始化分数
        for (String playerId : playerChoices.keySet()) {
            scores.put(playerId, 0);
        }

        // 遍历每个房子，计算谁能获得分数
        for (int house = 1; house <= 7; house++) {
            int value = HOUSE_VALUES[house];

            // 计算每个玩家到这个房子的距离
            Map<String, Integer> distances = new HashMap<>();
            for (Map.Entry<String, Integer> entry : playerChoices.entrySet()) {
                String playerId = entry.getKey();
                int choice = entry.getValue();
                int distance = Math.abs(choice - house);
                distances.put(playerId, distance);
            }

            // 找出最小距离
            int minDistance = distances.values().stream()
                    .min(Integer::compareTo)
                    .orElse(Integer.MAX_VALUE);

            // 找出所有距离等于最小距离的玩家
            List<String> closestPlayers = distances.entrySet().stream()
                    .filter(e -> e.getValue() == minDistance)
                    .map(Map.Entry::getKey)
                    .toList();

            // 根据最近玩家数量分配分数
            if (closestPlayers.size() == 1) {
                // 唯一最近：独得全部价值
                String playerId = closestPlayers.get(0);
                scores.put(playerId, scores.get(playerId) + value);

            } else if (closestPlayers.size() == 2) {
                // 两人并列：平分（向下取整）
                int shareValue = value / 2;
                for (String playerId : closestPlayers) {
                    scores.put(playerId, scores.get(playerId) + shareValue);
                }

            }
                // 三人一样近：价值作废，没人获得
                // 不做处理

        }

        return scores;
    }

    @Override
    protected List<GameEvent> generateEvents(Map<String, String> submissions, Map<String, Integer> baseScores) {
        List<GameEvent> events = new ArrayList<>();

        // 解析玩家选择
        Map<String, Integer> playerChoices = new HashMap<>();
        List<String> playerIds = new ArrayList<>(submissions.keySet());
        for (Map.Entry<String, String> entry : submissions.entrySet()) {
            playerChoices.put(entry.getKey(), Integer.parseInt(entry.getValue()));
        }

        // 记录每个玩家的选择
        for (Map.Entry<String, Integer> entry : playerChoices.entrySet()) {
            events.add(GameEvent.builder()
                    .type("PLAYER_CHOICE")
                    .targetPlayerId(entry.getKey())
                    .description("选择了 " + entry.getValue() + " 号房作为起点")
                    .build());
        }

        // 详细记录每个房子的分配情况
        for (int house = 1; house <= 7; house++) {
            int value = HOUSE_VALUES[house];

            // 计算距离
            Map<String, Integer> distances = new HashMap<>();
            for (Map.Entry<String, Integer> entry : playerChoices.entrySet()) {
                int distance = Math.abs(entry.getValue() - house);
                distances.put(entry.getKey(), distance);
            }

            int minDistance = distances.values().stream().min(Integer::compareTo).orElse(0);
            List<String> closestPlayers = distances.entrySet().stream()
                    .filter(e -> e.getValue() == minDistance)
                    .map(Map.Entry::getKey)
                    .toList();

            String distanceInfo = distances.entrySet().stream()
                    .map(e -> "玩家" + (playerIds.indexOf(e.getKey()) + 1) + "距离" + e.getValue())
                    .collect(Collectors.joining(", "));

            if (closestPlayers.size() == 1) {
                events.add(GameEvent.builder()
                        .type("HOUSE_ALLOCATED")
                        .targetPlayerId(closestPlayers.get(0))
                        .description("房子" + house + "（价值" + value + "）：" + distanceInfo +
                                " → 玩家" + (playerIds.indexOf(closestPlayers.get(0)) + 1) +
                                "唯一最近，独得" + value + "分")
                        .build());

            } else if (closestPlayers.size() == 2) {
                int shareValue = value / 2;
                String players = closestPlayers.stream()
                        .map(id -> "玩家" + (playerIds.indexOf(id) + 1))
                        .collect(Collectors.joining("和"));

                events.add(GameEvent.builder()
                        .type("HOUSE_SHARED")
                        .description("房子" + house + "（价值" + value + "）：" + distanceInfo +
                                " → " + players + "并列最近，各得" + shareValue + "分")
                        .build());

            } else {
                events.add(GameEvent.builder()
                        .type("HOUSE_WASTED")
                        .description("房子" + house + "（价值" + value + "）：" + distanceInfo +
                                " → 三人距离相同，价值作废")
                        .build());
            }
        }

        return events;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q010";
    }
}