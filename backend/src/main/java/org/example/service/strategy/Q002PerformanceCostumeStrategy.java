package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;
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
    public Q002PerformanceCostumeStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

//    @Override
//    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
//        Map<String, Integer> scores = new HashMap<>();
//        Iterator<Map.Entry<String, String>> it = submissions.entrySet().iterator();
//        Map.Entry<String, String> p1 = it.next(), p2 = it.next();
//
//        String c1 = p1.getValue(), c2 = p2.getValue();
//        boolean complete = (c1.equals("A") || c1.equals("C") || c2.equals("A") || c2.equals("C"))
//                        && (c1.equals("B") || c2.equals("B"));
//
//        int v1 = c1.equals("A") ? 7 : c1.equals("B") ? 5 : 3;
//        int v2 = c2.equals("A") ? 7 : c2.equals("B") ? 5 : 3;
//
//        scores.put(p1.getKey(), complete ? v1 : -v1);
//        scores.put(p2.getKey(), complete ? v2 : -v2);
//        return scores;
//    }

    @Override
    public String getQuestionIdentifier() {
        return "Q002";
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        boolean complete = submissions.containsValue("B")&&submissions.containsValue("C")||submissions.containsValue("A");
        return submissions.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e->{
                            int v = switch(e.getValue()){
                                case "A" -> 7;
                                case "B" -> 5;
                                default -> 3;
                            };
                            return complete? v:-v;
                        }
                ));
    }
}
