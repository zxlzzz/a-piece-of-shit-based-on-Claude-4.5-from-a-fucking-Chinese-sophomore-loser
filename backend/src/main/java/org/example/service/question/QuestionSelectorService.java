package org.example.service.question;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.QuestionDTO;
import org.example.entity.QuestionEntity;
import org.example.entity.QuestionMetadata;
import org.example.pojo.GameMode;
import org.example.repository.QuestionMetadataRepository;
import org.example.repository.QuestionRepository;
import org.example.repository.QuestionTagRelationRepository;
import org.example.repository.SyncOnlyQuestionRepository;
import org.example.utils.DTOConverter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.example.config.WebSocketConfig.WebSocketChannelInterceptor.log;

@Service
@Slf4j
public class QuestionSelectorService {
    private final QuestionRepository questionRepository;
    private final QuestionMetadataRepository metadataRepository;
    private final QuestionTagRelationRepository tagRelationRepository;
    private final SyncOnlyQuestionRepository syncOnlyQuestionRepository;
    private final DTOConverter dtoConverter;

    public QuestionSelectorService(
            QuestionRepository questionRepository,
            QuestionMetadataRepository metadataRepository,
            QuestionTagRelationRepository tagRelationRepository,
            SyncOnlyQuestionRepository syncOnlyQuestionRepository,
            DTOConverter dtoConverter) {
        this.questionRepository = questionRepository;
        this.metadataRepository = metadataRepository;
        this.tagRelationRepository = tagRelationRepository;
        this.syncOnlyQuestionRepository = syncOnlyQuestionRepository;
        this.dtoConverter = dtoConverter;
    }

    /**
     * 选择题目（返回 DTO）
     */
    public List<QuestionDTO> selectQuestions(int totalCount, int playerCount) {
        return selectQuestions(totalCount, playerCount, null, GameMode.SYNCHRONIZED);
    }

    /**
     * 选择题目（支持标签筛选）
     */
    public List<QuestionDTO> selectQuestions(int totalCount, int playerCount, List<Long> tagIds) {
        return selectQuestions(totalCount, playerCount, tagIds, GameMode.SYNCHRONIZED);
    }

    /**
     * 选择题目（支持标签筛选 + 游戏模式）
     * ASYNC 模式下会自动排除 SyncOnlyQuestionPool 中的题目。
     *
     * @param totalCount  需要的题目总数
     * @param playerCount 玩家人数
     * @param tagIds      标签ID列表（可选，null 表示不筛选）
     * @param gameMode    游戏模式
     * @return 选中的题目列表
     */
    public List<QuestionDTO> selectQuestions(int totalCount, int playerCount, List<Long> tagIds, GameMode gameMode) {
        // 1. 查询所有题目（带配置）
        List<QuestionEntity> allQuestions = questionRepository.findAllWithConfigs();

        // 2. 如果指定了标签，先根据标签筛选
        if (tagIds != null && !tagIds.isEmpty()) {
            Set<Long> filteredQuestionIds = filterQuestionIdsByTags(tagIds);
            allQuestions = allQuestions.stream()
                    .filter(q -> filteredQuestionIds.contains(q.getId()))
                    .toList();

            log.info("🏷️ 根据标签筛选后：{} 道题目", allQuestions.size());
        }

        // 3. 筛选适合人数的题目
        List<QuestionEntity> suitable = allQuestions.stream()
                .filter(q -> q.getMinPlayers() <= playerCount && q.getMaxPlayers() >= playerCount)
                .toList();

        if (suitable.isEmpty()) {
            throw new RuntimeException("No suitable questions found");
        }

        // 4. 加载需要同步模式的题目ID集合
        Set<Long> syncOnlyIds = syncOnlyQuestionRepository.findAllQuestionIds();
        if (!syncOnlyIds.isEmpty()) {
            log.info("🔒 同步专用题目数量: {}", syncOnlyIds.size());
        }

        // 5. ASYNC 模式：排除 syncOnly 题目
        if (gameMode == GameMode.ASYNC && !syncOnlyIds.isEmpty()) {
            int before = suitable.size();
            suitable = suitable.stream()
                    .filter(q -> !syncOnlyIds.contains(q.getId()))
                    .toList();
            log.info("⚡ ASYNC 模式：排除 {} 道同步专用题目，剩余 {} 道", before - suitable.size(), suitable.size());
        }

        if (suitable.isEmpty()) {
            throw new RuntimeException("No suitable questions found after applying game mode filter");
        }

        // 6. 批量查询所有题目的 metadata
        List<Long> questionIds = suitable.stream()
                .map(QuestionEntity::getId)
                .toList();

        Map<Long, QuestionMetadata> metadataMap = metadataRepository
                .findByQuestionIdIn(questionIds)
                .stream()
                .collect(Collectors.toMap(QuestionMetadata::getQuestionId, m -> m));

        // 7. 构建题目池（双池：asyncPool + syncOnlyPool）
        // ASYNC 模式下 syncOnly 题已在步骤5被过滤掉，syncOnlyPool 自然为空
        QuestionPool pool = buildQuestionPool(suitable, metadataMap, syncOnlyIds);

        // 8. 从池中选择题目
        List<QuestionEntity> selectedEntities = selectFromPool(pool, totalCount);

        // 9. 转换成 DTO（带配置）
        List<QuestionDTO> selectedDTOs = selectedEntities.stream()
                .map(dtoConverter::toQuestionDTOWithConfig)
                .collect(Collectors.toList());

        log.info("✅ 选题完成: 共选择 {} 道题目（玩家数: {}, 模式: {}）", selectedDTOs.size(), playerCount, gameMode);

        return selectedDTOs;
    }

