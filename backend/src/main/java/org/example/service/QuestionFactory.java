package org.example.service;

import jakarta.annotation.PostConstruct;
import org.example.entity.QuestionEntity;
import org.example.exception.BusinessException;
import org.example.pojo.GameContext;
import org.example.pojo.QuestionResult;
import org.example.service.QuestionScoringStrategyImpl.*;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QuestionFactory {
    private static final Map<String, QuestionScoringStrategy> STRATEGIES = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 注册所有策略
        registerStrategy(new Q001NumberGroupStrategy());
        registerStrategy(new normalChoiceStrategy());
        registerStrategy(new Q002PerformanceCostumeStrategy());
        registerStrategy(new Q003FarmingStrategy());
        registerStrategy(new Q004SheepAuctionStrategy());
        registerStrategy(new Q005InsuranceClaimStrategy());
        registerStrategy(new Q006RoadBuildingStrategy());
        registerStrategy(new Q007DoubleAuctionStrategy());
        registerStrategy(new Q008ShopLocationStrategy());
        // ... 更多策略
    }

    public void registerStrategy(QuestionScoringStrategy strategy) {
        STRATEGIES.put(strategy.getQuestionIdentifier(), strategy);
    }

    public QuestionResult calculateScores(GameContext context) {
        QuestionEntity question = context.getCurrentQuestion();
        String identifier = question.getStrategyId(); // 题目中指定策略ID

        QuestionScoringStrategy strategy = STRATEGIES.get(identifier);
        if (strategy == null) {
            throw new BusinessException("未找到计分策略: " + identifier);
        }

        return strategy.calculateResult(context);
    }
}
