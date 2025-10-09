package org.example.service;

import org.example.pojo.GameContext;
import org.example.pojo.QuestionResult;

public interface QuestionScoringStrategy {
    QuestionResult calculateResult(GameContext context);

    String getQuestionIdentifier();
}
