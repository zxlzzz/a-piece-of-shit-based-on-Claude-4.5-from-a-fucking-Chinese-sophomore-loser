package org.example.service.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerDTO;
import org.example.dto.QuestionDTO;
import org.example.dto.RoomDTO;
import org.example.entity.PlayerEntity;
import org.example.entity.RoomEntity;
import org.example.exception.BusinessException;
import org.example.pojo.GameRoom;
import org.example.pojo.RoomStatus;
import org.example.repository.PlayerRepository;
import org.example.repository.RoomRepository;
import org.example.service.cache.RoomCache;
import org.example.service.timer.QuestionTimerService;
import org.example.utils.RoomLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomLifecycleService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final RoomCache roomCache;
    private final QuestionTimerService timerService;

    @Transactional
    public RoomEntity initializeRoom(Integer maxPlayers, Integer questionCount, Integer timeLimit, GameRoom gameRoom) {
        String roomCode = generateRoomCode();

        RoomEntity roomEntity = RoomEntity.builder()
                .roomCode(roomCode)
                .status(RoomStatus.WAITING)
                .maxPlayers(maxPlayers)
                .questionCount(questionCount)
                .timeLimit(timeLimit != null ? timeLimit : 30)
                .build();

        RoomEntity savedRoom = roomRepository.save(roomEntity);

        gameRoom.setRoomCode(roomCode);
        gameRoom.setMaxPlayers(maxPlayers);
        gameRoom.setPlayers(new ArrayList<>());
        gameRoom.setQuestions(new ArrayList<>());
        gameRoom.setStarted(false);
        gameRoom.setFinished(false);
        gameRoom.setCurrentIndex(-1);
        gameRoom.setSubmissions(new ConcurrentHashMap<>());
        gameRoom.setScores(new ConcurrentHashMap<>());
        gameRoom.setPlayerGameStates(new ConcurrentHashMap<>());
        gameRoom.setRoomEntity(savedRoom);

        return savedRoom;
    }

    @Transactional
    public void handleJoin(String roomCode, String playerId, String playerName) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("Room not found"));

        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (RoomLock.getLock(roomCode)) {
            if (room.getStatus() != RoomStatus.WAITING) {
                boolean playerInRoom = gameRoom.getPlayers().stream()
                        .anyMatch(p -> p.getPlayerId().equals(playerId));

                if (!playerInRoom) {
                    throw new BusinessException("Game already started");
                }
                return;
            }

            if (gameRoom.getPlayers().size() >= room.getMaxPlayers()) {
                throw new BusinessException("Room is full");
            }

            boolean playerExists = gameRoom.getPlayers().stream()
                    .anyMatch(p -> p.getPlayerId().equals(playerId));

            if (!playerExists) {
                PlayerEntity player = playerRepository.findByPlayerId(playerId)
                        .orElseThrow(() -> new BusinessException("Player not found"));

                player.setRoom(room);
                player.setReady(false);
                playerRepository.save(player);

                PlayerDTO playerDTO = PlayerDTO.builder()
                        .playerId(playerId)
                        .name(playerName)
                        .score(0)
                        .ready(false)
                        .build();

                if (gameRoom.isTestRoom()) {
                    gameRoom.getPlayers().add(0, playerDTO);
                } else {
                    gameRoom.getPlayers().add(playerDTO);
                }

                gameRoom.getScores().put(playerId, 0);
            }
            
            roomCache.put(roomCode, gameRoom);
        }
    }

    @Transactional
    public boolean handleLeave(String roomCode, String playerId) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("Room not found"));

        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (RoomLock.getLock(roomCode)) {
            // 移除玩家
            gameRoom.getPlayers().removeIf(p -> p.getPlayerId().equals(playerId));
            gameRoom.getScores().remove(playerId);

            PlayerEntity player = playerRepository.findByPlayerId(playerId).orElse(null);
            if (player != null) {
                player.setRoom(null);
                playerRepository.save(player);
            }

            // 如果房间没人了，直接删除
            if (gameRoom.getPlayers().isEmpty()) {
                deleteRoom(roomCode, gameRoom);
                return false;
            }

            roomCache.put(roomCode, gameRoom);
            return true;
        }
    }

    @Transactional
    public void setPlayerReady(String roomCode, String playerId, boolean ready) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        gameRoom.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .ifPresent(p -> p.setReady(ready));

        roomCache.put(roomCode, gameRoom);
    }

    public RoomDTO toRoomDTO(String roomCode) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        RoomEntity roomEntity = gameRoom.getRoomEntity();
        if (roomEntity == null) {
            roomEntity = roomRepository.findByRoomCode(roomCode)
                    .orElseThrow(() -> new BusinessException("Room not found"));
            gameRoom.setRoomEntity(roomEntity);
        }

        return toRoomDTO(roomEntity, gameRoom);
    }

    private RoomDTO toRoomDTO(RoomEntity roomEntity, GameRoom gameRoom) {
        RoomStatus status = roomEntity.getStatus();

        QuestionDTO currentQuestionDTO = gameRoom.getCurrentQuestion();

        int questionCount = 0;
        if (roomEntity.getQuestionCount() != null) {
            questionCount = roomEntity.getQuestionCount();
        } else if (gameRoom.getQuestions() != null) {
            questionCount = gameRoom.getQuestions().size();
        }

        int currentPlayers = gameRoom.getPlayers().size();

        java.util.List<String> submittedPlayerIds = new ArrayList<>();
        if (gameRoom.isStarted() && gameRoom.getCurrentIndex() >= 0) {
            Map<String, String> currentSubmissions = gameRoom.getSubmissions().get(gameRoom.getCurrentIndex());
            if (currentSubmissions != null) {
                submittedPlayerIds = new ArrayList<>(currentSubmissions.keySet());
            }
        }

        return RoomDTO.builder()
                .roomCode(gameRoom.getRoomCode())
                .maxPlayers(roomEntity.getMaxPlayers())
                .currentPlayers(currentPlayers)
                .status(status)
                .finished(gameRoom.isFinished())
                .players(new ArrayList<>(gameRoom.getPlayers()))
                .questionStartTime(gameRoom.getQuestionStartTime())
                .timeLimit(gameRoom.getTimeLimit() != null ? gameRoom.getTimeLimit() : 30)
                .currentIndex(gameRoom.getCurrentIndex())
                .currentQuestion(currentQuestionDTO)
                .questionCount(questionCount)
                .submittedPlayerIds(submittedPlayerIds)
                .build();
    }

    @Transactional
    protected void deleteRoom(String roomCode, GameRoom gameRoom) {
        try {
            RoomEntity room = roomRepository.findByRoomCode(roomCode).orElse(null);
            if (room != null) {
                roomRepository.delete(room);
            }

            timerService.cancelTimeout(roomCode);
            RoomLock.removeLock(roomCode);

        } catch (Exception e) {
            log.error("Error deleting room {}: {}", roomCode, e.getMessage());
        }
    }

    private String generateRoomCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
