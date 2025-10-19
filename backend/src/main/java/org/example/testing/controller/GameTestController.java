package org.example.testing.controller;

import org.example.service.QuestionFactory;
import org.example.service.QuestionScoringStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/test")
public class GameTestController {
    @Autowired
    private QuestionFactory questionFactory;

    /**
     * 单次计算接口
     * POST /api/test/calculate
     * Body: {
     *   "strategyId": "Q001",
     *   "submissions": {
     *     "player1": "3",
     *     "player2": "5"
     *   }
     * }
     * Response: {
     *   "scores": {
     *     "player1": -3,
     *     "player2": -5
     *   }
     * }
     */
    @PostMapping("/calculate")
    public Map<String, Object> calculate(@RequestBody Map<String, Object> request) {
        String strategyId = (String) request.get("strategyId");
        Map<String, String> submissions = (Map<String, String>) request.get("submissions");

        QuestionScoringStrategy strategy = questionFactory.getStrategy(strategyId);
        Map<String, Integer> scores = strategy.test(submissions);

        return Map.of("scores", scores);
    }

    /**
     * 批量计算接口（性能优化用）
     * POST /api/test/batch-calculate
     * Body: {
     *   "strategyId": "Q001",
     *   "submissionsList": [
     *     {"player1": "1", "player2": "1"},
     *     {"player1": "1", "player2": "2"},
     *     ...
     *   ]
     * }
     */
    @PostMapping("/batch-calculate")
    public Map<String, Object> batchCalculate(@RequestBody Map<String, Object> request) {
        String strategyId = (String) request.get("strategyId");
        List<Map<String, String>> submissionsList =
                (List<Map<String, String>>) request.get("submissionsList");

        QuestionScoringStrategy strategy = questionFactory.getStrategy(strategyId);

        List<Map<String, Integer>> results = submissionsList.stream()
                .map(strategy::test)
                .toList();

        return Map.of("results", results);
    }

}
