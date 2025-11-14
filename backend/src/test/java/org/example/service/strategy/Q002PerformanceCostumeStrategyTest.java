package org.example.service.strategy;

import org.example.pojo.GameContext;
import org.example.pojo.PlayerGameState;
import org.example.service.buff.BuffApplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Q002PerformanceCostumeStrategy 单元测试
 *
 * 游戏规则：
 * 你们二人参加演出，盲选服装。如果集齐侍卫+王子，则获得选项分数，否则扣分。
 * - A: 精致的侍卫服装（7分）
 * - B: 王子服装（5分）
 * - C: 普通侍卫服装（3分）
 *
 * 胜利条件：
 * - 有A（精致侍卫）或者 (有B（王子）且 有C（普通侍卫）)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Q002服装选择策略测试")
class Q002PerformanceCostumeStrategyTest {

    @Mock
    private BuffApplier buffApplier;

    private Q002PerformanceCostumeStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new Q002PerformanceCostumeStrategy(buffApplier);

        // Mock buffApplier默认不修改分数
        when(buffApplier.applyBuffs(any(), any(), any(), eq(false)))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    // ==================== 胜利场景测试 ====================

    @Test
    @DisplayName("胜利场景 - 有精致侍卫(A)")
    void calculateScores_WithA_ShouldGetPositiveScores() {
        // 准备：两个玩家都选A
        Map<String, String> submissions = new HashMap<>();
        submissions.put("player1", "A");
        submissions.put("player2", "A");

        GameContext context = createContext(submissions);

        // 执行 - 调用calculateResult以触发calculateBaseScores
        var result = strategy.calculateResult(context);

        // 验证：应该得分（不是扣分）
        // 从result中提取分数
        var playerSubmissions = result.getPlayerSubmissions();
        assertEquals(2, playerSubmissions.size());

        // 两个玩家都应该得7分（A的价值）
        playerSubmissions.forEach(submission -> {
            assertTrue(submission.getBaseScore() > 0, "应该得正分");
        });
    }

    @Test
    @DisplayName("胜利场景 - 有王子(B)和普通侍卫(C)")
    void calculateScores_WithBandC_ShouldGetPositiveScores() {
        // 准备：一个玩家选B，一个选C
        Map<String, String> submissions = new HashMap<>();
        submissions.put("player1", "B");
        submissions.put("player2", "C");

        GameContext context = createContext(submissions);

        // 执行
        var result = strategy.calculateResult(context);

        // 验证：应该得分
        var playerSubmissions = result.getPlayerSubmissions();

        playerSubmissions.forEach(submission -> {
            assertTrue(submission.getBaseScore() > 0, "应该得正分");
        });
    }

    @Test
    @DisplayName("胜利场景 - 三个玩家：A+B+C")
    void calculateScores_ThreePlayersWithABC_ShouldGetPositiveScores() {
        Map<String, String> submissions = new HashMap<>();
        submissions.put("player1", "A");
        submissions.put("player2", "B");
        submissions.put("player3", "C");

        GameContext context = createContext(submissions);

        var result = strategy.calculateResult(context);

        // 验证：所有人都得分
        var playerSubmissions = result.getPlayerSubmissions();
        assertEquals(3, playerSubmissions.size());

        playerSubmissions.forEach(submission -> {
            assertTrue(submission.getBaseScore() > 0);
        });
    }

    @Test
    @DisplayName("胜利场景 - 多个精致侍卫(A)")
    void calculateScores_MultipleA_ShouldGetPositiveScores() {
        Map<String, String> submissions = new HashMap<>();
        submissions.put("player1", "A");
        submissions.put("player2", "A");
        submissions.put("player3", "A");

        GameContext context = createContext(submissions);

        var result = strategy.calculateResult(context);

        // 验证：所有人都得7分
        result.getPlayerSubmissions().forEach(submission -> {
            assertTrue(submission.getBaseScore() > 0);
        });
    }

    // ==================== 失败场景测试 ====================

    @Test
    @DisplayName("失败场景 - 只有王子(B)，没有侍卫")
    void calculateScores_OnlyB_ShouldGetNegativeScores() {
        // 准备：两个玩家都选B
        Map<String, String> submissions = new HashMap<>();
        submissions.put("player1", "B");
        submissions.put("player2", "B");

        GameContext context = createContext(submissions);

        // 执行
        var result = strategy.calculateResult(context);

        // 验证：应该扣分或不得分
        var playerSubmissions = result.getPlayerSubmissions();

        playerSubmissions.forEach(submission -> {
            assertTrue(submission.getBaseScore() <= 0, "不满足条件应该扣分或不得分");
        });
    }

    @Test
    @DisplayName("失败场景 - 只有普通侍卫(C)，没有王子")
    void calculateScores_OnlyC_ShouldGetNegativeScores() {
        Map<String, String> submissions = new HashMap<>();
        submissions.put("player1", "C");
        submissions.put("player2", "C");

        GameContext context = createContext(submissions);

        var result = strategy.calculateResult(context);

        // 验证：应该扣分或不得分
        result.getPlayerSubmissions().forEach(submission -> {
            assertTrue(submission.getBaseScore() <= 0);
        });
    }

    @Test
    @DisplayName("失败场景 - 三个玩家都选B")
    void calculateScores_ThreePlayersAllB_ShouldGetNegativeScores() {
        Map<String, String> submissions = new HashMap<>();
        submissions.put("player1", "B");
        submissions.put("player2", "B");
        submissions.put("player3", "B");

        GameContext context = createContext(submissions);

        var result = strategy.calculateResult(context);

        // 验证
        result.getPlayerSubmissions().forEach(submission -> {
            assertTrue(submission.getBaseScore() <= 0);
        });
    }

