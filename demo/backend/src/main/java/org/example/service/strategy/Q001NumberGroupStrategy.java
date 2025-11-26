package org.example.service.strategy;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 现在数字被分为两组，123一组，456一组，你们选择一个数，如果位于同一组，则获得该分数，否则改为扣除
 */
@Component
public class Q001NumberGroupStrategy extends BaseQuestionStrategy {
    private static final Set<Integer> GROUP_A = Set.of(1, 2, 3);

    public Q001NumberGroupStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q001";
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        // 判断是否在同一组
        var values = submissions.values().stream()
            .map(Integer::parseInt)
            .toList();
        int a = values.get(0);
        int b = values.get(1);

        // 同组返回true，否则false
        boolean sameGroup = (GROUP_A.contains(a) && GROUP_A.contains(b)) ||
                           (!GROUP_A.contains(a) && !GROUP_A.contains(b));

        // 计算分数：同组得分，不同组扣分
        return submissions.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> {
                    int value = Integer.parseInt(e.getValue());
                    return sameGroup ? value : -value;
                }
            ));
    }
}
