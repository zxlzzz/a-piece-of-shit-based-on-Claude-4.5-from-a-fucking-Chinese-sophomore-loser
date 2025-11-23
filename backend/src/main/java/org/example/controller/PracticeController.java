package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PracticeResultDTO;
import org.example.dto.PracticeSessionDTO;
import org.example.dto.QuestionDTO;
import org.example.entity.ChoiceRecordEntity;
import org.example.entity.QuestionEntity;
import org.example.exception.BusinessException;
import org.example.repository.QuestionRepository;
import org.example.service.question.QuestionFactory;
import org.example.service.question.QuestionScoringStrategy;
import org.example.service.question.impl.QuesServiceImpl;
import org.example.service.statistics.QuestionStatisticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 练习模式控制器
 * 提供单题练习功能，不保存游戏记录
 */
@RestController
@RequestMapping("/api/practice")
@RequiredArgsConstructor
@Slf4j
public class PracticeController {

    private final QuestionRepository questionRepository;
    private final QuestionStatisticsService statisticsService;
    private final QuestionFactory questionFactory;
    private final QuesServiceImpl quesService;

    /**
     * 练习会话缓存（临时存储，不持久化）
     * Key: sessionId
     * Value: PracticeSession
     */
    private final Map<String, PracticeSession> sessions = new ConcurrentHashMap<>();