    @Test
    @DisplayName("失败场景 - 三个玩家都选C")
    void calculateScores_ThreePlayersAllC_ShouldGetNegativeScores() {
        Map<String, String> submissions = new HashMap<>();
        submissions.put("player1", "C");
        submissions.put("player2", "C");
        submissions.put("player3", "C");

        GameContext context = createContext(submissions);

        var result = strategy.calculateResult(context);

        // 验证
        result.getPlayerSubmissions().forEach(submission -> {
            assertTrue(submission.getBaseScore() <= 0);
        });
    }

    // ==================== 分数值测试 ====================

    @Test
    @DisplayName("分数值 - 选A应该得7分（满足条件时）")
    void calculateScores_ChoiceA_ShouldGet7Points() {
        Map<String, String> submissions = new HashMap<>();
        submissions.put("player1", "A");  // 有A，条件满足

        GameContext context = createContext(submissions);

        var result = strategy.calculateResult(context);

        // 验证player1得7分
        var player1Score = result.getPlayerSubmissions().stream()
                .filter(s -> s.getPlayerId().equals("player1"))
                .findFirst()
                .orElseThrow();

        assertEquals(7, player1Score.getBaseScore(), "选A应该得7分");
    }

    @Test
    @DisplayName("分数值 - 选B应该得5分（满足条件时）")
    void calculateScores_ChoiceB_WithC_ShouldGet5Points() {
        Map<String, String> submissions = new HashMap<>();
        submissions.put("player1", "B");
        submissions.put("player2", "C");  // 有B和C，条件满足

        GameContext context = createContext(submissions);

        var result = strategy.calculateResult(context);

        // 验证player1得5分
        var player1Score = result.getPlayerSubmissions().stream()
                .filter(s -> s.getPlayerId().equals("player1"))
                .findFirst()
                .orElseThrow();

        assertEquals(5, player1Score.getBaseScore(), "选B应该得5分");
    }

    @Test
    @DisplayName("分数值 - 选C应该得3分（满足条件时）")
    void calculateScores_ChoiceC_WithB_ShouldGet3Points() {
        Map<String, String> submissions = new HashMap<>();
        submissions.put("player1", "C");
        submissions.put("player2", "B");  // 有B和C，条件满足

        GameContext context = createContext(submissions);

        var result = strategy.calculateResult(context);

        // 验证player1得3分
        var player1Score = result.getPlayerSubmissions().stream()
                .filter(s -> s.getPlayerId().equals("player1"))
                .findFirst()
                .orElseThrow();

        assertEquals(3, player1Score.getBaseScore(), "选C应该得3分");
    }

    // ==================== 边界情况测试 ====================

    @Test
    @DisplayName("边界情况 - 单个玩家")
    void calculateScores_SinglePlayer() {
        Map<String, String> submissions = new HashMap<>();
        submissions.put("player1", "A");

        GameContext context = createContext(submissions);

        var result = strategy.calculateResult(context);

        assertEquals(1, result.getPlayerSubmissions().size());
        assertEquals(7, result.getPlayerSubmissions().get(0).getBaseScore());
    }

    @Test
    @DisplayName("边界情况 - 空提交")
    void calculateScores_EmptySubmissions() {
        Map<String, String> submissions = new HashMap<>();

        GameContext context = createContext(submissions);

        var result = strategy.calculateResult(context);

        assertTrue(result.getPlayerSubmissions().isEmpty());
    }

    @Test
    @DisplayName("策略标识 - 应该返回Q002")
    void getQuestionIdentifier_ShouldReturnQ002() {
        assertEquals("Q002", strategy.getQuestionIdentifier());
    }

    // ==================== 混合场景测试 ====================

    @Test
    @DisplayName("混合场景 - 4个玩家，A+A+B+C")
    void calculateScores_FourPlayers_MixedChoices() {
        Map<String, String> submissions = new HashMap<>();
        submissions.put("player1", "A");
        submissions.put("player2", "A");
        submissions.put("player3", "B");
        submissions.put("player4", "C");

        GameContext context = createContext(submissions);

        var result = strategy.calculateResult(context);

        // 验证：条件满足（有A），所有人都得分
        assertEquals(4, result.getPlayerSubmissions().size());

        result.getPlayerSubmissions().forEach(submission -> {
            assertTrue(submission.getBaseScore() > 0);

            // 验证具体分数
            if (submission.getChoice().equals("A")) {
                assertEquals(7, submission.getBaseScore());
            } else if (submission.getChoice().equals("B")) {
                assertEquals(5, submission.getBaseScore());
            } else if (submission.getChoice().equals("C")) {
                assertEquals(3, submission.getBaseScore());
            }
        });
    }

    @Test
    @DisplayName("混合场景 - 3个玩家，B+B+C（应该成功）")
    void calculateScores_ThreePlayers_TwoB_OneC() {
        Map<String, String> submissions = new HashMap<>();
        submissions.put("player1", "B");
        submissions.put("player2", "B");
        submissions.put("player3", "C");

        GameContext context = createContext(submissions);

        var result = strategy.calculateResult(context);

        // 有B和C，条件满足
        result.getPlayerSubmissions().forEach(submission -> {
            assertTrue(submission.getBaseScore() > 0);
        });
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建游戏上下文
     */
    private GameContext createContext(Map<String, String> submissions) {
        Map<String, PlayerGameState> playerStates = new HashMap<>();

        submissions.forEach((playerId, choice) -> {
            PlayerGameState state = new PlayerGameState();
            state.setPlayerId(playerId);
            state.setPlayerName(playerId);
            state.setTotalScore(0);
            playerStates.put(playerId, state);
        });

        return GameContext.builder()
                .roomCode("TEST123")
                .currentSubmissions(submissions)
                .playerStates(playerStates)
                .currentQuestionIndex(0)
                .build();
    }
}
