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

/**
 * 房间生命周期服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomLifecycleService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final RoomCache roomCache;
    private final ObjectMapper objectMapper;
    private final QuestionTimerService timerService; 用于取消定时器
    private final ChatRoomManager chatRoomManager;  //  用于清理聊天室

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
                // 检查玩家是否已在房间内（允许重连）
                boolean playerInRoom = gameRoom.getPlayers().stream()
                        .anyMatch(p -> p.getPlayerId().equals(playerId));

                if (!playerInRoom) {
                    // 新玩家不允许加入进行中的游戏
                    throw new BusinessException("房间已开始游戏或已结束");
                }

                // 已在房间的玩家允许刷新/重连，检查是否在断线列表中
                    roomCache.put(roomCode, gameRoom);

                return; // 跳过后续加入逻辑
            }

            if (gameRoom.getPlayers().size() >= room.getMaxPlayers()) {
                throw new BusinessException("房间已满");
            }

            // 检查玩家是否已在房间内
            boolean playerExists = gameRoom.getPlayers().stream()
                    .anyMatch(p -> p.getPlayerId().equals(playerId));

            if (!playerExists) {
                // 修改：必须从数据库查找已登录的玩家
                PlayerEntity player = playerRepository.findByPlayerId(playerId)
                        .orElseThrow(() -> new BusinessException("玩家不存在，请先登录"));

                // 改：直接设置房间和准备状态
                player.setRoom(room);
                player.setReady(false);
                
                playerRepository.save(player);

                PlayerDTO playerDTO = PlayerDTO.builder()
                        .playerId(playerId)
                        .name(playerName)
                        .score(0)
                        .ready(false)
                        
                        .build();

                // 测试房间：真实玩家插入到第一位（成为房主）
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
                // 游戏未开始：检查是否房主离开
                boolean isRoomOwner = !gameRoom.getPlayers().isEmpty() &&
                        gameRoom.getPlayers().get(0).getPlayerId().equals(playerId);

                if (isRoomOwner) {
                    // 房主离开，使用原子删除方法
                    deleteRoomAtomically(roomCode, gameRoom);
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


                    // 同步到 Redis
                    roomCache.put(roomCode, gameRoom);
                }

            } else {
                // 游戏进行中：标记断线

                long connectedCount = gameRoom.getPlayers().stream()
                        .count();

                if (connectedCount == 0) {
                    // 改：游戏进行中时不立即删除，给重连时间
                    if (gameRoom.isStarted() && !gameRoom.isFinished()) {
                        log.warn(" 房间 {} 所有玩家断线，但游戏进行中，保留房间等待重连", roomCode);
                        roomCache.put(roomCode, gameRoom);
                        return true;
                    } else {
                        // 游戏未开始或已结束，使用原子删除方法
                        deleteRoomAtomically(roomCode, gameRoom);
                        return false; // 房间已解散
                    }
                }

                // 游戏进行中标记断线，同步到 Redis
                roomCache.put(roomCode, gameRoom);
            }

            return true; // 房间仍存在
        }
    }

    

                // 添加：如果游戏已结束，重连时重置房间过期时间
                if (gameRoom.isFinished()) {
                    // 给房间续期（重新计时5分钟）
                    // 这里可以通过 RoomCache 添加续期机制
                }
            } else {
                log.warn(" 玩家 {} 重连房间 {}，但未找到断线记录", playerId, roomCode);
            }

            // 同步到 Redis
            roomCache.put(roomCode, gameRoom);
        }
    }

    

            // 更新题目数量（可选）
            if (request.getQuestionCount() != null && request.getQuestionCount() > 0) {
                room.setQuestionCount(request.getQuestionCount());
            }

            // 更新每题时长（可选）
            if (request.getTimeLimit() != null && request.getTimeLimit() >= 20 && request.getTimeLimit() <= 120) {
                room.setTimeLimit(request.getTimeLimit());
            }

            // 更新聊天室开关
            if (request.getChatEnabled() != null) {
                room.setChatEnabled(request.getChatEnabled());
            }

            // 更新排名模式
            if (request.getRankingMode() != null) {
                room.setRankingMode(request.getRankingMode());
            }

            // 更新目标分数
            room.setTargetScore(request.getTargetScore());

            // 更新通关条件
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

            // 更新题目标签筛选
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

        // 测试房间中的Bot玩家：只更新内存，不操作数据库
        if (playerId.startsWith("BOT_")) {
            gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .ifPresent(p -> p.setReady(ready));

            // 同步到 Redis
            roomCache.put(roomCode, gameRoom);
            return;
        }

        // 真实玩家：更新数据库 + 内存
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

        // 同步到 Redis
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

     不存在，跳过断线处理", roomCode);
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

     不存在，跳过移除操作", roomCode);
            return;
        }

        synchronized (RoomLock.getLock(roomCode)) {
            if (gameRoom.isStarted() && !gameRoom.isFinished()) {
                return;
            }

            // 只有在游戏未开始或已结束时才真正移除

            // 从断线列表移除

            // 从玩家列表移除
            PlayerDTO removedPlayer = gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .orElse(null);

            if (removedPlayer != null) {
                gameRoom.getPlayers().remove(removedPlayer);
            }

            // 清理分数
            gameRoom.getScores().remove(playerId);

            // 清理玩家状态
            gameRoom.getPlayerGameStates().remove(playerId);

            // 更新数据库
            PlayerEntity player = playerRepository.findByPlayerId(playerId).orElse(null);
            if (player != null) {
                player.setRoom(null);
                player.setReady(false);
                playerRepository.save(player);
            }

            // 检查是否房间为空，使用原子删除方法（问题8）
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

        int currentPlayers = gameRoom.getPlayers().size();

 获取当前题目的已提交玩家ID列表（用于前端验证）
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

    // ==================== 私有方法 ====================

    /**
     * 原子删除房间
     *
     * @param roomCode 房间代码
     * @param gameRoom 内存中的房间对象（可选，如果已经获取）
     * @return 被删除的房间实体（用于发送删除通知）
     */
    @Transactional
    protected RoomEntity deleteRoomAtomically(String roomCode, GameRoom gameRoom) {
        RoomEntity room;

        // 使用RoomLock确保原子性（问题7）
        synchronized (RoomLock.getLock(roomCode)) {
            // 1. 查询房间实体
            room = roomRepository.findByRoomCode(roomCode).orElse(null);
            if (room == null) {
                log.warn(" 房间 {} 已不存在，跳过删除", roomCode);
                RoomLock.removeLock(roomCode); // 清理锁
                return null;
            }

            // 2. 检查房间状态，防止重复删除（问题7）
            if (room.getStatus() == RoomStatus.FINISHED && room.getId() == null) {
                log.warn(" 房间 {} 已处于删除状态，跳过重复删除", roomCode);
                RoomLock.removeLock(roomCode); // 清理锁
                return null;
            }


            // 3. 清理所有关联的玩家记录（问题2）
            if (gameRoom != null) {
                for (PlayerDTO player : gameRoom.getPlayers()) {
                    String playerId = player.getPlayerId();
                    // Bot玩家不在数据库中，跳过
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

            // 4. 取消定时器
            timerService.cancelTimeout(roomCode);

            // 5. 删除缓存（带重试）（问题5）
            roomCache.remove(roomCode);

            // 6. 主动清理聊天室（问题3）

            // 7. 真正删除数据库记录（问题1）
            roomRepository.delete(room);
        }

 在synchronized块外清理锁，防止内存泄漏
        RoomLock.removeLock(roomCode);

        return room;
    }

    private String generateRoomCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}