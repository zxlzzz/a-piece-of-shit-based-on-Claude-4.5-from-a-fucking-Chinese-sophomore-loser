package org.example.repository;

import org.example.entity.QuestionDependency;
import org.example.entity.QuestionEntity;

import java.util.List;

public interface QuestionDependencyRepository {
    List<QuestionDependency> findByQuestionIdIn(List<Long> questionIds);

    void deleteByQuestion(QuestionEntity question);
}
