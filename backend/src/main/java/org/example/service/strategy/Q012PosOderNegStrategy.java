package org.example.service.strategy;

import lombok.extern.slf4j.Slf4j;
import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 选择一项（x初始为0，偶数次取反后为正数
 * 4人 3选
 * A.x*2最后获得x的分数
 * B.x取反，无获得
 * C.什么都不做
 */
@Component
@Slf4j
public class Q012PosOderNegStrategy extends BaseQuestionStrategy {

    public Q012PosOderNegStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();
        int x = 1;

        // 顺序执行所有人的选择
        for (String choice : submissions.values()) {
            switch (choice) {
                case "A" -> x *= 2;
                case "B" -> x = -x;
                case "C" -> {} // 什么都不做
            }
        }

        int finalX = x;
        // 结算阶段：选A的得 finalX，其余得0
        submissions.forEach((id, choice) ->
                scores.put(id, "A".equals(choice) ? finalX : 0)
        );

        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q012";
    }
}