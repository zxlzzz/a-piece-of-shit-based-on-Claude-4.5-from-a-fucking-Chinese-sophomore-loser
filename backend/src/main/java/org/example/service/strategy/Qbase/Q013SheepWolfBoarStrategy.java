package org.example.service.strategy.Qbase;

import org.example.service.buff.BuffApplier;
import org.example.service.strategy.BaseQuestionStrategy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 在一片丛林中，选择你的角色
 *
 * A.绵羊（选择此项的人平分12分，但如果有人选了灰狼则不得分）
 * B.灰狼（初始分0分，每有一个绵羊得5分）
 * C.野猪（获得4分）
 *
 * 3人
 */
@Component
public class Q013SheepWolfBoarStrategy extends BaseQuestionStrategy {

    public Q013SheepWolfBoarStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        long countSheep = submissions.values().stream().filter(c -> "A".equals(c)).count();
        boolean hasWolf = submissions.values().stream().anyMatch(c -> "B".equals(c));

        return submissions.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> switch (e.getValue()) {
                            case "A" -> hasWolf ? 0 : (int) (12 / countSheep);
                            case "B" -> (int) (5 * countSheep);
                            case "C" -> 4;
                            default -> 0;
                        }
                ));
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q013";
    }
}