    /**
     * 根据标签筛选题目ID
     */
    private Set<Long> filterQuestionIdsByTags(List<Long> tagIds) {
        // 查询所有包含这些标签的题目ID
        return tagRelationRepository.findByQuestionIdIn(
                        questionRepository.findAllWithConfigs().stream()
                                .map(QuestionEntity::getId)
                                .toList()
                ).stream()
                .filter(relation -> tagIds.contains(relation.getTagId()))
                .map(org.example.entity.QuestionTagRelationEntity::getQuestionId)
                .collect(Collectors.toSet());
    }

    private QuestionPool buildQuestionPool(
            List<QuestionEntity> questions,
            Map<Long, QuestionMetadata> metadataMap,
            Set<Long> syncOnlyIds) {

        QuestionPool pool = new QuestionPool();

        for (QuestionEntity q : questions) {
            QuestionMetadata metadata = metadataMap.get(q.getId());

            boolean isSyncOnly = syncOnlyIds.contains(q.getId());

            // 检查是否是序列题
            if (metadata != null && metadata.getSequenceGroupId() != null) {
                pool.addSequence(q, metadata, isSyncOnly);
            }
            // 检查是否是重复题
            else if (metadata != null && Boolean.TRUE.equals(metadata.getIsRepeatable())) {
                pool.addRepeatable(q, metadata, isSyncOnly);
            }
            // 普通题
            else {
                pool.addNormal(q, isSyncOnly);
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

            List<PoolOption> availableOptions = new ArrayList<>();

            // asyncPool
            if (pool.hasAvailableSequence(remaining))    availableOptions.add(PoolOption.SEQUENCE);
            if (pool.hasAvailableRepeatable(remaining))  availableOptions.add(PoolOption.REPEATABLE);
            if (pool.hasNormalQuestions())                availableOptions.add(PoolOption.NORMAL);

            // syncOnlyPool（SYNCHRONIZED 模式下构建时题目已进入此池）
            if (pool.hasSyncOnlyAvailableSequence(remaining))   availableOptions.add(PoolOption.SYNC_ONLY_SEQUENCE);
            if (pool.hasSyncOnlyAvailableRepeatable(remaining)) availableOptions.add(PoolOption.SYNC_ONLY_REPEATABLE);
            if (pool.hasSyncOnlyNormalQuestions())               availableOptions.add(PoolOption.SYNC_ONLY_NORMAL);

            if (availableOptions.isEmpty()) {
                QuestionSelectorService.log.warn("题目不足，实际选择了 {} 题，期望 {} 题", selected.size(), totalCount);
                break;
            }

            PoolOption selectedOption = availableOptions.get(random.nextInt(availableOptions.size()));

            switch (selectedOption) {
                case SEQUENCE -> {
                    List<QuestionEntity> seq = pool.getRandomSequence(remaining);
                    if (seq != null) selected.addAll(seq);
                }
                case REPEATABLE -> {
                    List<QuestionEntity> rounds = pool.getRandomRepeatableAllRounds(remaining);
                    if (rounds != null) selected.addAll(rounds);
                }
                case NORMAL -> {
                    QuestionEntity normal = pool.getRandomNormal();
                    if (normal != null) selected.add(normal);
                }
                case SYNC_ONLY_SEQUENCE -> {
                    List<QuestionEntity> seq = pool.getSyncOnlyRandomSequence(remaining);
                    if (seq != null) selected.addAll(seq);
                }
                case SYNC_ONLY_REPEATABLE -> {
                    List<QuestionEntity> rounds = pool.getSyncOnlyRandomRepeatableAllRounds(remaining);
                    if (rounds != null) selected.addAll(rounds);
                }
                case SYNC_ONLY_NORMAL -> {
                    QuestionEntity normal = pool.getSyncOnlyRandomNormal();
                    if (normal != null) selected.add(normal);
                }
            }
        }

        return selected;
    }

    private enum PoolOption {
        SEQUENCE, REPEATABLE, NORMAL,
        SYNC_ONLY_SEQUENCE, SYNC_ONLY_REPEATABLE, SYNC_ONLY_NORMAL
    }

}
/**
 * 题目池：维护两个逻辑子池
 * - asyncPool:    异步/同步模式均可使用的题目
 * - syncOnlyPool: 仅同步模式可使用的题目（当前为空，未来有需要时向 sync_only_questions 表插入数据）
 *
 * selectFromPool 时：ASYNC 模式只从 asyncPool 抽取；SYNCHRONIZED 模式两个池均可。
 */
@Data
class QuestionPool {

