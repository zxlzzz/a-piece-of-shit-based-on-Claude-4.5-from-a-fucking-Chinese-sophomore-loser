package org.example.pojo;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerDTO;
import org.example.dto.QuestionDTO;
import org.example.utils.DTOConverter;

import java.util.List;
import java.util.Map;


@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomUpdate {

    private String roomCode;
    private Integer currentIndex;
    private List<PlayerDTO> players;
    private RoomStatus status;
    private QuestionDTO currentQuestion;
    private Integer maxPlayers;
    private Map<String, Integer> scores;

    /**
     * 是否刚完成本题的提交和计分
     * 用于触发前端展示得分动画
     */
    private Boolean justScored;

    /**
     * 从 GameRoom 构造 RoomUpdate
     */
    public RoomUpdate(GameRoom gameRoom) {
        this.roomCode = gameRoom.getRoomCode();
        this.currentIndex = gameRoom.getCurrentIndex();
        this.players = gameRoom.getPlayers();
        this.status = determineStatus(gameRoom);
        this.currentQuestion = DTOConverter.toQuestionDTO(gameRoom.getCurrentQuestion());  // ✅
        this.maxPlayers = gameRoom.getMaxPlayers();
        this.scores = gameRoom.getScores();
        this.justScored = false;
    }

    private RoomStatus determineStatus(GameRoom gameRoom) {
        if (gameRoom.isFinished()) return RoomStatus.FINISHED;
        if (gameRoom.isStarted()) return RoomStatus.PLAYING;
        return RoomStatus.WAITING;
    }
}

