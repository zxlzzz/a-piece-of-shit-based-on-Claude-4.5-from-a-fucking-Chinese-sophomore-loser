package org.example.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.QuestionEntity;
import org.example.entity.QuestionMetadata;
import org.example.repository.QuestionMetadataRepository;
import org.example.repository.QuestionRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class QuestionSelectorService {
    private final QuestionRepository questionRepository;
    private final QuestionMetadataRepository metadataRepository;

    public QuestionSelectorService(QuestionRepository questionRepository, QuestionMetadataRepository metadataRepository) {
        this.questionRepository = questionRepository;
        this.metadataRepository = metadataRepository;
    }

    public List<QuestionEntity> selectQuestions(int totalCount, int playerCount){
        List<QuestionEntity> allQuestions = questionRepository.findAll();
        List<QuestionEntity> suitable = allQuestions.stream()
                .filter(q -> q.getMinPlayers() <= playerCount && q.getMaxPlayers() >= playerCount)
                .toList();

        if(suitable.isEmpty()){
            throw new RuntimeException("No suitable questions found");
        }

        // ← 新增：批量查询所有题目的 metadata（避免 N+1 问题）
        List<Long> questionIds = suitable.stream().map(QuestionEntity::getId).toList();
        Map<Long, QuestionMetadata> metadataMap = metadataRepository
                .findByQuestionIdIn(questionIds)
                .stream()
                .collect(Collectors.toMap(QuestionMetadata::getQuestionId, m -> m));

        QuestionPool pool = buildQuestionPool(suitable, metadataMap);  // ← 传入 metadataMap
        return selectFromPool(pool, totalCount);
    }

    private QuestionPool buildQuestionPool(
            List<QuestionEntity> questions,
            Map<Long, QuestionMetadata> metadataMap) {  // ← 新增参数

        QuestionPool pool = new QuestionPool();

        for(QuestionEntity q: questions){
            QuestionMetadata metadata = metadataMap.get(q.getId());  // ← 从 map 中获取

            // 检查是否是序列题
            if(metadata != null && metadata.getSequenceGroupId() != null){
                pool.addSequence(q, metadata);  // ← 传入 metadata
            }
            // 检查是否是重复题
            else if (metadata != null && Boolean.TRUE.equals(metadata.getIsRepeatable())){
                pool.addRepeatable(q, metadata);  // ← 传入 metadata
            }
            // 普通题
            else {
                pool.addNormal(q);
            }
        }
        return pool;
    }

    private List<QuestionEntity> selectFromPool(QuestionPool pool, int totalCount) {
        List<QuestionEntity> selected = new ArrayList<>();
        Random random = new Random();

        while (selected.size() < totalCount) {
            int remaining = totalCount - selected.size();

            // 1. 优先处理序列题目
            if (pool.hasAvailableSequence(remaining)) {
                List<QuestionEntity> sequence = pool.getRandomSequence(remaining);
                if (sequence != null) {
                    selected.addAll(sequence);
                    continue;
                }
            }

            // 2. 处理可重复题目
            if (pool.hasAvailableRepeatable(remaining)) {
                QuestionEntity repeatable = pool.getRandomRepeatable(remaining);
                if (repeatable != null) {
                    selected.add(repeatable);
                    continue;
                }
            }

            // 3. 选择普通题目
            if (pool.hasNormalQuestions()) {
                QuestionEntity normal = pool.getRandomNormal();
                selected.add(normal);
            } else {
                // 没有更多题目了
                log.warn("题目不足，实际选择了 {} 题，期望 {} 题", selected.size(), totalCount);
                break;
            }
        }

        return selected;
    }
}
@Data
class QuestionPool {
    // 序列题目：sequenceGroupId -> SequenceInfo
    private Map<String, SequenceInfo> sequenceGroups = new HashMap<>();  // ← 改类型

    // 可重复题目：questionId -> RepeatableQuestionInfo
    private Map<Long, RepeatableQuestionInfo> repeatableQuestions = new HashMap<>();

    // 普通题目
    private List<QuestionEntity> normalQuestions = new ArrayList<>();

    // ← 修改：需要传入 metadata
    public void addSequence(QuestionEntity question, QuestionMetadata metadata) {
        String groupId = metadata.getSequenceGroupId();
        sequenceGroups.computeIfAbsent(groupId, k -> new SequenceInfo())
                .addQuestion(question, metadata);
    }

    // ← 修改：需要传入 metadata
    public void addRepeatable(QuestionEntity question, QuestionMetadata metadata) {
        repeatableQuestions.put(question.getId(),
                new RepeatableQuestionInfo(question, metadata.getRepeatTimes(), 0));
    }

    public void addNormal(QuestionEntity question) {
        normalQuestions.add(question);
    }

    public boolean hasAvailableSequence(int remainingSlots) {
        return sequenceGroups.values().stream()
                .anyMatch(seq -> seq.getQuestions().size() <= remainingSlots);  // ← 改方法
    }

    public List<QuestionEntity> getRandomSequence(int remainingSlots) {
        List<String> availableGroups = sequenceGroups.entrySet().stream()
                .filter(e -> e.getValue().getQuestions().size() <= remainingSlots)  // ← 改方法
                .map(Map.Entry::getKey)
                .toList();

        if (availableGroups.isEmpty()) return null;

        String selectedGroup = availableGroups.get(new Random().nextInt(availableGroups.size()));
        SequenceInfo sequenceInfo = sequenceGroups.remove(selectedGroup);  // ← 改类型

        // 按 sequenceOrder 排序并返回
        return sequenceInfo.getQuestions().stream()
                .sorted(Comparator.comparing(pair -> pair.getMetadata().getSequenceOrder()))
                .map(QuestionMetadataPair::getQuestion)
                .toList();
    }

    public boolean hasAvailableRepeatable(int remainingSlots) {
        return repeatableQuestions.values().stream()
                .anyMatch(info -> info.getUsedCount() < info.getMaxCount());
    }

    public QuestionEntity getRandomRepeatable(int remainingSlots) {
        List<RepeatableQuestionInfo> available = repeatableQuestions.values().stream()
                .filter(info -> info.getUsedCount() < info.getMaxCount())
                .toList();

        if (available.isEmpty()) return null;

        RepeatableQuestionInfo selected = available.get(new Random().nextInt(available.size()));
        selected.usedCount++;

        if (selected.getUsedCount() >= selected.getMaxCount()) {
            repeatableQuestions.remove(selected.getQuestion().getId());
        }

        return selected.getQuestion();
    }

    public boolean hasNormalQuestions() {
        return !normalQuestions.isEmpty();
    }

    public QuestionEntity getRandomNormal() {
        if (normalQuestions.isEmpty()) return null;
        return normalQuestions.remove(new Random().nextInt(normalQuestions.size()));
    }
}
// 在文件末尾添加这两个类

@Data
class SequenceInfo {
    private List<QuestionMetadataPair> questions = new ArrayList<>();

    public void addQuestion(QuestionEntity question, QuestionMetadata metadata) {
        questions.add(new QuestionMetadataPair(question, metadata));
    }

    public List<QuestionMetadataPair> getQuestions() {
        return questions;
    }
}

@Data
@AllArgsConstructor
class QuestionMetadataPair {
    private QuestionEntity question;
    private QuestionMetadata metadata;
}

// RepeatableQuestionInfo 保持不变
@Data
@AllArgsConstructor
class RepeatableQuestionInfo {
    private QuestionEntity question;
    private int maxCount;
    protected int usedCount;
}