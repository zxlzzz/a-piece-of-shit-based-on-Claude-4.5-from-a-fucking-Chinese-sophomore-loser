package org.example.service.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerDTO;
import org.example.dto.QuestionDTO;
import org.example.entity.*;
import org.example.exception.BusinessException;
import org.example.pojo.GameRoom;
import org.example.pojo.RoomStatus;
import org.example.repository.*;
import org.example.service.question.QuestionSelectorService;
import org.example.service.broadcast.RoomStateBroadcaster;
import org.example.service.cache.RoomCache;
import org.example.service.persistence.GamePersistenceService;
import org.example.service.room.RoomLifecycleService;
import org.example.service.scoring.ScoringResult;
import org.example.service.scoring.ScoringService;
import org.example.service.submission.SubmissionService;
import org.example.service.timer.QuestionTimerService;
import org.example.utils.RoomLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameFlowService {

    private final RoomCache roomCache;
    private final RoomRepository roomRepository;
    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final PlayerGameRepository playerGameRepository;
    private final QuestionSelectorService questionSelector;
    private final SubmissionService submissionService;
    private final ScoringService scoringService;
    private final QuestionTimerService timerService;
    private final RoomStateBroadcaster broadcaster;
    private final RoomLifecycleService roomLifecycleService;
    private final GamePersistenceService gamePersistenceService;
    private final ObjectMapper objectMapper;

    private final Map<String, AtomicBoolean> advancing = new java.util.concurrent.ConcurrentHashMap<>();

    @Transactional(timeout = 10)
    public void startGame(String roomCode) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (RoomLock.getLock(roomCode)) {
            if (gameRoom.isStarted()) {
                return;
            }

            RoomEntity room = roomRepository.findByRoomCode(roomCode)
                    .orElseThrow(() -> new BusinessException("Room not found"));
            room.setStatus(RoomStatus.PLAYING);
            roomRepository.save(room);

            GameEntity game = GameEntity.builder()
                    .room(room)
                    .startTime(LocalDateTime.now())
                    .build();
            GameEntity savedGame = gameRepository.save(game);

            gameRoom.setRoomEntity(room);
            gameRoom.setGameId(savedGame.getId());

            for (PlayerDTO playerDTO : gameRoom.getPlayers()) {
                if (playerDTO.getPlayerId().startsWith("BOT_")) {
                    continue;
                }

                PlayerEntity player = playerRepository.findByPlayerId(playerDTO.getPlayerId())
                        .orElseThrow(() -> new BusinessException("Player not found: " + playerDTO.getPlayerId()));

                PlayerGameEntity playerGame = PlayerGameEntity.builder()
                        .player(player)
                        .game(savedGame)
                        .score(0)
                        .build();
                playerGameRepository.save(playerGame);
            }

            int playerCount = gameRoom.getPlayers().size();

            List<QuestionDTO> questions = questionSelector.selectQuestions(
                    room.getQuestionCount(),
                    playerCount
            );

            if (questions == null || questions.isEmpty()) {
                room.setStatus(RoomStatus.WAITING);
                roomRepository.save(room);
                throw new BusinessException("Failed to load questions");
            }

            gameRoom.setQuestions(questions);
            gameRoom.setGameId(savedGame.getId());
            gameRoom.setStarted(true);
            gameRoom.setCurrentIndex(0);
            gameRoom.setQuestionStartTime(LocalDateTime.now());

            Integer timeLimit = room.getTimeLimit() != null ? room.getTimeLimit() : 30;
            gameRoom.setTimeLimit(timeLimit);

            timerService.scheduleTimeout(roomCode, timeLimit,
                    () -> advanceQuestion(roomCode, "timeout", true));

            roomCache.put(roomCode, gameRoom);

            broadcaster.sendRoomUpdate(roomCode, roomLifecycleService.toRoomDTO(roomCode));
        }
    }

    public void advanceQuestion(String roomCode, String reason, boolean fillDefaults) {
        AtomicBoolean isAdvancing = advancing.computeIfAbsent(roomCode, k -> new AtomicBoolean(false));
        if (!isAdvancing.compareAndSet(false, true)) {
            return;
        }

        try {
            synchronized (RoomLock.getLock(roomCode)) {
                GameRoom gameRoom = roomCache.get(roomCode);
                if (gameRoom == null || !gameRoom.isStarted()) {
                    return;
                }

                if (fillDefaults) {
                    submissionService.fillDefaultAnswers(gameRoom);
                }

                ScoringResult result = scoringService.calculateScores(gameRoom);
                if (result == null) {
                    return;
                }

                for (Map.Entry<String, Integer> entry : result.getFinalScores().entrySet()) {
                    String playerId = entry.getKey();
                    int score = entry.getValue();
                    gameRoom.getScores().merge(playerId, score, Integer::sum);
                }

                Map<Integer, Map<String, GameRoom.QuestionScoreDetail>> questionScores = gameRoom.getQuestionScores();
                if (questionScores == null) {
                    questionScores = new java.util.concurrent.ConcurrentHashMap<>();
                    gameRoom.setQuestionScores(questionScores);
                }
                questionScores.put(gameRoom.getCurrentIndex(), result.getScoreDetails());

                boolean isLastQuestion = gameRoom.getCurrentIndex() >= gameRoom.getQuestions().size() - 1;

                if (isLastQuestion) {
                    finishGame(gameRoom);
                } else {
                    gameRoom.setCurrentIndex(gameRoom.getCurrentIndex() + 1);
                    gameRoom.setQuestionStartTime(LocalDateTime.now());

                    Integer timeLimit = gameRoom.getTimeLimit() != null ? gameRoom.getTimeLimit() : 30;
                    timerService.scheduleTimeout(roomCode, timeLimit,
                            () -> advanceQuestion(roomCode, "timeout", true));

                    roomCache.put(roomCode, gameRoom);
                }

                broadcaster.sendRoomUpdate(roomCode, roomLifecycleService.toRoomDTO(roomCode));
            }
        } finally {
            isAdvancing.set(false);
        }
    }

    @Transactional
    protected void finishGame(GameRoom gameRoom) {
        try {
            gameRoom.setFinished(true);

            RoomEntity room = roomRepository.findByRoomCode(gameRoom.getRoomCode()).orElse(null);
            if (room != null) {
                room.setStatus(RoomStatus.FINISHED);
                roomRepository.save(room);
                gameRoom.setRoomEntity(room);
            }

            if (gameRoom.getGameId() != null) {
                GameEntity game = gameRepository.findById(gameRoom.getGameId()).orElse(null);
                if (game != null) {
                    game.setEndTime(LocalDateTime.now());
                    gameRepository.save(game);

                    for (Map.Entry<String, Integer> entry : gameRoom.getScores().entrySet()) {
                        String playerId = entry.getKey();

                        if (playerId.startsWith("BOT_")) {
                            continue;
                        }

                        PlayerEntity player = playerRepository.findByPlayerId(playerId)
                                .orElseThrow(() -> new BusinessException("Player not found: " + playerId));

                        PlayerGameEntity playerGame = playerGameRepository
                                .findByPlayerAndGame(player, game)
                                .orElseThrow(() -> new BusinessException("Game record not found"));

                        playerGame.setScore(entry.getValue());
                        playerGameRepository.save(playerGame);
                    }
                }
            }

            gamePersistenceService.saveGameResult(gameRoom);

            roomCache.put(gameRoom.getRoomCode(), gameRoom);

        } catch (Exception e) {
            log.error("Error finishing game for room {}: {}", gameRoom.getRoomCode(), e.getMessage(), e);
        }
    }
}
