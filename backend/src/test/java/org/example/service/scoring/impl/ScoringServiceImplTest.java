package org.example.service.scoring.impl;

import org.example.dto.PlayerDTO;
import org.example.dto.PlayerSubmissionDTO;
import org.example.dto.QuestionDTO;
import org.example.dto.QuestionDetailDTO;
import org.example.exception.BusinessException;
import org.example.pojo.GameRoom;
import org.example.service.cache.RoomCache;
import org.example.service.question.QuestionFactory;
import org.example.service.question.QuestionScoringStrategy;
import org.example.service.scoring.ScoringResult;
import org.example.service.strategy.QR.RepeatableQuestionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ScoringServiceImpl 单元测试
 *
 * 测试覆盖：
 * 1. 普通题目计分
 * 2. 可重复题目计分
 * 3. 空提交处理
 * 4. 观战者过滤
 * 5. 轮次管理
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("计分服务测试")
class ScoringServiceImplTest {

    @Mock
    private RoomCache roomCache;

    @Mock
    private QuestionFactory questionFactory;

    @Mock
    private QuestionScoringStrategy mockStrategy;

    @Mock
    private RepeatableQuestionStrategy mockRepeatableStrategy;

    @InjectMocks
    private ScoringServiceImpl scoringService;

    private GameRoom gameRoom;
    private QuestionDTO testQuestion;

    private static final String ROOM_CODE = "TEST123";
    private static final String PLAYER1_ID = "player1";
    private static final String PLAYER2_ID = "player2";
    private static final String SPECTATOR_ID = "spectator1";
    private static final String STRATEGY_ID = "Q001";

    @BeforeEach
    void setUp() {
        // 创建测试问题
        testQuestion = QuestionDTO.builder()
                .id(1L)
                .strategyId(STRATEGY_ID)
                .text("测试问题")
                .build();

        // 创建玩家列表
        List<PlayerDTO> players = new ArrayList<>();
        players.add(PlayerDTO.builder()
                .playerId(PLAYER1_ID)
                .name("玩家1")
                .spectator(false)
                .build());
        players.add(PlayerDTO.builder()
                .playerId(PLAYER2_ID)
                .name("玩家2")
                .spectator(false)
                .build());
        players.add(PlayerDTO.builder()
                .playerId(SPECTATOR_ID)
                .name("观战者")
                .spectator(true)
                .build());

        // 创建游戏房间
        gameRoom = new GameRoom();
        gameRoom.setRoomCode(ROOM_CODE);
        gameRoom.setPlayers(players);
        gameRoom.setQuestions(List.of(testQuestion));
        gameRoom.setCurrentIndex(0);
        gameRoom.setScores(new ConcurrentHashMap<>());
        gameRoom.setSubmissions(new ConcurrentHashMap<>());

        // 添加提交记录
        Map<String, String> submissions = new HashMap<>();
        submissions.put(PLAYER1_ID, "A");
        submissions.put(PLAYER2_ID, "B");
        gameRoom.getSubmissions().put(0, submissions);
    }

    // ==================== 普通题目计分测试 ====================

    @Test
    @DisplayName("计分成功 - 普通题目")
    void calculateScores_NormalQuestion_Success() {
        // Mock策略
        when(questionFactory.getStrategy(STRATEGY_ID)).thenReturn(mockStrategy);
        when(mockStrategy instanceof RepeatableQuestionStrategy).thenReturn(false);

        // Mock计分结果
        QuestionDetailDTO detailDTO = createMockQuestionDetail();
        when(mockStrategy.calculateResult(any())).thenReturn(detailDTO);

        // 执行
        ScoringResult result = scoringService.calculateScores(gameRoom);

        // 验证
        assertNotNull(result);
        assertFalse(result.isRepeatableQuestion());
        assertEquals(10, result.getBaseScores().get(PLAYER1_ID));
        assertEquals(20, result.getBaseScores().get(PLAYER2_ID));
        assertEquals(10, result.getFinalScores().get(PLAYER1_ID));
        assertEquals(20, result.getFinalScores().get(PLAYER2_ID));

        // 验证策略被调用
        verify(mockStrategy).calculateResult(any());
        verify(questionFactory).getStrategy(STRATEGY_ID);
    }

