package org.example.service.strategy;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 酸奶发酵中，有三类微生物的互作：
 * 乳酸链球菌（A），保加利亚乳杆菌（B）和酵母菌（C）
 * 其中（A）产生的作物会促进（B）的生长，（B）产生的作物会促进（C）的生长，
 * 而（C）产生的作物却会抑制（A）的活性，你们分别扮演一类菌群，受到另外两个菌种数量的影响
 *
 * A.乳酸链球菌（获得8分，每有一个C扣2分）
 * B.保加利亚乳杆菌（每有一个A得4分）
 * C.酵母菌（每有一个B得5分）
 *
 * 4-7人
 */
@Component
public class Q003YogurtMicrobeStrategy extends BaseQuestionStrategy {

    public Q003YogurtMicrobeStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        long countA = submissions.values().stream().filter(c -> "A".equals(c)).count();
        long countB = submissions.values().stream().filter(c -> "B".equals(c)).count();
        long countC = submissions.values().stream().filter(c -> "C".equals(c)).count();

        return submissions.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> switch (e.getValue()) {
                            case "A" -> (int) (8 - 2 * countC);
                            case "B" -> (int) (4 * countA);
                            case "C" -> (int) (5 * countB);
                            default -> 0;
                        }
                ));
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q003";
    }
}