    /**
     * 开始练习会话
     * POST /api/practice/start
     *
     * @param questionId 题目ID（可选，不提供则随机）
     * @param playerCount 玩家人数（必需，用于匹配题目和生成统计）
     * @return 练习会话信息
     */
    @PostMapping("/start")
    public ResponseEntity<PracticeSessionDTO> startPractice(
            @RequestParam(required = false) Long questionId,
            @RequestParam Integer playerCount) {

        log.info("🎯 开始练习会话: questionId={}, playerCount={}", questionId, playerCount);

        // 获取题目
        QuestionEntity questionEntity;
        if (questionId != null) {
            // 指定题目
            questionEntity = questionRepository.findById(questionId)
                    .orElseThrow(() -> new BusinessException("题目不存在: " + questionId));

            // 检查题目是否适合当前人数
            if (questionEntity.getMinPlayers() != null && playerCount < questionEntity.getMinPlayers()) {
                throw new BusinessException("当前题目至少需要 " + questionEntity.getMinPlayers() + " 人");
            }
            if (questionEntity.getMaxPlayers() != null && playerCount > questionEntity.getMaxPlayers()) {
                throw new BusinessException("当前题目最多支持 " + questionEntity.getMaxPlayers() + " 人");
            }
        } else {
            // 随机题目（根据人数筛选）
            List<QuestionDTO> suitableQuestions = quesService.getQuestionsByPlayerCountDTO(playerCount, 1);
            if (suitableQuestions.isEmpty()) {
                throw new BusinessException("没有适合 " + playerCount + " 人的题目");
            }
            questionEntity = questionRepository.findById(suitableQuestions.get(0).getId())
                    .orElseThrow(() -> new BusinessException("题目获取失败"));
        }

        // 生成会话ID
        String sessionId = UUID.randomUUID().toString();

        // 转换为DTO
        QuestionDTO questionDTO = quesService.convertEntitiesToDTOs(List.of(questionEntity)).get(0);

        // 生成Bot选择（基于统计数据）
        String botChoice = statisticsService.generateBotChoice(questionEntity.getId(), playerCount);

        // 如果没有统计数据，随机生成Bot选择
        if (botChoice == null) {
            botChoice = generateRandomChoice(questionDTO);
            log.info("📊 没有统计数据，随机生成Bot选择: {}", botChoice);
        }

        // 创建并保存会话
        PracticeSession session = new PracticeSession(
                sessionId,
                questionEntity.getId(),
                questionDTO,
                botChoice,
                playerCount
        );
        sessions.put(sessionId, session);

        // 返回会话信息（不包含bot选择）
        PracticeSessionDTO response = PracticeSessionDTO.builder()
                .sessionId(sessionId)
                .question(questionDTO)
                .botChoice(null)  // 提交前不返回
                .playerCount(playerCount)
                .build();

        log.info("✅ 练习会话创建成功: sessionId={}, questionId={}", sessionId, questionEntity.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * 提交练习答案
     * POST /api/practice/submit
     *
     * @param sessionId 会话ID
     * @param playerChoice 玩家选择
     * @param playerId 玩家ID（可选，用于统计记录）
     * @return 练习结果（包含双方选择和得分）
     */
    @PostMapping("/submit")
    public ResponseEntity<PracticeResultDTO> submitAnswer(
            @RequestParam String sessionId,
            @RequestParam String playerChoice,
            @RequestParam(required = false) String playerId) {

        log.info("📝 提交练习答案: sessionId={}, playerChoice={}, playerId={}",
                sessionId, playerChoice, playerId);

        // 获取会话
        PracticeSession session = sessions.get(sessionId);
        if (session == null) {
            throw new BusinessException("会话不存在或已过期: " + sessionId);
        }

        // 验证选择是否有效
        QuestionDTO question = session.getQuestion();
        if (!isValidChoice(question, playerChoice)) {
            throw new BusinessException("无效的选择: " + playerChoice);
        }

        // 获取计分策略
        QuestionScoringStrategy strategy = questionFactory.getStrategy(question.getStrategyId());

        // 准备提交数据（玩家 + Bot）
        Map<String, String> submissions = new HashMap<>();
        submissions.put("player", playerChoice);
        submissions.put("bot", session.getBotChoice());

        // 计算分数
        Map<String, Integer> scores = strategy.calculateScores(submissions);

        // 异步记录玩家选择到统计（Bot选择不记录）
        statisticsService.recordChoice(
                session.getQuestionId(),
                playerChoice,
                playerId,
                session.getPlayerCount(),
                ChoiceRecordEntity.GameType.PRACTICE,
                null  // 练习模式没有roomCode
        );

        // 构建结果
        PracticeResultDTO result = PracticeResultDTO.builder()
                .playerChoice(playerChoice)
                .botChoice(session.getBotChoice())
                .playerScore(scores.getOrDefault("player", 0))
                .botScore(scores.getOrDefault("bot", 0))
                .question(question)
                .allScores(scores)
                .build();

        // 删除会话（一次性）
        sessions.remove(sessionId);

        log.info("✅ 练习答案提交成功: sessionId={}, 玩家得分={}, Bot得分={}",
                sessionId, result.getPlayerScore(), result.getBotScore());

        return ResponseEntity.ok(result);
    }

    /**
     * 验证选择是否有效
     */
    private boolean isValidChoice(QuestionDTO question, String choice) {
        switch (question.getType()) {
            case CHOICE:
                // 选择题：检查选项是否存在
                return question.getOptions() != null &&
                        question.getOptions().stream()
                                .anyMatch(opt -> opt.getKey().equals(choice));
            case BID:
                // 竞价题：检查是否在范围内
                try {
                    int value = Integer.parseInt(choice);
                    return value >= question.getMin() &&
                           value <= question.getMax() &&
                           (value - question.getMin()) % question.getStep() == 0;
                } catch (NumberFormatException e) {
                    return false;
                }
            default:
                return false;
        }
    }

    /**
     * 随机生成Bot选择（当没有统计数据时使用）
     */
    private String generateRandomChoice(QuestionDTO question) {
        switch (question.getType()) {
            case CHOICE:
                // 选择题：随机选一个选项
                if (question.getOptions() != null && !question.getOptions().isEmpty()) {
                    int randomIndex = (int) (Math.random() * question.getOptions().size());
                    return question.getOptions().get(randomIndex).getKey();
                }
                return "A"; // 默认选项
            case BID:
                // 竞价题：在范围内随机选择
                int range = (question.getMax() - question.getMin()) / question.getStep();
                int randomSteps = (int) (Math.random() * (range + 1));
                return String.valueOf(question.getMin() + randomSteps * question.getStep());
            default:
                return "A";
        }
    }

    /**
     * 练习会话内部类（临时存储）
     */
    private static class PracticeSession {
        private final String sessionId;
        private final Long questionId;
        private final QuestionDTO question;
        private final String botChoice;
        private final Integer playerCount;

        public PracticeSession(String sessionId, Long questionId, QuestionDTO question,
                               String botChoice, Integer playerCount) {
            this.sessionId = sessionId;
            this.questionId = questionId;
            this.question = question;
            this.botChoice = botChoice;
            this.playerCount = playerCount;
        }

        public String getSessionId() { return sessionId; }
        public Long getQuestionId() { return questionId; }
        public QuestionDTO getQuestion() { return question; }
        public String getBotChoice() { return botChoice; }
        public Integer getPlayerCount() { return playerCount; }
    }
}