    // ===== asyncPool =====
    private Map<String, SequenceInfo> sequenceGroups = new HashMap<>();
    private Map<Long, RepeatableQuestionInfo> repeatableQuestions = new HashMap<>();
    private List<QuestionEntity> normalQuestions = new ArrayList<>();

    // ===== syncOnlyPool =====
    private Map<String, SequenceInfo> syncOnlySequenceGroups = new HashMap<>();
    private Map<Long, RepeatableQuestionInfo> syncOnlyRepeatableQuestions = new HashMap<>();
    private List<QuestionEntity> syncOnlyNormalQuestions = new ArrayList<>();

    public void addSequence(QuestionEntity question, QuestionMetadata metadata, boolean syncOnly) {
        String groupId = metadata.getSequenceGroupId();
        if (syncOnly) {
            syncOnlySequenceGroups.computeIfAbsent(groupId, k -> new SequenceInfo())
                    .addQuestion(question, metadata);
        } else {
            sequenceGroups.computeIfAbsent(groupId, k -> new SequenceInfo())
                    .addQuestion(question, metadata);
        }
    }

    public void addRepeatable(QuestionEntity question, QuestionMetadata metadata, boolean syncOnly) {
        if (syncOnly) {
            syncOnlyRepeatableQuestions.put(question.getId(),
                    new RepeatableQuestionInfo(question, metadata.getRepeatTimes(), 0));
        } else {
            repeatableQuestions.put(question.getId(),
                    new RepeatableQuestionInfo(question, metadata.getRepeatTimes(), 0));
        }
    }

    public void addNormal(QuestionEntity question, boolean syncOnly) {
        if (syncOnly) {
            syncOnlyNormalQuestions.add(question);
        } else {
            normalQuestions.add(question);
        }
    }

    public boolean hasAvailableSequence(int remainingSlots) {
        return sequenceGroups.values().stream()
                .anyMatch(seq -> seq.getQuestions().size() <= remainingSlots);
    }

    public List<QuestionEntity> getRandomSequence(int remainingSlots) {
        List<String> availableGroups = sequenceGroups.entrySet().stream()
                .filter(e -> e.getValue().getQuestions().size() <= remainingSlots)
                .map(Map.Entry::getKey)
                .toList();

        if (availableGroups.isEmpty()) return null;

        String selectedGroup = availableGroups.get(new Random().nextInt(availableGroups.size()));
        SequenceInfo sequenceInfo = sequenceGroups.remove(selectedGroup);

        return sequenceInfo.getQuestions().stream()
                .sorted(Comparator.comparing(pair -> pair.getMetadata().getSequenceOrder()))
                .map(QuestionMetadataPair::getQuestion)
                .toList();
    }

    public boolean hasAvailableRepeatable(int remainingSlots) {
        return repeatableQuestions.values().stream()
                .anyMatch(info -> info.getMaxCount() <= remainingSlots);
    }

