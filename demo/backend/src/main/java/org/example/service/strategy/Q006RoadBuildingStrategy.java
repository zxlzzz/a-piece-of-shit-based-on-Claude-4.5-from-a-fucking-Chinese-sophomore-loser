package org.example.service.strategy;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;

/**
 * 你们二人共同修路，总共需要 6 分。出分较高的人得 8 分，较低的得 4 分，相同则均得 4 分。
 * 2-8
 */
@Component
public class Q006RoadBuildingStrategy extends BaseQuestionStrategy {


    @Override
    public String getQuestionIdentifier() {
        return "Q006";
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        // 计算总贡献
        int total = submissions.values().stream()
            .mapToInt(Integer::parseInt)
            .sum();
        boolean roadFixed = total >= 6;

        // 按贡献排序（升序）
        var sorted = submissions.entrySet().stream()
            .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getValue())))
            .toList();

        int lowContribution = Integer.parseInt(sorted.get(0).getValue());
        int highContribution = Integer.parseInt(sorted.get(1).getValue());

        if (roadFixed) {
            // 修路成功：低贡献得4-贡献，高贡献得8-贡献
            return Map.of(
                sorted.get(0).getKey(), 4 - lowContribution,
                sorted.get(1).getKey(), 8 - highContribution
            );
        } else {
            // 修路失败：都扣除贡献
            return Map.of(
                sorted.get(0).getKey(), -lowContribution,
                sorted.get(1).getKey(), -highContribution
            );
        }
    }
}
