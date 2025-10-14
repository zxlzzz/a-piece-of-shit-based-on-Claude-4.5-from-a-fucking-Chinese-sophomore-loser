package org.example.service;

import jakarta.annotation.PostConstruct;
import org.example.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.example.config.WebSocketConfig.WebSocketChannelInterceptor.log;

@Component
public class QuestionFactory {
    @Autowired
    private List<QuestionScoringStrategy> allStrategies;

    private final Map<String, QuestionScoringStrategy> STRATEGIES = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        allStrategies.forEach(strategy ->
                STRATEGIES.put(strategy.getQuestionIdentifier(), strategy)
        );

        log.info("✅ 已自动注册 {} 个题目策略: {}",
                STRATEGIES.size(),
                String.join(", ", STRATEGIES.keySet()));
    }

    public QuestionScoringStrategy getStrategy(String strategyId) {
        QuestionScoringStrategy strategy = STRATEGIES.get(strategyId);
        if (strategy == null) {
            throw new BusinessException("未找到计分策略: " + strategyId);
        }
        return strategy;
    }
}
