package org.example.service.strategy;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 你们三人分别决定在一片牧场养牛的数量，如果牛的总数在3头及以下，每头牛可价值3分，以上则每超过一头牛的价值减1（可以减至负数），则你的选择为
 * 0-3
 */
@Component
public class Q011CattleRaiseStrategy extends BaseQuestionStrategy {

    public Q011CattleRaiseStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q011";
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        // 计算总牛数
        int total = submissions.values().stream()
            .mapToInt(Integer::parseInt)
            .sum();

        // 计算每头牛的价值
        // <=3头：每头3分
        // >3头：3-(总数-3)，可以为负数
        int value = total <= 3 ? 3 : 3 - (total - 3);

        // 计算每个玩家的分数 = 养牛数 * 每头价值
        return submissions.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> Integer.parseInt(e.getValue()) * value
            ));
    }
}
