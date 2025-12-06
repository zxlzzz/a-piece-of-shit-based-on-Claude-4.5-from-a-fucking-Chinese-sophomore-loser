package org.example.service.strategy;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 你们二人参加演出，盲选服装。如果集齐侍卫+王子，则获得选项分数，否则扣分。
 *         "key": "A",
 *         "text": "精致的侍卫服装（7）"
 *         "key": "B",
 *         "text": "王子服装（5）"
 *         "key": "C",
 *         "text": "普通侍卫服装（3）"
 */
@Component
public class Q002PerformanceCostumeStrategy extends BaseQuestionStrategy {

    private static final Map<String, Integer> COSTUME_VALUES = Map.of(
        "A", 7,  // 精致侍卫
        "B", 5,  // 王子
        "C", 3   // 普通侍卫
    );


    @Override
    public String getQuestionIdentifier() {
        return "Q002";
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        // 集齐侍卫+王子：必须有B（王子）且有A或C（任一侍卫）
        boolean hasKing = submissions.containsValue("B");
        boolean hasGuard = submissions.containsValue("A") || submissions.containsValue("C");
        boolean success = hasKing && hasGuard;

        // 成功得分，失败扣分
        return submissions.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> {
                    int value = COSTUME_VALUES.getOrDefault(e.getValue(), 3);
                    return success ? value : -value;
                }
            ));
    }
}
