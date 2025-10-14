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

import static org.example.config.WebSocketConfig.WebSocketChannelInterceptor.log;

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
        pool.validateSequences();
        return pool;
    }

    private List<QuestionEntity> selectFromPool(QuestionPool pool, int totalCount) {
        List<QuestionEntity> selected = new ArrayList<>();
        Random random = new Random();

        while (selected.size() < totalCount) {
            int remaining = totalCount - selected.size();

            // 🔥 构建可用选项列表
            List<PoolOption> availableOptions = new ArrayList<>();

            // 1. 检查序列题
            if (pool.hasAvailableSequence(remaining)) {
                availableOptions.add(PoolOption.SEQUENCE);
            }

            // 2. 检查重复题（🔥 检查是否有完整空间容纳所有轮次）
            if (pool.hasAvailableRepeatable(remaining)) {
                availableOptions.add(PoolOption.REPEATABLE);
            }

            // 3. 普通题始终可选（如果有）
            if (pool.hasNormalQuestions()) {
                availableOptions.add(PoolOption.NORMAL);
            }

            // 没有可用选项，结束
            if (availableOptions.isEmpty()) {
                QuestionSelectorService.log.warn("题目不足，实际选择了 {} 题，期望 {} 题", selected.size(), totalCount);
                break;
            }

            // 完全随机选择一个类型
            PoolOption selectedOption = availableOptions.get(random.nextInt(availableOptions.size()));

            switch (selectedOption) {
                case SEQUENCE:
                    List<QuestionEntity> sequence = pool.getRandomSequence(remaining);
                    if (sequence != null) {
                        selected.addAll(sequence);
                    }
                    break;

                case REPEATABLE:
                    // 🔥 一次性获取所有轮次
                    List<QuestionEntity> repeatableRounds = pool.getRandomRepeatableAllRounds(remaining);
                    if (repeatableRounds != null) {
                        selected.addAll(repeatableRounds);
                    }
                    break;

                case NORMAL:
                    QuestionEntity normal = pool.getRandomNormal();
                    if (normal != null) {
                        selected.add(normal);
                    }
                    break;
            }
        }

        return selected;
    }

    private enum PoolOption {
        SEQUENCE,
        REPEATABLE,
        NORMAL
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
                .anyMatch(info -> info.getMaxCount() <= remainingSlots);
    }

    // 🔥 新增方法：一次性返回重复题的所有轮次
    public List<QuestionEntity> getRandomRepeatableAllRounds(int remainingSlots) {
        List<RepeatableQuestionInfo> available = repeatableQuestions.values().stream()
                .filter(info -> info.getMaxCount() <= remainingSlots)  // 🔥 检查总轮次是否能放下
                .toList();

        if (available.isEmpty()) return null;

        RepeatableQuestionInfo selected = available.get(new Random().nextInt(available.size()));

        // 🔥 生成 N 个相同的题目（N = maxCount）
        List<QuestionEntity> rounds = new ArrayList<>();
        for (int i = 0; i < selected.getMaxCount(); i++) {
            rounds.add(selected.getQuestion());
        }

        // 🔥 用完后从池子里移除
        repeatableQuestions.remove(selected.getQuestion().getId());

        log.info("选中重复题: {} (重复{}次)", selected.getQuestion().getStrategyId(), selected.getMaxCount());

        return rounds;
    }

    public boolean hasNormalQuestions() {
        return !normalQuestions.isEmpty();
    }

    public QuestionEntity getRandomNormal() {
        if (normalQuestions.isEmpty()) return null;
        return normalQuestions.remove(new Random().nextInt(normalQuestions.size()));
    }

    /**
     * 验证序列题是否完整
     * 如果某个序列缺题，移除该序列并将题目放入普通池
     */
    public void validateSequences() {
        Iterator<Map.Entry<String, SequenceInfo>> iterator = sequenceGroups.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, SequenceInfo> entry = iterator.next();
            String groupId = entry.getKey();
            SequenceInfo info = entry.getValue();

            List<QuestionMetadataPair> pairs = info.getQuestions();
            if (pairs.isEmpty()) {
                iterator.remove();
                continue;
            }

            // 获取期望的总数
            Integer expectedTotal = pairs.get(0).getMetadata().getTotalSequenceCount();
            if (expectedTotal == null) {
                log.warn("⚠️ 序列 {} 缺少 totalSequenceCount，移入普通池", groupId);
                pairs.forEach(pair -> normalQuestions.add(pair.getQuestion()));
                iterator.remove();
                continue;
            }

            // 检查数量是否匹配
            if (pairs.size() != expectedTotal) {
                log.warn("⚠️ 序列 {} 不完整：期望{}题，实际{}题，移入普通池",
                        groupId, expectedTotal, pairs.size());
                pairs.forEach(pair -> normalQuestions.add(pair.getQuestion()));
                iterator.remove();
                continue;
            }

            // 检查 sequenceOrder 是否连续
            List<Integer> orders = pairs.stream()
                    .map(pair -> pair.getMetadata().getSequenceOrder())
                    .sorted()
                    .toList();

            boolean isConsecutive = true;
            for (int i = 0; i < orders.size(); i++) {
                if (orders.get(i) != i + 1) {  // 假设 sequenceOrder 从 1 开始
                    isConsecutive = false;
                    break;
                }
            }

            if (!isConsecutive) {
                log.warn("⚠️ 序列 {} 的 sequenceOrder 不连续：{}，移入普通池", groupId, orders);
                pairs.forEach(pair -> normalQuestions.add(pair.getQuestion()));
                iterator.remove();
                continue;
            }

            log.info("✅ 序列 {} 验证通过：{}题，顺序{}", groupId, expectedTotal, orders);
        }
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