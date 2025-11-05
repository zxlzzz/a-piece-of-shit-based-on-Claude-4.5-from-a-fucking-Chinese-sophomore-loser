package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 选择一项，仅当有一项选的人唯一最多和一项选的人唯一最少时计分，按剪刀石头布规则判别胜负，胜加对应分，负扣对应分，其他人分数不变，则你的选择是
 * A.剪刀（2）B.石头（10）C.布（5）
 * 6-8人
 */
@Service
public class Q013RockPaperAndScissorStrategy extends BaseQuestionStrategy {
    public Q013RockPaperAndScissorStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Long> count = submissions.values().stream()
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));

        long max = Collections.max(count.values());
        long min = Collections.min(count.values());
        if (count.values().stream().filter(v -> v == max).count() != 1
                || count.values().stream().filter(v -> v == min).count() != 1)
            return submissions.keySet().stream().collect(Collectors.toMap(k -> k, k -> 0));

        String maxChoice = count.entrySet().stream().filter(e -> e.getValue() == max).findFirst().get().getKey();
        String minChoice = count.entrySet().stream().filter(e -> e.getValue() == min).findFirst().get().getKey();

        // 剪刀A 胜 布C；石头B 胜 剪刀A；布C 胜 石头B
        Map<String, Integer> scoreMap = Map.of("A", 2, "B", 10, "C", 5);
        Map<String, String> beats = Map.of("A", "C", "B", "A", "C", "B");

        int win = beats.get(maxChoice).equals(minChoice) ? 1 : -1;

        return submissions.entrySet().stream()
                .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().equals(maxChoice) ? (win > 0 ? scoreMap.get(e.getValue()) : -scoreMap.get(e.getValue()))
                        : e.getValue().equals(minChoice) ? (win > 0 ? -scoreMap.get(e.getValue()) : scoreMap.get(e.getValue()))
                        : 0
        ));
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q013";
    }
}

