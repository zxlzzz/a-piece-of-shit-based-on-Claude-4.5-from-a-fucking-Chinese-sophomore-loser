package org.example.service.strategy;

import lombok.extern.slf4j.Slf4j;
import org.example.service.buff.BuffApplier;
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

    public Q010HouseTheftStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> playerChoices = submissions.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Integer.parseInt(e.getValue())));
        Map<String, Integer> scores = playerChoices.keySet().stream()
                .collect(Collectors.toMap(k -> k, k -> 0));
        for (int house = 1; house <= 7; house++) {
            int value = HOUSE_VALUES[house];
            int finalHouse = house;
            Map<String, Integer> distances = playerChoices.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            e -> Math.abs(e.getValue() - finalHouse)));
            int minDist = distances.values().stream().min(Integer::compareTo).orElse(Integer.MAX_VALUE);
            List<String> closest = distances.entrySet().stream()
                    .filter(e -> e.getValue() == minDist)
                    .map(Map.Entry::getKey)
                    .toList();
            switch (closest.size()) {
                case 1 -> scores.merge(closest.get(0), value, Integer::sum);
                case 2 -> {
                    int share = value / 2;
                    closest.forEach(p -> scores.merge(p, share, Integer::sum));
                }
            }
        }
        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q010";
    }
}