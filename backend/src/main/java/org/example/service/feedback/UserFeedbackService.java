package org.example.service.feedback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.UserFeedbackDTO;
import org.example.entity.UserFeedbackEntity;
import org.example.repository.UserFeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户反馈服务
 *
 * TODO: 需要添加防恶意提交机制
 *       当前版本为MVP，没有任何防护措施
 *       后续需要添加：
 *       1. IP限流
 *       2. 内容校验（长度限制、敏感词过滤）
 *       3. 全局Rate Limiting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserFeedbackService {

    private final UserFeedbackRepository feedbackRepository;

    /**
     * 提交用户反馈
     *
     * @param feedbackDTO 反馈内容
     * @return 保存的反馈实体
     */
    @Transactional
    public UserFeedbackEntity submitFeedback(UserFeedbackDTO feedbackDTO) {
        // 基础校验
        if (feedbackDTO.getContent() == null || feedbackDTO.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("反馈内容不能为空");
        }

        // 类型校验
        if (feedbackDTO.getType() == null || feedbackDTO.getType().trim().isEmpty()) {
            throw new IllegalArgumentException("请选择反馈类型");
        }

        if (!feedbackDTO.getType().equals("QUESTION_SUGGESTION") &&
            !feedbackDTO.getType().equals("OTHER")) {
            throw new IllegalArgumentException("无效的反馈类型");
        }

        // 内容长度校验
        if (feedbackDTO.getContent().length() > 2000) {
            throw new IllegalArgumentException("反馈内容不能超过2000字");
        }

        // 昵称长度校验
        if (feedbackDTO.getNickname() != null && feedbackDTO.getNickname().length() > 100) {
            throw new IllegalArgumentException("昵称不能超过100字");
        }

        // 联系方式长度校验
        if (feedbackDTO.getContact() != null && feedbackDTO.getContact().length() > 200) {
            throw new IllegalArgumentException("联系方式不能超过200字");
        }

        // 保存反馈
        UserFeedbackEntity feedback = UserFeedbackEntity.builder()
                .type(feedbackDTO.getType())
                .content(feedbackDTO.getContent().trim())
                .nickname(feedbackDTO.getNickname() != null ? feedbackDTO.getNickname().trim() : null)
                .contact(feedbackDTO.getContact() != null ? feedbackDTO.getContact().trim() : null)
                .build();

        UserFeedbackEntity saved = feedbackRepository.save(feedback);

        log.debug("收到用户反馈 - 类型: {}, 有昵称: {}, 有联系方式: {}",
                feedbackDTO.getType(),
                feedbackDTO.getNickname() != null,
                feedbackDTO.getContact() != null);

        return saved;
    }

    /**
     * 获取所有反馈（管理员使用）
     */
    public List<UserFeedbackEntity> getAllFeedbacks() {
        return feedbackRepository.findAll();
    }

    /**
     * 根据类型获取反馈（管理员使用）
     */
    public List<UserFeedbackEntity> getFeedbacksByType(String type) {
        return feedbackRepository.findByType(type);
    }
}
