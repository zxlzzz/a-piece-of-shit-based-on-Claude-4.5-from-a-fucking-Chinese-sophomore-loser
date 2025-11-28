package org.example.service.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.controller.GameController;
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
import org.example.service.chat.ChatRoomManager;
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
    private final ObjectMapper objectMapper;
    private final QuestionTimerService timerService;
    private final ChatRoomManager chatRoomManager;

    @Transactional
    public RoomEntity initializeRoom(Integer maxPlayers, Integer questionCount, Integer timeLimit, GameRoom gameRoom) {
        String roomCode = generateRoomCode();

        RoomEntity roomEntity = RoomEntity.builder()
                .roomCode(roomCode)
                .status(RoomStatus.WAITING)
                .maxPlayers(maxPlayers)
                .questionCount(questionCount)
                .timeLimit(timeLimit != null ? timeLimit : 30)
                .password(null)
                .rankingMode("standard")
                .targetScore(null)
                .winConditionsJson(null)
                .questionTagIdsJson(null)
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
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (RoomLock.getLock(roomCode)) {
            if (room.getStatus() != RoomStatus.WAITING) {
                boolean playerInRoom = gameRoom.getPlayers().stream()
                        .anyMatch(p -> p.getPlayerId().equals(playerId));

                if (!playerInRoom) {
                    throw new BusinessException("房间已开始游戏或已结束");
                }

                    roomCache.put(roomCode, gameRoom);

                return;
            }

            if (gameRoom.getPlayers().size() >= room.getMaxPlayers()) {
                throw new BusinessException("房间已满");
            }

            boolean playerExists = gameRoom.getPlayers().stream()
                    .anyMatch(p -> p.getPlayerId().equals(playerId));

            if (!playerExists) {
                PlayerEntity player = playerRepository.findByPlayerId(playerId)
                        .orElseThrow(() -> new BusinessException("玩家不存在，请先登录"));

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

                roomCache.put(roomCode, gameRoom);
            } else {
                roomCache.put(roomCode, gameRoom);
            }
        }
    }

    @Transactional
    public boolean handleLeave(String roomCode, String playerId) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (RoomLock.getLock(roomCode)) {
            PlayerDTO leavingPlayer = gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .orElse(null);

            String playerName = leavingPlayer != null ? leavingPlayer.getName() : "未知玩家";

            if (!gameRoom.isStarted()) {
                boolean isRoomOwner = !gameRoom.getPlayers().isEmpty() &&
                        gameRoom.getPlayers().get(0).getPlayerId().equals(playerId);

                if (isRoomOwner) {
                    deleteRoomAtomically(roomCode, gameRoom);
                    return false;
                } else {
                    gameRoom.getPlayers().removeIf(p -> p.getPlayerId().equals(playerId));
                    gameRoom.getScores().remove(playerId);

                    PlayerEntity player = playerRepository.findByPlayerId(playerId).orElse(null);
                    if (player != null) {
                        player.setRoom(null);
                        playerRepository.save(player);
                    }


                    roomCache.put(roomCode, gameRoom);
                }

            } else {

                long connectedCount = gameRoom.getPlayers().stream()
                        .count();

                if (connectedCount == 0) {
                    if (gameRoom.isStarted() && !gameRoom.isFinished()) {
                        log.warn(" 房间 {} 所有玩家断线，但游戏进行中，保留房间等待重连", roomCode);
                        roomCache.put(roomCode, gameRoom);
                        return true;
                    } else {
                        deleteRoomAtomically(roomCode, gameRoom);
                        return false;
                    }
                }

                roomCache.put(roomCode, gameRoom);
            }

            return true;
        }
    }

    

                if (gameRoom.isFinished()) {
                }
            } else {
                log.warn(" 玩家 {} 重连房间 {}，但未找到断线记录", playerId, roomCode);
            }

            roomCache.put(roomCode, gameRoom);
        }
    }

    

            if (request.getQuestionCount() != null && request.getQuestionCount() > 0) {
                room.setQuestionCount(request.getQuestionCount());
            }

            if (request.getTimeLimit() != null && request.getTimeLimit() >= 20 && request.getTimeLimit() <= 120) {
                room.setTimeLimit(request.getTimeLimit());
            }

            if (request.getChatEnabled() != null) {
                room.setChatEnabled(request.getChatEnabled());
            }

            if (request.getRankingMode() != null) {
                room.setRankingMode(request.getRankingMode());
            }

            room.setTargetScore(request.getTargetScore());

            String winConditionsJson = null;
            if (request.getWinConditions() != null) {
                try {
                    winConditionsJson = objectMapper.writeValueAsString(request.getWinConditions());
                } catch (Exception e) {
                    log.error("序列化通关条件失败", e);
                    throw new BusinessException("通关条件格式错误");
                }
            }
            room.setWinConditionsJson(winConditionsJson);

            String questionTagIdsJson = null;
            if (request.getQuestionTagIds() != null) {
                try {
                    questionTagIdsJson = objectMapper.writeValueAsString(request.getQuestionTagIds());
                } catch (Exception e) {
                    log.error("序列化题目标签失败", e);
                    throw new BusinessException("题目标签格式错误");
                }
            }
            room.setQuestionTagIdsJson(questionTagIdsJson);

            RoomEntity savedRoom = roomRepository.save(room);
            gameRoom.setRoomEntity(savedRoom);

        }
    }

    @Transactional
    public void setPlayerReady(String roomCode, String playerId, boolean ready) {
        GameRoom gameRoom = roomCache.get(roomCode);
        if (gameRoom == null) {
            throw new BusinessException("房间不存在");
        }

        if (playerId.startsWith("BOT_")) {
            gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .ifPresent(p -> p.setReady(ready));

            roomCache.put(roomCode, gameRoom);
            return;
        }

        PlayerEntity player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

        if (!player.getRoom().getRoomCode().equals(roomCode)) {
            throw new BusinessException("玩家不在该房间中");
        }

        player.setReady(ready);
        playerRepository.save(player);

        gameRoom.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .ifPresent(p -> p.setReady(ready));

        roomCache.put(roomCode, gameRoom);

        long totalPlayers = gameRoom.getPlayers().size();
        long readyPlayers = gameRoom.getPlayers().stream()
            .filter(PlayerDTO::getReady)
            .count();
    }

    public RoomDTO toRoomDTO(String roomCode) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        RoomEntity roomEntity = gameRoom.getRoomEntity();
        if (roomEntity == null) {
            roomEntity = roomRepository.findByRoomCode(roomCode)
                    .orElseThrow(() -> new BusinessException("房间不存在"));
            gameRoom.setRoomEntity(roomEntity);
        }

        return toRoomDTO(roomEntity, gameRoom);
    }


            return;
        }

        synchronized (RoomLock.getLock(roomCode)) {
            String playerName = gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .map(PlayerDTO::getName)
                    .findFirst()
                    .orElse("未知玩家");


            if (gameRoom.isStarted() && gameRoom.getCurrentQuestion() != null) {
                boolean allDisconnected = gameRoom.getPlayers().stream()
                        .allMatch(PlayerDTO::getDisconnected);

                if (allDisconnected) {
                    log.warn(" 房间 {} 所有玩家都断开连接", roomCode);
                    timerService.cancelTimeout(roomCode);
                }
            }
        }
    }


            return;
        }

        synchronized (RoomLock.getLock(roomCode)) {
            if (gameRoom.isStarted() && !gameRoom.isFinished()) {
                return;
            }



            PlayerDTO removedPlayer = gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .orElse(null);

            if (removedPlayer != null) {
                gameRoom.getPlayers().remove(removedPlayer);
            }

            gameRoom.getScores().remove(playerId);

            gameRoom.getPlayerGameStates().remove(playerId);

            PlayerEntity player = playerRepository.findByPlayerId(playerId).orElse(null);
            if (player != null) {
                player.setRoom(null);
                player.setReady(false);
                playerRepository.save(player);
            }

            if (gameRoom.getPlayers().isEmpty()) {
                log.warn("🏠 房间 {} 所有玩家都已离开，准备解散", roomCode);
                deleteRoomAtomically(roomCode, gameRoom);
            }
        }
    }

    private RoomDTO toRoomDTO(RoomEntity roomEntity, GameRoom gameRoom) {
        RoomStatus status = RoomStatus.WAITING;
        if (gameRoom.isFinished()) {
            status = RoomStatus.FINISHED;
        } else if (gameRoom.isStarted()) {
            status = RoomStatus.PLAYING;
        }

        
        QuestionDTO currentQuestionDTO = gameRoom.getCurrentQuestion();

        Integer questionCount = null;
        if (gameRoom.getQuestions() != null && !gameRoom.getQuestions().isEmpty()) {
            questionCount = gameRoom.getQuestions().size();
        } else if (roomEntity != null && roomEntity.getQuestionCount() != null) {
            questionCount = roomEntity.getQuestionCount();
        } else {
            questionCount = 10;
        }

        RoomDTO.WinConditions winConditions = null;
        if (roomEntity != null && roomEntity.getWinConditionsJson() != null) {
            try {
                winConditions = objectMapper.readValue(
                        roomEntity.getWinConditionsJson(),
                        RoomDTO.WinConditions.class
                );
            } catch (Exception e) {
                log.error("解析通关条件失败", e);
            }
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
                .maxPlayers(gameRoom.getMaxPlayers() != null ? gameRoom.getMaxPlayers() :
                        (roomEntity != null ? roomEntity.getMaxPlayers() : gameRoom.getPlayers().size()))
                .currentPlayers(currentPlayers)
                .status(status)
                .finished(gameRoom.isFinished())
                .players(new ArrayList<>(gameRoom.getPlayers()))
                .questionStartTime(gameRoom.getQuestionStartTime())
                .timeLimit(gameRoom.getTimeLimit() != null ? gameRoom.getTimeLimit() :
                        (roomEntity != null && roomEntity.getTimeLimit() != null ? roomEntity.getTimeLimit() : 30))
                .currentIndex(gameRoom.getCurrentIndex())
                .currentQuestion(currentQuestionDTO)
                .questionCount(questionCount)
                .hasPassword(roomEntity != null && roomEntity.getPassword() != null && !roomEntity.getPassword().isEmpty())
                .submittedPlayerIds(submittedPlayerIds)
                .rankingMode(roomEntity != null ? roomEntity.getRankingMode() : "standard")
                .targetScore(roomEntity != null ? roomEntity.getTargetScore() : null)
                .winConditions(winConditions)
                .chatEnabled(roomEntity != null ? roomEntity.getChatEnabled() : true)
                .privateChatEnabled(roomEntity != null ? roomEntity.getPrivateChatEnabled() : true)
                .build();
    }

    @Transactional
    public void deleteRoom(String roomCode) {
        GameRoom gameRoom = roomCache.get(roomCode);
        deleteRoomAtomically(roomCode, gameRoom);
    }


    @Transactional
    protected RoomEntity deleteRoomAtomically(String roomCode, GameRoom gameRoom) {
        RoomEntity room;

        synchronized (RoomLock.getLock(roomCode)) {
            room = roomRepository.findByRoomCode(roomCode).orElse(null);
            if (room == null) {
                log.warn(" 房间 {} 已不存在，跳过删除", roomCode);
                RoomLock.removeLock(roomCode);
                return null;
            }

            if (room.getStatus() == RoomStatus.FINISHED && room.getId() == null) {
                log.warn(" 房间 {} 已处于删除状态，跳过重复删除", roomCode);
                RoomLock.removeLock(roomCode);
                return null;
            }


            if (gameRoom != null) {
                for (PlayerDTO player : gameRoom.getPlayers()) {
                    String playerId = player.getPlayerId();
                    if (!playerId.startsWith("BOT_")) {
                        PlayerEntity playerEntity = playerRepository.findByPlayerId(playerId).orElse(null);
                        if (playerEntity != null) {
                            playerEntity.setRoom(null);
                            playerEntity.setReady(false);
                            playerRepository.save(playerEntity);
                        }
                    }
                }
            }

            timerService.cancelTimeout(roomCode);

            roomCache.remove(roomCode);


            roomRepository.delete(room);
        }


        RoomLock.removeLock(roomCode);

        return room;
    }

    private String generateRoomCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}