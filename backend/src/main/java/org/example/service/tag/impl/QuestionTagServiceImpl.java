package org.example.service.tag.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.TagDTO;
import org.example.entity.QuestionTagEntity;
import org.example.entity.QuestionTagRelationEntity;
import org.example.repository.QuestionTagRelationRepository;
import org.example.repository.QuestionTagRepository;
import org.example.service.tag.QuestionTagService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目标签服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionTagServiceImpl implements QuestionTagService {

    private final QuestionTagRepository tagRepository;
    private final QuestionTagRelationRepository relationRepository;
    private final ObjectMapper objectMapper;

    /**
     * 应用启动时自动初始化标签数据
     */
    @PostConstruct
    @Transactional
    public void initializeTagsFromJson() {
        // 如果数据库已有数据，跳过初始化
        if (tagRepository.count() > 0) {
            log.info("✅ 标签数据已存在，跳过初始化");
            return;
        }

        log.info("📝 开始从 question-tags.json 初始化标签数据");

        try {
            ClassPathResource resource = new ClassPathResource("question-tags.json");
            Map<String, Object> data = objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<Map<String, Object>>() {}
            );

            // 1. 初始化标签
            List<Map<String, Object>> tagList = (List<Map<String, Object>>) data.get("tags");
            List<QuestionTagEntity> tags = tagList.stream()
                    .map(tagData -> QuestionTagEntity.builder()
                            .id(((Number) tagData.get("id")).longValue())
                            .name((String) tagData.get("name"))
                            .category((String) tagData.get("category"))
                            .color((String) tagData.get("color"))
                            .build())
                    .toList();
            tagRepository.saveAll(tags);
            log.info("✅ 保存了 {} 个标签", tags.size());

            // 2. 初始化题目-标签关联
            List<Map<String, Object>> mappings = (List<Map<String, Object>>) data.get("questionTagMappings");
            List<QuestionTagRelationEntity> relations = new ArrayList<>();

            for (Map<String, Object> mapping : mappings) {
                Long questionId = ((Number) mapping.get("questionId")).longValue();
                List<Number> tagIds = (List<Number>) mapping.get("tagIds");

                for (Number tagId : tagIds) {
                    relations.add(QuestionTagRelationEntity.builder()
                            .questionId(questionId)
                            .tagId(tagId.longValue())
                            .build());
                }
            }

            relationRepository.saveAll(relations);
            log.info("✅ 保存了 {} 个题目-标签关联", relations.size());

        } catch (IOException e) {
            log.error("❌ 初始化标签数据失败", e);
            throw new RuntimeException("初始化标签数据失败", e);
        }
    }

    @Override
    public List<TagDTO> getAllTags() {
        return tagRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, List<TagDTO>> getTagsByCategory() {
        return tagRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.groupingBy(TagDTO::getCategory));
    }

    @Override
    public List<TagDTO> getTagsByQuestionId(Long questionId) {
        List<Long> tagIds = relationRepository.findTagIdsByQuestionId(questionId);
        if (tagIds.isEmpty()) {
            return Collections.emptyList();
        }

        return tagRepository.findAllById(tagIds).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Map<Long, List<TagDTO>> getTagsForQuestions(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. 批量查询关联关系
        List<QuestionTagRelationEntity> relations = relationRepository.findByQuestionIdIn(questionIds);

        // 2. 提取所有tagId并批量查询标签
        Set<Long> tagIds = relations.stream()
                .map(QuestionTagRelationEntity::getTagId)
                .collect(Collectors.toSet());

        Map<Long, TagDTO> tagMap = tagRepository.findAllById(tagIds).stream()
                .collect(Collectors.toMap(
                        QuestionTagEntity::getId,
                        this::toDTO
                ));

        // 3. 构建 questionId -> List<TagDTO> 的映射
        Map<Long, List<TagDTO>> result = new HashMap<>();
        for (QuestionTagRelationEntity relation : relations) {
            TagDTO tag = tagMap.get(relation.getTagId());
            if (tag != null) {
                result.computeIfAbsent(relation.getQuestionId(), k -> new ArrayList<>())
                        .add(tag);
            }
        }

        return result;
    }

    // ==================== 私有方法 ====================

    private TagDTO toDTO(QuestionTagEntity entity) {
        return TagDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .category(entity.getCategory())
                .color(entity.getColor())
                .build();
    }
}
