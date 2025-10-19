package org.example.service.room.impl;

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
import org.example.repository.BidQuestionConfigRepository;
import org.example.repository.ChoiceQuestionConfigRepository;
import org.example.repository.PlayerRepository;
import org.example.repository.RoomRepository;
import org.example.service.cache.RoomCache;
import org.example.service.room.RoomLifecycleService;
import org.example.utils.DTOConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间生命周期服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomLifecycleServiceImpl implements RoomLifecycleService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final RoomCache roomCache;
    private final ObjectMapper objectMapper;
    private final ChoiceQuestionConfigRepository choiceConfigRepository;
    private final BidQuestionConfigRepository bidConfigRepository;
    private final DTOConverter dtoConverter;

    @Override
    @Transactional
    public RoomEntity initializeRoom(Integer maxPlayers, Integer questionCount, GameRoom gameRoom) {
        String roomCode = generateRoomCode();

        // 🔥 创建房间实体（只有基础字段）
        RoomEntity roomEntity = RoomEntity.builder()
                .roomCode(roomCode)
                .status(RoomStatus.WAITING)
                .maxPlayers(maxPlayers)
                .questionCount(questionCount)
                // 🔥 高级规则使用默认值
                .rankingMode("standard")
                .targetScore(null)
                .winConditionsJson(null)
                .build();

        RoomEntity savedRoom = roomRepository.save(roomEntity);

        // 初始化内存房间
        gameRoom.setRoomCode(roomCode);
        gameRoom.setMaxPlayers(maxPlayers);
        gameRoom.setPlayers(new ArrayList<>());
        gameRoom.setQuestions(new ArrayList<>());
        gameRoom.setStarted(false);
        gameRoom.setFinished(false);
        gameRoom.setCurrentIndex(-1);
        gameRoom.setSubmissions(new ConcurrentHashMap<>());
        gameRoom.setScores(new ConcurrentHashMap<>());
        gameRoom.setDisconnectedPlayers(new ConcurrentHashMap<>());
        gameRoom.setPlayerGameStates(new ConcurrentHashMap<>());

        log.info("✅ 创建房间: {}, 最大人数: {}, 题目数: {}", roomCode, maxPlayers, questionCount);
        return savedRoom;
    }

    @Override
    @Transactional
    public void handleJoin(String roomCode, String playerId, String playerName) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (getInternedRoomCode(roomCode)) {
            // 检查房间状态
            if (room.getStatus() != RoomStatus.WAITING) {
                throw new BusinessException("房间已开始游戏或已结束");
            }

            // 检查房间是否已满
            if (gameRoom.getPlayers().size() >= room.getMaxPlayers()) {
                throw new BusinessException("房间已满");
            }

            // 检查玩家是否已在房间内
            boolean playerExists = gameRoom.getPlayers().stream()
                    .anyMatch(p -> p.getPlayerId().equals(playerId));

            if (!playerExists) {
                PlayerEntity player = playerRepository.findByPlayerId(playerId)
                        .orElse(null);

                if (player == null) {
                    player = PlayerEntity.builder()
                            .playerId(playerId)
                            .name(playerName)
                            .ready(false)
                            .room(room)
                            .build();
                } else {
                    player.setRoom(room);
                    player.setReady(false);
                }
                playerRepository.save(player);

                PlayerDTO playerDTO = PlayerDTO.builder()
                        .playerId(playerId)
                        .name(playerName)
                        .score(0)
                        .ready(false)
                        .build();
                gameRoom.getPlayers().add(playerDTO);
                gameRoom.getScores().put(playerId, 0);

                log.info("✅ 玩家 {} ({}) 加入房间 {}", playerName, playerId, roomCode);
            }
        }
    }

    @Override
    @Transactional
    public boolean handleLeave(String roomCode, String playerId) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (getInternedRoomCode(roomCode)) {
            gameRoom.getDisconnectedPlayers().put(playerId, LocalDateTime.now());

            PlayerDTO leavingPlayer = gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .orElse(null);

            String playerName = leavingPlayer != null ? leavingPlayer.getName() : "未知玩家";

            if (!gameRoom.isStarted()) {
                // 游戏未开始：检查是否房主离开
                boolean isRoomOwner = !gameRoom.getPlayers().isEmpty() &&
                        gameRoom.getPlayers().get(0).getPlayerId().equals(playerId);

                if (isRoomOwner) {
                    // 房主离开，解散房间
                    roomCache.remove(roomCode);
                    room.setStatus(RoomStatus.FINISHED);
                    roomRepository.save(room);

                    log.info("🏠 房主 {} 离开，房间 {} 已解散", playerName, roomCode);
                    return false; // 房间已解散
                } else {
                    // 普通玩家离开
                    gameRoom.getPlayers().removeIf(p -> p.getPlayerId().equals(playerId));
                    gameRoom.getScores().remove(playerId);

                    PlayerEntity player = playerRepository.findByPlayerId(playerId).orElse(null);
                    if (player != null) {
                        player.setRoom(null);
                        playerRepository.save(player);
                    }

                    log.info("👋 玩家 {} 离开房间 {}（游戏未开始）", playerName, roomCode);
                }

            } else {
                // 游戏进行中：标记断线
                log.info("⏸️ 玩家 {} 离开房间 {}（游戏进行中，后续自动提交）", playerName, roomCode);

                long connectedCount = gameRoom.getPlayers().stream()
                        .filter(p -> !gameRoom.getDisconnectedPlayers().containsKey(p.getPlayerId()))
                        .count();

                if (connectedCount == 0) {
                    // 所有人都离开，解散房间
                    roomCache.remove(roomCode);
                    room.setStatus(RoomStatus.FINISHED);
                    roomRepository.save(room);
                    log.info("🏠 所有玩家离开，房间 {} 已解散", roomCode);
                    return false; // 房间已解散
                }
            }

            return true; // 房间仍存在
        }
    }

    @Override
    public void handleReconnect(String roomCode, String playerId) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (getInternedRoomCode(roomCode)) {
            if (gameRoom.getDisconnectedPlayers().containsKey(playerId)) {
                gameRoom.getDisconnectedPlayers().remove(playerId);

                gameRoom.getPlayers().stream()
                        .filter(p -> p.getPlayerId().equals(playerId))
                        .findFirst()
                        .ifPresent(player -> log.info("🔄 玩家 {} 重新连接到房间 {}", player.getName(), roomCode));
            }
        }
    }

    @Override
    @Transactional
    public void updateSettings(String roomCode, GameController.UpdateRoomSettingsRequest request) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (getInternedRoomCode(roomCode)) {
            // 校验：游戏未开始
            if (gameRoom.isStarted()) {
                throw new BusinessException("游戏已开始，无法修改设置");
            }

            // 更新题目数量（可选）
            if (request.getQuestionCount() != null && request.getQuestionCount() > 0) {
                room.setQuestionCount(request.getQuestionCount());
                log.info("📝 房间 {} 题目数量更新为: {}", roomCode, request.getQuestionCount());
            }

            // 更新排名模式
            if (request.getRankingMode() != null) {
                room.setRankingMode(request.getRankingMode());
                log.info("📊 房间 {} 排名模式更新为: {}", roomCode, request.getRankingMode());
            }

            // 更新目标分数
            room.setTargetScore(request.getTargetScore());

            // 更新通关条件
            String winConditionsJson = null;
            if (request.getWinConditions() != null) {
                try {
                    winConditionsJson = objectMapper.writeValueAsString(request.getWinConditions());
                    log.info("🎯 房间 {} 通关条件更新为: {}", roomCode, winConditionsJson);
                } catch (Exception e) {
                    log.error("序列化通关条件失败", e);
                    throw new BusinessException("通关条件格式错误");
                }
            }
            room.setWinConditionsJson(winConditionsJson);

            // 保存到数据库
            roomRepository.save(room);

            log.info("✅ 房间 {} 设置更新成功", roomCode);
        }
    }

    @Override
    @Transactional
    public void setPlayerReady(String roomCode, String playerId, boolean ready) {
        PlayerEntity player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

        if (!player.getRoom().getRoomCode().equals(roomCode)) {
            throw new BusinessException("玩家不在该房间中");
        }

        player.setReady(ready);
        playerRepository.save(player);

        GameRoom gameRoom = roomCache.get(roomCode);
        if (gameRoom != null) {
            gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .ifPresent(p -> p.setReady(ready));
        }

        log.info("✅ 玩家 {} 设置准备状态: {}", playerId, ready);
    }

    @Override
    public RoomDTO toRoomDTO(String roomCode) {
        RoomEntity roomEntity = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);
        return toRoomDTO(roomEntity, gameRoom);
    }

    private RoomDTO toRoomDTO(RoomEntity roomEntity, GameRoom gameRoom) {
        RoomStatus status = RoomStatus.WAITING;
        if (gameRoom.isFinished()) {
            status = RoomStatus.FINISHED;
        } else if (gameRoom.isStarted()) {
            status = RoomStatus.PLAYING;
        }

        // 🔥 直接使用 DTO（无需转换）
        QuestionDTO currentQuestionDTO = gameRoom.getCurrentQuestion();

        Integer questionCount = null;
        if (gameRoom.getQuestions() != null && !gameRoom.getQuestions().isEmpty()) {
            questionCount = gameRoom.getQuestions().size();
        } else if (roomEntity != null && roomEntity.getQuestionCount() != null) {
            questionCount = roomEntity.getQuestionCount();
        } else {
            questionCount = 10;
        }

        // 解析 winConditionsJson
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

        return RoomDTO.builder()
                .roomCode(gameRoom.getRoomCode())
                .maxPlayers(gameRoom.getMaxPlayers() != null ? gameRoom.getMaxPlayers() :
                        (roomEntity != null ? roomEntity.getMaxPlayers() : gameRoom.getPlayers().size()))
                .currentPlayers(gameRoom.getPlayers().size())
                .status(status)
                .players(new ArrayList<>(gameRoom.getPlayers()))
                .questionStartTime(gameRoom.getQuestionStartTime())
                .timeLimit(gameRoom.getTimeLimit())
                .currentIndex(gameRoom.getCurrentIndex())
                .currentQuestion(currentQuestionDTO)  // ✅ 直接使用
                .questionCount(questionCount)
                .rankingMode(roomEntity != null ? roomEntity.getRankingMode() : "standard")
                .targetScore(roomEntity != null ? roomEntity.getTargetScore() : null)
                .winConditions(winConditions)
                .build();
    }

    // ==================== 私有方法 ====================

    private String generateRoomCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private String getInternedRoomCode(String roomCode) {
        return roomCode.intern();
    }
}