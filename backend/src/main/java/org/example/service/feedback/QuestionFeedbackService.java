package org.example.service.feedback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.QuestionFeedbackDTO;
import org.example.entity.QuestionFeedbackEntity;
import org.example.repository.QuestionFeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 题目反馈服务
 *
 * TODO: 需要添加防恶意提交机制
 *       当前版本为MVP，没有任何防护措施
 *       后续需要添加：
 *       1. IP限流（同一IP对同一题目的提交频率限制）
 *       2. 内容校验（长度限制、敏感词过滤、HTML标签过滤）
 *       3. 全局Rate Limiting
 *       4. 可选：验证码
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionFeedbackService {

    private final QuestionFeedbackRepository feedbackRepository;

    /**
     * 提交题目反馈
     *
     * @param questionId 题目ID
     * @param feedbackDTO 反馈内容
     * @return 保存的反馈实体
     */
    @Transactional
    public QuestionFeedbackEntity submitFeedback(Long questionId, QuestionFeedbackDTO feedbackDTO) {
        // 基础校验：至少提交一个内容
        if (feedbackDTO.getRating() == null &&
            (feedbackDTO.getComment() == null || feedbackDTO.getComment().trim().isEmpty())) {
            throw new IllegalArgumentException("评分和评价至少需要提交一项");
        }

        // 评分范围校验
        if (feedbackDTO.getRating() != null &&
            (feedbackDTO.getRating() < 1 || feedbackDTO.getRating() > 5)) {
            throw new IllegalArgumentException("评分必须在1-5之间");
        }

        // 评论长度校验（可选）
        if (feedbackDTO.getComment() != null && feedbackDTO.getComment().length() > 500) {
            throw new IllegalArgumentException("评价内容不能超过500字");
        }

        // 保存反馈
        QuestionFeedbackEntity feedback = QuestionFeedbackEntity.builder()
                .questionId(questionId)
                .rating(feedbackDTO.getRating())
                .comment(feedbackDTO.getComment() != null ? feedbackDTO.getComment().trim() : null)
                .build();

        QuestionFeedbackEntity saved = feedbackRepository.save(feedback);

        log.debug("收到题目反馈 - 题目ID: {}, 评分: {}, 有评论: {}",
                questionId, feedbackDTO.getRating(), feedbackDTO.getComment() != null);

        return saved;
    }

    /**
     * 获取某题目的所有反馈
     * （仅供管理员使用）
     *
     * @param questionId 题目ID
     * @return 反馈列表
     */
    public List<QuestionFeedbackEntity> getFeedbacksByQuestionId(Long questionId) {
        return feedbackRepository.findByQuestionId(questionId);
    }

    /**
     * 获取某题目的反馈统计
     *
     * @param questionId 题目ID
     * @return 反馈数量
     */
    public long getFeedbackCount(Long questionId) {
        return feedbackRepository.countByQuestionId(questionId);
    }
}