    public List<QuestionEntity> getRandomRepeatableAllRounds(int remainingSlots) {
        List<RepeatableQuestionInfo> available = repeatableQuestions.values().stream()
                .filter(info -> info.getMaxCount() <= remainingSlots)
                .toList();

        if (available.isEmpty()) return null;

        RepeatableQuestionInfo selected = available.get(new Random().nextInt(available.size()));

        List<QuestionEntity> rounds = new ArrayList<>();
        for (int i = 0; i < selected.getMaxCount(); i++) {
            rounds.add(selected.getQuestion());
        }

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

    // ===== syncOnlyPool 对应方法 =====

    public boolean hasSyncOnlyAvailableSequence(int remainingSlots) {
        return syncOnlySequenceGroups.values().stream()
                .anyMatch(seq -> seq.getQuestions().size() <= remainingSlots);
    }

    public List<QuestionEntity> getSyncOnlyRandomSequence(int remainingSlots) {
        List<String> available = syncOnlySequenceGroups.entrySet().stream()
                .filter(e -> e.getValue().getQuestions().size() <= remainingSlots)
                .map(Map.Entry::getKey)
                .toList();
        if (available.isEmpty()) return null;
        String selected = available.get(new Random().nextInt(available.size()));
        SequenceInfo info = syncOnlySequenceGroups.remove(selected);
        return info.getQuestions().stream()
                .sorted(Comparator.comparing(pair -> pair.getMetadata().getSequenceOrder()))
                .map(QuestionMetadataPair::getQuestion)
                .toList();
    }

    public boolean hasSyncOnlyAvailableRepeatable(int remainingSlots) {
        return syncOnlyRepeatableQuestions.values().stream()
                .anyMatch(info -> info.getMaxCount() <= remainingSlots);
    }

    public List<QuestionEntity> getSyncOnlyRandomRepeatableAllRounds(int remainingSlots) {
        List<RepeatableQuestionInfo> available = syncOnlyRepeatableQuestions.values().stream()
                .filter(info -> info.getMaxCount() <= remainingSlots)
                .toList();
        if (available.isEmpty()) return null;
        RepeatableQuestionInfo selected = available.get(new Random().nextInt(available.size()));
        List<QuestionEntity> rounds = new ArrayList<>();
        for (int i = 0; i < selected.getMaxCount(); i++) {
            rounds.add(selected.getQuestion());
        }
        syncOnlyRepeatableQuestions.remove(selected.getQuestion().getId());
        return rounds;
    }

    public boolean hasSyncOnlyNormalQuestions() {
        return !syncOnlyNormalQuestions.isEmpty();
    }

    public QuestionEntity getSyncOnlyRandomNormal() {
        if (syncOnlyNormalQuestions.isEmpty()) return null;
        return syncOnlyNormalQuestions.remove(new Random().nextInt(syncOnlyNormalQuestions.size()));
    }

    /**
     * 验证序列题是否完整（asyncPool + syncOnlyPool 均验证）
     */
    public void validateSequences() {
        validateSequenceMap(sequenceGroups, normalQuestions, "asyncPool");
        validateSequenceMap(syncOnlySequenceGroups, syncOnlyNormalQuestions, "syncOnlyPool");
    }

    private void validateSequenceMap(Map<String, SequenceInfo> groups,
                                      List<QuestionEntity> fallbackList,
                                      String poolName) {
        Iterator<Map.Entry<String, SequenceInfo>> iterator = groups.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SequenceInfo> entry = iterator.next();
            String groupId = entry.getKey();
            SequenceInfo info = entry.getValue();

            List<QuestionMetadataPair> pairs = info.getQuestions();
            if (pairs.isEmpty()) { iterator.remove(); continue; }

            Integer expectedTotal = pairs.get(0).getMetadata().getTotalSequenceCount();
            if (expectedTotal == null) {
                log.warn("⚠️ [{}] 序列 {} 缺少 totalSequenceCount，移入普通池", poolName, groupId);
                pairs.forEach(pair -> fallbackList.add(pair.getQuestion()));
                iterator.remove();
                continue;
            }

            if (pairs.size() != expectedTotal) {
                log.warn("⚠️ [{}] 序列 {} 不完整：期望{}题，实际{}题，移入普通池",
                        poolName, groupId, expectedTotal, pairs.size());
                pairs.forEach(pair -> fallbackList.add(pair.getQuestion()));
                iterator.remove();
                continue;
            }

            List<Integer> orders = pairs.stream()
                    .map(pair -> pair.getMetadata().getSequenceOrder())
                    .sorted()
                    .toList();

            boolean isConsecutive = true;
            for (int i = 0; i < orders.size(); i++) {
                if (orders.get(i) != i + 1) { isConsecutive = false; break; }
            }

            if (!isConsecutive) {
                log.warn("⚠️ [{}] 序列 {} 的 sequenceOrder 不连续：{}，移入普通池", poolName, groupId, orders);
                pairs.forEach(pair -> fallbackList.add(pair.getQuestion()));
                iterator.remove();
                continue;
            }

            log.info("✅ [{}] 序列 {} 验证通过：{}题，顺序{}", poolName, groupId, expectedTotal, orders);
        }
    }
}
// 在文件末尾添加这两个类

@Getter
@Data
class SequenceInfo {
    private List<QuestionMetadataPair> questions = new ArrayList<>();

    public void addQuestion(QuestionEntity question, QuestionMetadata metadata) {
        questions.add(new QuestionMetadataPair(question, metadata));
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