    @Test
    @DisplayName("计分失败 - 策略不存在")
    void calculateScores_StrategyNotFound_ThrowsException() {
        // Mock：策略不存在
        when(questionFactory.getStrategy(STRATEGY_ID)).thenReturn(null);

        // 执行并验证异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            scoringService.calculateScores(gameRoom);
        });

        assertTrue(exception.getMessage().contains("无法获取题目策略"));
    }

    @Test
    @DisplayName("计分 - 过滤观战者")
    void calculateScores_FilterSpectators() {
        when(questionFactory.getStrategy(STRATEGY_ID)).thenReturn(mockStrategy);

        // Mock计分结果（只包含玩家，不包含观战者）
        QuestionDetailDTO detailDTO = createMockQuestionDetail();
        when(mockStrategy.calculateResult(any())).thenReturn(detailDTO);

        // 执行
        ScoringResult result = scoringService.calculateScores(gameRoom);

        // 验证：结果中不应包含观战者
        assertFalse(result.getBaseScores().containsKey(SPECTATOR_ID));
        assertFalse(result.getFinalScores().containsKey(SPECTATOR_ID));

        // 只有两个玩家的分数
        assertEquals(2, result.getBaseScores().size());
        assertEquals(2, result.getFinalScores().size());
    }

    @Test
    @DisplayName("计分 - 空提交记录")
    void calculateScores_EmptySubmissions() {
        // 清空提交记录
        gameRoom.getSubmissions().clear();

        // 执行
        ScoringResult result = scoringService.calculateScores(gameRoom);

        // 验证：返回空结果
        assertNotNull(result);
        assertTrue(result.getBaseScores().isEmpty());
        assertTrue(result.getFinalScores().isEmpty());
        assertFalse(result.isRepeatableQuestion());
        assertEquals(0, result.getCurrentRound());
        assertEquals(0, result.getTotalRounds());

        // 策略不应该被调用
        verify(questionFactory, never()).getStrategy(anyString());
    }

    @Test
    @DisplayName("计分 - null提交记录")
    void calculateScores_NullSubmissions() {
        // 设置为null
        gameRoom.getSubmissions().put(0, null);

        // 执行
        ScoringResult result = scoringService.calculateScores(gameRoom);

        // 验证：返回空结果
        assertNotNull(result);
        assertTrue(result.getBaseScores().isEmpty());
        assertTrue(result.getFinalScores().isEmpty());
    }

    // ==================== 可重复题目测试 ====================

    @Test
    @DisplayName("计分成功 - 可重复题目")
    void calculateScores_RepeatableQuestion_Success() {
        // Mock可重复策略
        when(questionFactory.getStrategy(STRATEGY_ID)).thenReturn(mockRepeatableStrategy);
        when(mockRepeatableStrategy.getTotalRounds()).thenReturn(3);

        // Mock计分结果
        QuestionDetailDTO detailDTO = createMockQuestionDetail();
        when(mockRepeatableStrategy.calculateRoundResult(any(), eq(1))).thenReturn(detailDTO);

        // 执行
        ScoringResult result = scoringService.calculateScores(gameRoom);

        // 验证
        assertNotNull(result);
        assertTrue(result.isRepeatableQuestion());
        assertEquals(1, result.getCurrentRound());
        assertEquals(3, result.getTotalRounds());

        // 验证策略被调用
        verify(mockRepeatableStrategy).calculateRoundResult(any(), eq(1));
        verify(mockRepeatableStrategy).getTotalRounds();
    }

    @Test
    @DisplayName("可重复题目 - 应该继续重复")
    void shouldContinueRepeating_ShouldReturnTrue() {
        ScoringResult result = ScoringResult.builder()
                .repeatableQuestion(true)
                .currentRound(2)
                .totalRounds(3)
                .build();

        boolean shouldContinue = scoringService.shouldContinueRepeating(gameRoom, result);

        assertTrue(shouldContinue);
    }

    @Test
    @DisplayName("可重复题目 - 不应该继续重复（已到最后一轮）")
    void shouldContinueRepeating_LastRound_ShouldReturnFalse() {
        ScoringResult result = ScoringResult.builder()
                .repeatableQuestion(true)
                .currentRound(3)
                .totalRounds(3)
                .build();

        boolean shouldContinue = scoringService.shouldContinueRepeating(gameRoom, result);

        assertFalse(shouldContinue);
    }

    @Test
    @DisplayName("可重复题目 - 不应该继续重复（普通题目）")
    void shouldContinueRepeating_NormalQuestion_ShouldReturnFalse() {
        ScoringResult result = ScoringResult.builder()
                .repeatableQuestion(false)
                .currentRound(0)
                .totalRounds(0)
                .build();

        boolean shouldContinue = scoringService.shouldContinueRepeating(gameRoom, result);

        assertFalse(shouldContinue);
    }

    @Test
    @DisplayName("清理轮次记录 - 成功")
    void clearRounds_Success() {
        // 先计算一次以创建轮次记录
        when(questionFactory.getStrategy(STRATEGY_ID)).thenReturn(mockRepeatableStrategy);
        when(mockRepeatableStrategy.getTotalRounds()).thenReturn(3);
        when(mockRepeatableStrategy.calculateRoundResult(any(), anyInt()))
                .thenReturn(createMockQuestionDetail());

        scoringService.calculateScores(gameRoom);

        // 清理
        assertDoesNotThrow(() -> {
            scoringService.clearRounds(ROOM_CODE);
        });
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建模拟的问题详情DTO
     */
    private QuestionDetailDTO createMockQuestionDetail() {
        List<PlayerSubmissionDTO> submissions = new ArrayList<>();

        submissions.add(PlayerSubmissionDTO.builder()
                .playerId(PLAYER1_ID)
                .playerName("玩家1")
                .choice("A")
                .baseScore(10)
                .finalScore(10)
                .build());

        submissions.add(PlayerSubmissionDTO.builder()
                .playerId(PLAYER2_ID)
                .playerName("玩家2")
                .choice("B")
                .baseScore(20)
                .finalScore(20)
                .build());

        return QuestionDetailDTO.builder()
                .questionId(1L)
                .questionText("测试问题")
                .playerSubmissions(submissions)
                .build();
    }

    // ==================== 边界情况测试 ====================

    @Test
    @DisplayName("边界情况 - 单个玩家")
    void calculateScores_SinglePlayer() {
        // 只保留一个玩家
        gameRoom.setPlayers(List.of(PlayerDTO.builder()
                .playerId(PLAYER1_ID)
                .name("玩家1")
                .spectator(false)
                .build()));

        Map<String, String> submissions = new HashMap<>();
        submissions.put(PLAYER1_ID, "A");
        gameRoom.getSubmissions().put(0, submissions);

        when(questionFactory.getStrategy(STRATEGY_ID)).thenReturn(mockStrategy);

        QuestionDetailDTO detailDTO = QuestionDetailDTO.builder()
                .playerSubmissions(List.of(PlayerSubmissionDTO.builder()
                        .playerId(PLAYER1_ID)
                        .baseScore(10)
                        .finalScore(10)
                        .build()))
                .build();

        when(mockStrategy.calculateResult(any())).thenReturn(detailDTO);

        // 执行
        ScoringResult result = scoringService.calculateScores(gameRoom);

        // 验证
        assertEquals(1, result.getBaseScores().size());
        assertTrue(result.getBaseScores().containsKey(PLAYER1_ID));
    }

    @Test
    @DisplayName("边界情况 - 所有玩家都是观战者")
    void calculateScores_AllSpectators() {
        // 所有玩家都设为观战者
        gameRoom.getPlayers().forEach(p -> p.setSpectator(true));

        when(questionFactory.getStrategy(STRATEGY_ID)).thenReturn(mockStrategy);

        QuestionDetailDTO detailDTO = QuestionDetailDTO.builder()
                .playerSubmissions(new ArrayList<>())
                .build();

        when(mockStrategy.calculateResult(any())).thenReturn(detailDTO);

        // 执行
        ScoringResult result = scoringService.calculateScores(gameRoom);

        // 验证：应该没有分数
        assertTrue(result.getBaseScores().isEmpty());
        assertTrue(result.getFinalScores().isEmpty());
    }

    @Test
    @DisplayName("边界情况 - 玩家有初始分数")
    void calculateScores_WithInitialScores() {
        // 设置初始分数
        gameRoom.getScores().put(PLAYER1_ID, 50);
        gameRoom.getScores().put(PLAYER2_ID, 30);

        when(questionFactory.getStrategy(STRATEGY_ID)).thenReturn(mockStrategy);
        when(mockStrategy.calculateResult(any())).thenReturn(createMockQuestionDetail());

        // 执行
        ScoringResult result = scoringService.calculateScores(gameRoom);

        // 验证：返回本轮分数，不包含历史分数
        assertEquals(10, result.getFinalScores().get(PLAYER1_ID));
        assertEquals(20, result.getFinalScores().get(PLAYER2_ID));
    }
}
